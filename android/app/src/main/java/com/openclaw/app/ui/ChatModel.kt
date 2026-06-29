package com.openclaw.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

/** Marker the agent emits to hand a file/photo back to the user (see backend `_FILE_PROTOCOL`). */
val FILE_MARKER = Regex("""\[\[FILE:([^\]]+)]]""")

/** A piece of an assistant message: streamed text, or a tool (terminal) block. */
sealed interface Seg

class TextSeg(initial: String = "") : Seg {
    var text by mutableStateOf(initial)
}

class ToolSeg(val command: String) : Seg {
    var output by mutableStateOf("")
    var exit by mutableStateOf<Int?>(null)
}

/** An attachment: a file sent by the user (localPath set) or returned by the agent
 *  (remotePath set, fetched on demand). Images render inline. */
class FileSeg(val name: String, val remotePath: String, val isImage: Boolean) : Seg {
    var localPath by mutableStateOf<String?>(null) // present once available locally (sent, or downloaded)
    var downloading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/** An interactive multiple-choice question the agent asked (AskUserQuestion). */
data class QOption(val label: String, val description: String)
data class Q(val question: String, val header: String, val multiSelect: Boolean, val options: List<QOption>)
class QuestionSeg(val id: String, val questions: List<Q>) : Seg {
    var answered by mutableStateOf(false)
}

/** Parse the AskUserQuestion `questions` payload into UI models. */
fun parseQuestions(arr: JSONArray): List<Q> {
    val out = ArrayList<Q>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val opts = ArrayList<QOption>()
        val oarr = o.optJSONArray("options") ?: JSONArray()
        for (j in 0 until oarr.length()) {
            val oo = oarr.optJSONObject(j) ?: continue
            opts.add(QOption(oo.optString("label"), oo.optString("description")))
        }
        out.add(Q(o.optString("question"), o.optString("header"), o.optBoolean("multiSelect"), opts))
    }
    return out
}

class ChatMsg(val role: String) {
    val segs: SnapshotStateList<Seg> = mutableStateListOf()
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun appendText(s: String) {
        val last = segs.lastOrNull()
        if (last is TextSeg) last.text += s
        else segs.add(TextSeg(s))
    }

    fun addTool(command: String) {
        segs.add(ToolSeg(command))
    }

    fun fillTool(exitCode: Int, out: String) {
        (segs.lastOrNull { it is ToolSeg } as? ToolSeg)?.let {
            it.exit = exitCode
            it.output = out
        }
    }

    /** Plain-text view (for the user bubble / simple display). */
    fun plain(): String = segs.filterIsInstance<TextSeg>().joinToString("") { it.text }
}

fun userMsg(text: String): ChatMsg = ChatMsg("user").apply { segs.add(TextSeg(text)) }
fun assistantMsg(): ChatMsg = ChatMsg("assistant").apply { streaming = true }

/** Serialize a finished message for server-side history (tool output capped to keep it small). */
fun ChatMsg.toJson(): JSONObject {
    val arr = JSONArray()
    segs.forEach { seg ->
        val o = JSONObject()
        when (seg) {
            is TextSeg -> { o.put("t", "text"); o.put("text", seg.text) }
            is ToolSeg -> {
                o.put("t", "tool"); o.put("command", seg.command)
                o.put("output", seg.output.take(4000)); seg.exit?.let { o.put("exit", it) }
            }
            is FileSeg -> { o.put("t", "file"); o.put("name", seg.name); o.put("path", seg.remotePath); o.put("image", seg.isImage) }
            // History keeps questions as plain text; the answer is its own user message.
            is QuestionSeg -> { o.put("t", "text"); o.put("text", seg.questions.joinToString("\n") { "❓ ${it.question}" }) }
        }
        arr.put(o)
    }
    val m = JSONObject().put("role", role).put("segs", arr)
    error?.let { m.put("error", it) }
    return m
}

/** Rebuild a message from stored history (non-streaming; images re-fetched on demand). */
fun chatMsgFromJson(o: JSONObject): ChatMsg {
    val m = ChatMsg(o.optString("role", "assistant"))
    val arr = o.optJSONArray("segs") ?: JSONArray()
    for (i in 0 until arr.length()) {
        val s = arr.optJSONObject(i) ?: continue
        when (s.optString("t")) {
            "text" -> m.segs.add(TextSeg(s.optString("text")))
            "tool" -> m.segs.add(ToolSeg(s.optString("command")).apply {
                output = s.optString("output")
                if (s.has("exit") && !s.isNull("exit")) exit = s.getInt("exit")
            })
            "file" -> m.segs.add(FileSeg(s.optString("name"), s.optString("path"), s.optBoolean("image")))
        }
    }
    m.streaming = false
    o.optString("error").takeIf { it.isNotBlank() }?.let { m.error = it }
    return m
}
