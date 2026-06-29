package com.openclaw.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.app.AuditEntry
import com.openclaw.app.LinkService
import com.openclaw.app.ModelInfo
import com.openclaw.app.Notifier
import com.openclaw.app.OpenClawClient
import com.openclaw.app.OpenClawListener
import com.openclaw.app.Persona
import com.openclaw.app.SshLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ConnState { Disconnected, Connecting, Ready }

/** A project: stable `id` (keys conversation/context) + renamable display `name`
 *  + optional explicit working dir `cwd` (null = auto per-project dir) + `shared`:
 *  when true, all agents in the project share one context (see each other), else
 *  each agent has its own isolated context. */
data class Proj(val id: String, val name: String, val cwd: String? = null, val shared: Boolean = false)

/** A file the user picked and copied to cache, staged to upload with the next message. */
data class Staged(val localPath: String, val name: String, val isImage: Boolean)

private val IMAGE_EXT = Regex("(?i).*\\.(png|jpe?g|gif|webp|bmp|heic|heif)$")

val DEFAULT_MODELS = listOf(
    ModelInfo("claude-opus-4-8", "Opus 4.8"),
    ModelInfo("claude-sonnet-4-6", "Sonnet 4.6"),
    ModelInfo("claude-haiku-4-5", "Haiku 4.5"),
)

class AppViewModel(app: Application) : AndroidViewModel(app), OpenClawListener {

    private val prefs = app.getSharedPreferences("openclaw", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val link = SshLink(app)
    private var client: OpenClawClient? = null

    // connection form
    val host = mutableStateOf("")
    val sshUser = mutableStateOf("root")
    val sshPass = mutableStateOf("")
    val claudeKey = mutableStateOf("")

    val conn = mutableStateOf(ConnState.Disconnected)
    // Force the connection form even when we have saved creds (user chose "изменить данные").
    val showForm = mutableStateOf(false)
    val statusMsg = mutableStateOf("")
    val lang = mutableStateOf("ru") // "ru" | "en", persisted; drives LocalStrings
    private fun t() = Strings(lang.value == "en")
    val busy = mutableStateOf(false)
    val scrollTick = mutableStateOf(0) // bumped on every streamed chunk to drive auto-scroll

    val personas = mutableStateListOf<Persona>()
    var models: List<ModelInfo> = DEFAULT_MODELS
    val personaModel = mutableStateMapOf<String, String>() // persona id -> model override
    val audit = mutableStateListOf<AuditEntry>()

    val projects = mutableStateListOf<Proj>()
    val currentProjectId = mutableStateOf("default")
    val currentPersonaId = mutableStateOf<String?>(null)

    private val threads = HashMap<String, SnapshotStateList<ChatMsg>>()
    private var streamingMsg: ChatMsg? = null
    private var streamingKey: String? = null // thread key the in-flight reply belongs to

    // Attachments picked but not yet sent (shown as previews above the input).
    val staged = mutableStateListOf<Staged>()

    // True only after an explicit user-initiated disconnect: suppresses auto-reconnect.
    private var userDisconnected = false
    // Guards against stacking up multiple reconnect attempts.
    private var reconnecting = false
    // A connect attempt's coroutine is in flight. This (NOT conn==Connecting) gates connect(),
    // otherwise a drop that sets conn=Connecting would block its own scheduled reconnect.
    private var connecting = false
    // Force-fails a connect that hangs (half-open SSH/WS after a network/VPN switch).
    private var watchdog: Runnable? = null

    init {
        loadPrefs()
    }

    // ---------- prefs ----------

    private fun loadPrefs() {
        lang.value = prefs.getString("lang", "ru") ?: "ru"
        host.value = prefs.getString("host", "") ?: ""
        sshUser.value = prefs.getString("ssh_user", "root") ?: "root"
        sshPass.value = prefs.getString("ssh_pass", "") ?: ""
        claudeKey.value = prefs.getString("claude_key", "") ?: ""
        projects.clear()
        prefs.getString("projects", null)?.let { raw ->
            runCatching { JSONArray(raw) }.getOrNull()?.let { a ->
                for (i in 0 until a.length()) {
                    when (val el = a.get(i)) {
                        is JSONObject -> projects.add(Proj(el.getString("id"), el.optString("name", el.getString("id")), el.optString("cwd").ifBlank { null }, el.optBoolean("shared", false)))
                        else -> projects.add(Proj(el.toString(), el.toString())) // migrate old name-only format
                    }
                }
            }
        }
        if (projects.isEmpty()) projects.add(Proj("default", "default"))
        currentProjectId.value = prefs.getString("current_project", "default") ?: "default"
        if (projects.none { it.id == currentProjectId.value }) currentProjectId.value = projects.first().id
        prefs.getString("persona_models", null)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()?.let { o ->
                for (k in o.keys()) personaModel[k] = o.getString(k)
            }
        }
    }

    private fun projectsToJson(): JSONArray {
        val a = JSONArray()
        projects.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("cwd", it.cwd ?: "").put("shared", it.shared)) }
        return a
    }

    private fun saveProjects() {
        val a = projectsToJson()
        prefs.edit().putString("projects", a.toString())
            .putString("current_project", currentProjectId.value).apply()
        runCatching { client?.saveProjects(a) } // mirror to the server so a reinstall restores it
    }

    private fun savePersonaModels() {
        val o = JSONObject(); personaModel.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("persona_models", o.toString()).apply()
    }

    /** Persist connection creds so a cold start / reconnect needs no re-entry. */
    private fun saveCreds() {
        prefs.edit()
            .putString("host", host.value.trim())
            .putString("ssh_user", sshUser.value.trim().ifEmpty { "root" })
            .putString("ssh_pass", sshPass.value)
            .putString("claude_key", claudeKey.value.trim())
            .apply()
    }

    fun hasSavedCreds(): Boolean = host.value.isNotBlank() && sshPass.value.isNotBlank()

    fun setLang(code: String) {
        lang.value = code
        prefs.edit().putString("lang", code).apply()
    }

    // ---------- derived ----------

    fun persona(id: String?): Persona? = personas.firstOrNull { it.id == id }
    fun currentPersona(): Persona? = persona(currentPersonaId.value)

    fun modelFor(id: String?): String =
        personaModel[id] ?: persona(id)?.defaultModel ?: "claude-opus-4-8"

    private fun threadKey(): String = "${currentProjectId.value}:${currentPersonaId.value}"

    fun currentThread(): SnapshotStateList<ChatMsg> =
        threads.getOrPut(threadKey()) { mutableStateListOf() }

    // ---------- actions ----------

    fun selectPersona(id: String) { currentPersonaId.value = id }

    fun currentProjectName(): String =
        projects.firstOrNull { it.id == currentProjectId.value }?.name ?: currentProjectId.value

    fun currentProj(): Proj? = projects.firstOrNull { it.id == currentProjectId.value }

    fun selectProject(id: String) { currentProjectId.value = id; saveProjects() }

    fun createProject(name: String, cwd: String? = null) {
        val n = name.trim()
        if (n.isEmpty()) return
        var id = n
        var i = 2
        while (projects.any { it.id == id }) { id = "${n}_$i"; i++ }
        projects.add(Proj(id, n, cwd?.trim()?.ifBlank { null }))
        currentProjectId.value = id
        saveProjects()
    }

    /** Rename = label change only. The stable id keeps conversation_id, threads
     *  and the backend Claude Code session intact, so the context is preserved. */
    fun renameProject(id: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) return
        val idx = projects.indexOfFirst { it.id == id }
        if (idx >= 0) projects[idx] = projects[idx].copy(name = n)
        saveProjects()
    }

    /** Set/clear an explicit working dir for a project. Changing it for a project with
     *  existing context starts a fresh Claude Code session in the new dir (backend retries). */
    fun setProjectCwd(id: String, cwd: String?) {
        val idx = projects.indexOfFirst { it.id == id }
        if (idx >= 0) projects[idx] = projects[idx].copy(cwd = cwd?.trim()?.ifBlank { null })
        saveProjects()
    }

    /** Shared = all agents in the project share one Claude Code context (see each other). */
    fun setProjectShared(id: String, shared: Boolean) {
        val idx = projects.indexOfFirst { it.id == id }
        if (idx >= 0) projects[idx] = projects[idx].copy(shared = shared)
        saveProjects()
    }

    fun setModel(personaId: String, modelId: String) {
        personaModel[personaId] = modelId
        savePersonaModels()
    }

    fun savePersona(p: Persona) {
        client?.savePersona(p)
    }

    fun refreshAudit() { client?.getAudit(100) }

    fun connect() {
        if (host.value.isBlank() || sshPass.value.isBlank()) {
            statusMsg.value = t().needCreds
            showForm.value = true
            return
        }
        if (connecting) return // an attempt is already in flight
        connecting = true
        userDisconnected = false
        showForm.value = false
        conn.value = ConnState.Connecting
        statusMsg.value = t().stConnecting
        armWatchdog() // never let a single attempt hang forever
        viewModelScope.launch {
            // Tear down any stale link/socket before re-establishing (reconnect path).
            client?.close(); client = null
            withContext(Dispatchers.IO) { runCatching { link.close() } }
            val ep = try {
                withContext(Dispatchers.IO) {
                    link.connect(host.value.trim(), sshUser.value.trim().ifEmpty { "root" }, sshPass.value, claudeKey.value.trim())
                }
            } catch (e: Exception) {
                ui {
                    connecting = false
                    cancelWatchdog()
                    conn.value = ConnState.Disconnected
                    statusMsg.value = t().stConnectFail(e.message)
                    scheduleReconnect()
                }
                return@launch
            }
            ui {
                statusMsg.value = t().stAuth
                // connecting stays true until Ready (onPersonas) / a drop / the watchdog.
                client = OpenClawClient(ep.wsUrl, this@AppViewModel).also {
                    it.connect { it.auth(ep.token) }
                }
            }
        }
    }

    /** Wall-clock guard on a connect: if we haven't reached Ready in time, abandon the
     *  attempt (close link+socket — which unblocks any stuck blocking call) and retry. */
    private fun armWatchdog() {
        cancelWatchdog()
        val r = Runnable {
            if (conn.value != ConnState.Ready) {
                connecting = false
                client?.close(); client = null
                viewModelScope.launch { withContext(Dispatchers.IO) { runCatching { link.close() } } }
                conn.value = ConnState.Disconnected
                statusMsg.value = t().stTimeout
                scheduleReconnect()
            }
        }
        watchdog = r
        main.postDelayed(r, 30_000)
    }

    private fun cancelWatchdog() {
        watchdog?.let { main.removeCallbacks(it) }
        watchdog = null
    }

    /** From the splash: let the user edit server creds (stops auto-reconnect, shows the form). */
    fun editConnection() {
        userDisconnected = true
        reconnecting = false
        connecting = false
        cancelWatchdog()
        conn.value = ConnState.Disconnected
        showForm.value = true
    }

    /** On cold start / return to foreground: silently restore the link if we have creds
     *  and the user didn't deliberately disconnect. */
    fun autoConnect() {
        if (conn.value == ConnState.Disconnected && hasSavedCreds() && !userDisconnected) connect()
    }

    /** Returning to the app after backgrounding — re-establish if the link dropped. */
    fun onResume() {
        if (conn.value != ConnState.Ready && !connecting && hasSavedCreds() && !userDisconnected) {
            connect()
        }
    }

    private fun scheduleReconnect() {
        if (userDisconnected || reconnecting || !hasSavedCreds()) return
        reconnecting = true
        statusMsg.value = t().stReconnecting
        main.postDelayed({
            reconnecting = false
            if (!userDisconnected && conn.value != ConnState.Ready) connect()
        }, 2000)
    }

    /** A live turn died with the socket — close out its bubble so the UI isn't stuck. */
    private fun abortStreaming(note: String) {
        streamingMsg?.let { it.appendText("\n— $note —"); it.streaming = false }
        streamingMsg = null
        busy.value = false
    }

    fun disconnect() {
        userDisconnected = true
        reconnecting = false
        connecting = false
        cancelWatchdog()
        client?.close(); client = null
        viewModelScope.launch { withContext(Dispatchers.IO) { link.close() } }
        personas.clear()
        conn.value = ConnState.Disconnected
        statusMsg.value = t().stDisconnected
        runCatching { LinkService.stop(getApplication<Application>()) }
    }

    fun send(text: String) {
        val pid = currentPersonaId.value ?: return
        val atts = staged.toList()
        val body = text.trim()
        if ((body.isEmpty() && atts.isEmpty()) || busy.value || conn.value != ConnState.Ready) return
        val tkey = threadKey()
        val th = currentThread()
        val um = userMsg(body)
        // Pre-compute each upload's server path so the FileSeg carries it: that lets the
        // message be rebuilt from the server after a reinstall (the local cache is gone then).
        val uploads = atts.mapIndexed { i, a ->
            val safe = a.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val remote = "/var/lib/openclaw/uploads/${System.currentTimeMillis()}_${i}_$safe"
            um.segs.add(FileSeg(a.name, remote, a.isImage).apply { localPath = a.localPath })
            a.localPath to remote
        }
        th.add(um)
        staged.clear()
        if (uploads.isEmpty()) {
            persistMessage(tkey, um)
            startTurn(pid, th, body, tkey)
            return
        }
        // Upload the attachments over SFTP, then prompt with their server paths appended
        // so the agent can Read them (the backend's _FILE_PROTOCOL explains the format).
        busy.value = true
        bump()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    uploads.forEach { (localPath, remote) -> link.uploadFile(localPath, remote) }
                }
            } catch (e: Exception) {
                ui { um.error = t().stUploadFail(e.message); busy.value = false }
                return@launch
            }
            val notes = uploads.joinToString("") { "\n[Вложение: ${it.second}]" }
            ui {
                busy.value = false
                persistMessage(tkey, um)
                startTurn(pid, th, (body + notes).trim(), tkey)
            }
        }
    }

    private fun startTurn(pid: String, th: SnapshotStateList<ChatMsg>, text: String, tkey: String) {
        val a = assistantMsg(); th.add(a); streamingMsg = a; streamingKey = tkey
        busy.value = true
        bump()
        // Shared project -> one context id for all agents; else isolated per agent.
        val conv = if (currentProj()?.shared == true) currentProjectId.value else threadKey()
        client?.prompt(pid, modelFor(pid), conv, currentProjectId.value, text, currentProj()?.cwd)
    }

    /** Save one finished message to the server under its thread key (best-effort). */
    private fun persistMessage(key: String, msg: ChatMsg) {
        runCatching { client?.saveMessage(key, msg.toJson()) }
    }

    // ---------- attachments ----------

    /** Copy a picked file/photo into cache and stage it for the next message. */
    fun stageAttachment(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val cr = app.contentResolver
                val name = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
                val isImage = cr.getType(uri)?.startsWith("image/") == true || IMAGE_EXT.matches(name)
                val dir = File(app.cacheDir, "outgoing").apply { mkdirs() }
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val f = File(dir, "${System.currentTimeMillis()}_$safe")
                (cr.openInputStream(uri) ?: throw RuntimeException(if (lang.value == "en") "can't open file" else "не открыть файл"))
                    .use { input -> f.outputStream().use { input.copyTo(it) } }
                ui { staged.add(Staged(f.absolutePath, name, isImage)) }
            } catch (e: Exception) {
                ui { statusMsg.value = t().stAttachFail(e.message) }
            }
        }
    }

    fun removeStaged(index: Int) { if (index in staged.indices) staged.removeAt(index) }

    /** Download a file the agent returned and open it with a viewer/share target. */
    fun openFileSeg(seg: FileSeg) {
        seg.localPath?.let { openWithViewer(File(it)); return }
        if (seg.downloading || seg.remotePath.isBlank()) return
        seg.downloading = true; seg.error = null
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { downloadToCache(seg) }
                ui { seg.downloading = false; seg.localPath = f.absolutePath; openWithViewer(f) }
            } catch (e: Exception) {
                ui { seg.downloading = false; seg.error = e.message }
            }
        }
    }

    /** Ensure a restored/received image is downloaded so it can render inline. */
    fun ensureImage(seg: FileSeg) = fetchForDisplay(seg)

    private fun fetchForDisplay(seg: FileSeg) {
        if (seg.localPath != null || seg.downloading || seg.remotePath.isBlank()) return
        seg.downloading = true
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { downloadToCache(seg) }
                ui { seg.downloading = false; seg.localPath = f.absolutePath }
            } catch (e: Exception) {
                ui { seg.downloading = false; seg.error = e.message }
            }
        }
    }

    private fun downloadToCache(seg: FileSeg): File {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "incoming").apply { mkdirs() }
        val safe = seg.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
        val f = File(dir, "${System.currentTimeMillis()}_$safe")
        link.downloadFile(seg.remotePath, f)
        return f
    }

    private fun openWithViewer(f: File) {
        try {
            val ctx = getApplication<Application>()
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val mime = ctx.contentResolver.getType(uri) ?: "*/*"
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(view, t().openFile).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            statusMsg.value = t().stOpenFail(e.message)
        }
    }

    /** Pull `[[FILE:/path]]` markers out of the finished reply into downloadable attachments. */
    private fun materializeFiles(msg: ChatMsg) {
        val paths = mutableListOf<String>()
        msg.segs.filterIsInstance<TextSeg>().forEach { ts ->
            val found = FILE_MARKER.findAll(ts.text).map { it.groupValues[1].trim() }.toList()
            if (found.isNotEmpty()) {
                paths.addAll(found)
                ts.text = ts.text.replace(FILE_MARKER, "").trim()
            }
        }
        paths.filter { it.isNotBlank() }.forEach { path ->
            val name = path.substringAfterLast('/').ifBlank { "file" }
            val seg = FileSeg(name, path, IMAGE_EXT.matches(name))
            msg.segs.add(seg)
            if (seg.isImage) fetchForDisplay(seg)
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) { null }

    /** Agent asked a multiple-choice question — attach it to the live reply as interactive options. */
    override fun onQuestion(id: String, questions: org.json.JSONArray) = ui {
        val qs = parseQuestions(questions)
        if (qs.isEmpty()) return@ui
        val target = streamingMsg ?: currentThread().lastOrNull { it.role == "assistant" }
        target?.segs?.add(QuestionSeg(id, qs))
        bump()
    }

    /** User tapped through a question card → send the chosen answer as the next message. */
    fun answerQuestion(seg: QuestionSeg, answerText: String) {
        if (busy.value || seg.answered || answerText.isBlank()) return
        seg.answered = true
        send(answerText)
    }

    fun interrupt() {
        client?.interrupt()
        // Free the UI right away so the next message can be sent immediately; the server's
        // "interrupted" (and its supersede logic) then just confirms what we already did.
        streamingMsg?.let {
            it.appendText("\n— ${t().interrupted} —"); it.streaming = false
            persistMessage(streamingKey ?: threadKey(), it)
        }
        streamingMsg = null; streamingKey = null
        busy.value = false
    }

    // ---------- listener (OkHttp threads -> main) ----------

    private fun ui(block: () -> Unit) {
        main.post(block)
    }

    override fun onAuthOk(models: List<ModelInfo>) = ui {
        if (models.isNotEmpty()) this.models = models
        client?.listPersonas()
        client?.getAudit(100)
        client?.getState() // pull saved projects + chat history
    }

    override fun onPersonas(personas: List<Persona>) = ui {
        this.personas.clear(); this.personas.addAll(personas)
        if (currentPersonaId.value == null || personas.none { it.id == currentPersonaId.value }) {
            currentPersonaId.value = personas.firstOrNull()?.id
        }
        conn.value = ConnState.Ready
        statusMsg.value = t().stConnected
        reconnecting = false
        connecting = false
        cancelWatchdog()
        showForm.value = false
        saveCreds() // persist only after a confirmed-good connection (so the form never returns for good creds)
        runCatching { LinkService.start(getApplication<Application>()) }
    }

    override fun onAudit(entries: List<AuditEntry>) = ui {
        audit.clear(); audit.addAll(entries.reversed())
    }

    /** Server returned saved projects + transcripts (on connect). Merge projects (union,
     *  so neither side loses entries) and fill any chat threads we don't have in memory. */
    override fun onState(state: JSONObject) = ui {
        state.optJSONArray("projects")?.let { pj ->
            val byId = LinkedHashMap<String, Proj>()
            for (i in 0 until pj.length()) {
                val o = pj.optJSONObject(i) ?: continue
                val id = o.optString("id"); if (id.isBlank()) continue
                byId[id] = Proj(id, o.optString("name", id), o.optString("cwd").ifBlank { null }, o.optBoolean("shared", false))
            }
            projects.forEach { if (!byId.containsKey(it.id)) byId[it.id] = it } // keep offline-created ones
            val merged = byId.values.toList()
            if (merged.isNotEmpty()) {
                projects.clear(); projects.addAll(merged)
                if (projects.none { it.id == currentProjectId.value }) currentProjectId.value = projects.first().id
                saveProjects() // cache locally + push the union back to the server
            }
        }
        state.optJSONObject("conversations")?.let { convs ->
            val keys = convs.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = convs.optJSONArray(key) ?: continue
                val list = threads.getOrPut(key) { mutableStateListOf() }
                // Server history is authoritative on connect; rebuild when it has more than
                // we hold locally (covers fresh installs + proactive pushes queued offline).
                if (arr.length() > list.size) {
                    list.clear()
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o -> list.add(chatMsgFromJson(o)) }
                }
            }
        }
    }

    /** Proactive message from the agent (a background monitor/event fired) — append to its
     *  thread and pop a notification if the user isn't already looking at that chat. */
    override fun onPush(conv: String, text: String) = ui {
        if (text.isBlank()) return@ui
        val key = conv.ifBlank { threadKey() }
        val list = threads.getOrPut(key) { mutableStateListOf() }
        val last = list.lastOrNull()
        val dup = last != null && last.role == "assistant" && last.plain().trim() == text.trim()
        if (!dup) {
            list.add(ChatMsg("assistant").apply { segs.add(TextSeg(text)) })
            bump()
            if (key != threadKey()) {
                val name = persona(key.substringAfterLast(':'))?.name ?: t().agent
                Notifier.show(getApplication<Application>(), name, text)
            }
        }
    }

    private fun bump() { scrollTick.value++ }

    override fun onTextDelta(text: String) = ui { streamingMsg?.appendText(text); bump() }
    override fun onThinkingDelta(text: String) {}
    override fun onToolUse(command: String) = ui { streamingMsg?.addTool(command); bump() }
    override fun onToolResult(exitCode: Int, output: String) = ui { streamingMsg?.fillTool(exitCode, output); bump() }

    override fun onTurnEnd(stopReason: String?, outputTokens: Int?) = ui {
        streamingMsg?.let {
            materializeFiles(it); it.streaming = false
            persistMessage(streamingKey ?: threadKey(), it)
        }
        streamingMsg = null; streamingKey = null
        busy.value = false
        client?.getAudit(100)
    }

    override fun onInterrupted() = ui {
        streamingMsg?.let {
            it.appendText("\n— ${t().interrupted} —"); it.streaming = false
            persistMessage(streamingKey ?: threadKey(), it)
        }
        streamingMsg = null; streamingKey = null; busy.value = false
    }

    override fun onError(message: String) = ui {
        val m = streamingMsg
        if (m != null) {
            m.error = message; m.streaming = false
            persistMessage(streamingKey ?: threadKey(), m)
            streamingMsg = null; streamingKey = null
        }
        statusMsg.value = t().stError(message)
        busy.value = false
    }

    override fun onDisconnected(reason: String) = ui {
        client = null
        connecting = false // this attempt's WS phase ended → let a reconnect fire
        cancelWatchdog()
        abortStreaming(t().linkLost)
        if (!userDisconnected) {
            conn.value = ConnState.Connecting
            scheduleReconnect()
        } else {
            conn.value = ConnState.Disconnected
        }
    }

    override fun onClosed(reason: String) = ui {
        client = null
        connecting = false
        cancelWatchdog()
        abortStreaming(t().linkLost)
        if (!userDisconnected) {
            conn.value = ConnState.Connecting
            scheduleReconnect()
        } else {
            conn.value = ConnState.Disconnected
        }
    }
}
