package com.openclaw.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Callbacks arrive on OkHttp's reader thread — the Activity marshals them to the UI thread. */
interface OpenClawListener {
    fun onAuthOk(models: List<ModelInfo>)
    fun onPersonas(personas: List<Persona>)
    fun onTextDelta(text: String)
    fun onThinkingDelta(text: String)
    fun onToolUse(command: String)
    fun onToolResult(exitCode: Int, output: String)
    fun onTurnEnd(stopReason: String?, outputTokens: Int?)
    fun onInterrupted()
    fun onError(message: String)
    /** Transport died (socket failure / clean close) — distinct from app-level onError,
     *  so the ViewModel can auto-reconnect without treating it as a turn error. */
    fun onDisconnected(reason: String)
    fun onClosed(reason: String)
    fun onAudit(entries: List<AuditEntry>)
    /** Server-stored state (projects + chat transcripts) returned after get_state. */
    fun onState(state: JSONObject)
    /** Agent asked a multiple-choice question (AskUserQuestion) — render interactive options. */
    fun onQuestion(id: String, questions: JSONArray)
    /** Agent sent a proactive message (a background monitor/event fired). */
    fun onPush(conv: String, text: String)
}

/** Typed WebSocket client for the openclaw agent. Mirrors backend/openclaw_agent/server.py. */
class OpenClawClient(
    private val url: String,
    private val listener: OpenClawListener,
) {
    // No client-side ping: the server already pings every 20s and OkHttp's reader thread
    // auto-replies with a pong (never blocked by app work), which keeps the link + NAT
    // alive. A client ping here would falsely kill a healthy connection whenever the
    // server's event loop is busy for >20s during a long turn ("didn't receive pong").
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null

    fun connect(onOpen: () -> Unit) {
        val req = Request.Builder().url(url).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onDisconnected(t.message ?: "websocket failure")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(reason)
            }
        })
    }

    private fun send(o: JSONObject) {
        ws?.send(o.toString())
    }

    fun auth(token: String) = send(JSONObject().put("type", "auth").put("token", token))

    fun setApiKey(key: String) = send(JSONObject().put("type", "set_api_key").put("key", key))

    fun listPersonas() = send(JSONObject().put("type", "list_personas"))

    fun getAudit(limit: Int = 100) = send(JSONObject().put("type", "get_audit").put("limit", limit))

    fun savePersona(p: Persona) {
        val tools = JSONArray()
        p.allowedTools.forEach { tools.put(it) }
        val o = JSONObject()
            .put("id", p.id)
            .put("name", p.name)
            .put("emoji", p.emoji)
            .put("default_model", p.defaultModel)
            .put("theme_color", p.themeColor)
            .put("allowed_tools", tools)
            .put("system_prompt", p.systemPrompt)
            .put("all_tools", p.allTools)
        send(JSONObject().put("type", "save_persona").put("persona", o))
    }

    fun prompt(persona: String, model: String?, conversationId: String, project: String, text: String, cwd: String? = null) {
        val o = JSONObject()
            .put("type", "prompt")
            .put("persona", persona)
            .put("conversation_id", conversationId)
            .put("project", project)
            .put("text", text)
        if (model != null) o.put("model", model)
        if (!cwd.isNullOrBlank()) o.put("cwd", cwd)
        send(o)
    }

    fun renameProject(old: String, new: String) =
        send(JSONObject().put("type", "rename_project").put("old", old).put("new", new))

    /** Persist one rendered chat message under its thread key ("<project>:<persona>"). */
    fun saveMessage(conversationId: String, message: JSONObject) =
        send(JSONObject().put("type", "save_message").put("conversation_id", conversationId).put("message", message))

    /** Persist the project list (id/name/cwd/shared) so a reinstall restores it. */
    fun saveProjects(projects: JSONArray) =
        send(JSONObject().put("type", "save_projects").put("projects", projects))

    fun clearConversation(conversationId: String) =
        send(JSONObject().put("type", "clear_conversation").put("conversation_id", conversationId))

    /** Ask the server for stored projects + transcripts (called on connect). */
    fun getState() = send(JSONObject().put("type", "get_state"))

    fun interrupt() = send(JSONObject().put("type", "interrupt"))

    fun close() {
        ws?.close(1000, null)
        ws = null
    }

    private fun str(o: JSONObject, key: String): String? = if (o.has(key) && !o.isNull(key)) o.getString(key) else null

    private fun handle(text: String) {
        val m = try {
            JSONObject(text)
        } catch (e: Exception) {
            listener.onError("bad json from agent")
            return
        }
        when (m.optString("type")) {
            "auth_ok" -> {
                val models = ArrayList<ModelInfo>()
                val arr = m.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    models.add(ModelInfo(id, o.optString("label", id)))
                }
                listener.onAuthOk(models)
            }
            "personas" -> {
                val list = ArrayList<Persona>()
                val arr = m.optJSONArray("personas") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val toolsArr = o.optJSONArray("allowed_tools")
                    val tools = ArrayList<String>()
                    if (toolsArr != null) for (j in 0 until toolsArr.length()) tools.add(toolsArr.optString(j))
                    list.add(
                        Persona(
                            id = o.getString("id"),
                            name = str(o, "name") ?: o.getString("id"),
                            emoji = str(o, "emoji") ?: "",
                            defaultModel = str(o, "default_model"),
                            themeColor = str(o, "theme_color"),
                            allowedTools = tools,
                            systemPrompt = str(o, "system_prompt") ?: "",
                            allTools = o.optBoolean("all_tools", false),
                        )
                    )
                }
                listener.onPersonas(list)
            }
            "text_delta" -> listener.onTextDelta(m.optString("text"))
            "thinking_delta" -> listener.onThinkingDelta(m.optString("text"))
            "tool_use" -> listener.onToolUse(m.optString("command"))
            "tool_result" -> listener.onToolResult(m.optInt("exit_code"), m.optString("output"))
            "turn_end" -> {
                val usage = m.optJSONObject("usage")
                val out = if (usage != null && usage.has("output_tokens")) usage.getInt("output_tokens") else null
                listener.onTurnEnd(str(m, "stop_reason"), out)
            }
            "interrupted" -> listener.onInterrupted()
            "audit" -> {
                val entries = ArrayList<AuditEntry>()
                val arr = m.optJSONArray("entries") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    entries.add(
                        AuditEntry(
                            ts = o.optDouble("ts", 0.0),
                            persona = o.optString("persona", "?"),
                            command = o.optString("command", ""),
                            exitCode = o.optInt("exit_code", 0),
                        )
                    )
                }
                listener.onAudit(entries)
            }
            "state" -> listener.onState(m)
            "question" -> listener.onQuestion(m.optString("id"), m.optJSONArray("questions") ?: JSONArray())
            "push" -> listener.onPush(m.optString("conv"), m.optString("text"))
            "ok" -> Unit // set_api_key / save_* ack
            "error" -> listener.onError(m.optString("message"))
            else -> Unit
        }
    }
}
