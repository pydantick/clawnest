package com.openclaw.app

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

data class AgentEndpoint(val wsUrl: String, val token: String)

/**
 * One SSH session does everything: it runs assets/provision.sh to make sure the
 * openclaw agent is configured and to read its token, then opens an SSH local
 * port-forward to the agent's WebSocket port. All agent traffic rides INSIDE the
 * SSH connection (SSH-encrypted) — no VPN, no public port. The app then just talks
 * plain ws:// to 127.0.0.1:<forwarded port>.
 */
class SshLink(private val context: Context) {

    private var session: Session? = null

    private fun en() = context.getSharedPreferences("openclaw", Context.MODE_PRIVATE)
        .getString("lang", "ru") == "en"
    private fun m(ru: String, eng: String) = if (en()) eng else ru

    // Try standard SSH (22) first, then 443 — many mobile/Wi-Fi networks block 22
    // but never 443. The server is set up to accept SSH on both.
    private val sshPorts = intArrayOf(22, 443)

    fun connect(host: String, user: String, password: String, claudeKey: String): AgentEndpoint {
        val s = openSession(host, user, password)
        session = s

        val result = runProvision(s, claudeKey)
        val agentHost = if (result.has("agent_host")) result.getString("agent_host") else "10.13.37.1"
        val agentPort = if (result.has("agent_port")) result.getInt("agent_port") else 8765
        val token = result.getString("token")

        // localhost:<assigned> -> (over ssh) -> agentHost:agentPort on the server
        val localPort = try {
            s.setPortForwardingL(0, agentHost, agentPort)
        } catch (e: Exception) {
            throw RuntimeException("forward: ${e.message ?: e.javaClass.simpleName}")
        }
        return AgentEndpoint("ws://127.0.0.1:$localPort/", token)
    }

    fun close() {
        try {
            session?.disconnect()
        } catch (_: Exception) {
        }
        session = null
    }

    /** SFTP-upload a local file to [remotePath] on the server (over the same SSH session),
     *  creating parent dirs as needed. Blocking — call off the main thread. */
    fun uploadFile(localPath: String, remotePath: String) {
        val s = session ?: throw RuntimeException(m("нет SSH-соединения", "no SSH connection"))
        val sftp = s.openChannel("sftp") as ChannelSftp
        sftp.connect(15_000)
        try {
            mkdirs(sftp, remotePath.substringBeforeLast('/', ""))
            File(localPath).inputStream().use { sftp.put(it, remotePath, ChannelSftp.OVERWRITE) }
        } finally {
            sftp.disconnect()
        }
    }

    /** SFTP-download [remotePath] from the server into [localFile]. Blocking. */
    fun downloadFile(remotePath: String, localFile: File) {
        val s = session ?: throw RuntimeException(m("нет SSH-соединения", "no SSH connection"))
        val sftp = s.openChannel("sftp") as ChannelSftp
        sftp.connect(15_000)
        try {
            localFile.parentFile?.mkdirs()
            localFile.outputStream().use { sftp.get(remotePath, it) }
        } finally {
            sftp.disconnect()
        }
    }

    private fun mkdirs(sftp: ChannelSftp, dir: String) {
        if (dir.isBlank() || dir == "/") return
        var path = ""
        for (part in dir.trim('/').split('/')) {
            if (part.isEmpty()) continue
            path += "/$part"
            try {
                sftp.stat(path)
            } catch (_: Exception) {
                try { sftp.mkdir(path) } catch (_: Exception) {}
            }
        }
    }

    private fun openSession(host: String, user: String, password: String): Session {
        var last: Exception? = null
        for (port in sshPorts) {
            try {
                val jsch = JSch()
                val s = jsch.getSession(user, host, port)
                s.setPassword(password)
                s.setConfig("StrictHostKeyChecking", "no")
                s.serverAliveInterval = 20_000
                s.serverAliveCountMax = 3
                s.connect(10_000)
                return s
            } catch (e: Exception) {
                last = e
            }
        }
        throw RuntimeException("SSH: ${last?.message ?: m("нет связи (порты 22/443)", "no connection (ports 22/443)")}")
    }

    private fun runProvision(s: Session, claudeKey: String): JSONObject {
        val script = context.assets.open("provision.sh").bufferedReader().use { it.readText() }
            .replace("__CLAUDE_KEY__", shEscape(claudeKey))
        // Bound provision with a socket timeout: after a network/VPN switch the old session
        // can go half-open, and an unbounded read here would hang the whole connect forever.
        val prevTimeout = try { s.timeout } catch (_: Exception) { 0 }
        try { s.timeout = 20_000 } catch (_: Exception) {}
        val channel = s.openChannel("exec") as ChannelExec
        channel.setCommand("bash -s")
        val stdin = channel.outputStream
        val stdout = channel.inputStream
        val errBuf = ByteArrayOutputStream()
        channel.setErrStream(errBuf)
        val out: String
        val exit: Int
        try {
            channel.connect(10_000)
            stdin.write(script.toByteArray(Charsets.UTF_8))
            stdin.flush()
            stdin.close()
            out = stdout.readBytes().toString(Charsets.UTF_8)
            val deadline = System.currentTimeMillis() + 20_000
            while (!channel.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(50)
            exit = channel.exitStatus
        } finally {
            try { channel.disconnect() } catch (_: Exception) {}
            try { s.timeout = prevTimeout } catch (_: Exception) {} // restore 0 for the persistent forward
        }

        val marker = "===OPENCLAW-RESULT==="
        val start = out.indexOf(marker)
        val end = out.indexOf("===END===")
        if (start < 0 || end < 0 || end < start) {
            val detail = errBuf.toString("UTF-8").ifBlank { out }.trim().takeLast(300)
            throw RuntimeException(m("установка не вернула результат (exit=$exit). $detail",
                "setup returned no result (exit=$exit). $detail"))
        }
        return JSONObject(out.substring(start + marker.length, end).trim())
    }

    /** Escape a value to sit safely inside single quotes in bash. */
    private fun shEscape(s: String): String = s.replace("'", "'\\''")
}
