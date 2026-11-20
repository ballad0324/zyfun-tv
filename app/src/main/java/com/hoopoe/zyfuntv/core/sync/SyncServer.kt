package com.hoopoe.zyfuntv.core.sync

import com.hoopoe.zyfuntv.data.repository.BackupSummary
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 电视端局域网同步服务：监听本地端口，生成短时配对码，
 * 暴露 /info（握手校验）与 /sync/config（接收手机端推送的全量备份并落库）。
 * 纯 ServerSocket 实现，不引入第三方 HTTP 依赖；仅在同步页打开期间运行。
 */
class SyncServer(
    private val deviceName: String,
    private val onImport: suspend (String) -> BackupSummary,
    private val onExport: suspend () -> String
) {
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null

    @Volatile private var running = false
    @Volatile var port: Int = 0
        private set
    @Volatile var code: String = ""
        private set
    @Volatile private var codeExpireAt: Long = 0
    @Volatile var lastResult: String? = null
        private set

    val isRunning: Boolean get() = running

    /** 启动服务，返回本机局域网 IP；绑定失败返回 null */
    fun start(): String? {
        if (running) return LanAddress.localIpv4()
        val socket = tryBind() ?: return null
        server = socket
        port = socket.localPort
        pool = Executors.newCachedThreadPool()
        running = true
        rotateCode()
        Thread { acceptLoop() }.start()
        return LanAddress.localIpv4()
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        pool?.shutdownNow()
        server = null
        pool = null
    }

    /** 刷新配对码，2 分钟内有效 */
    fun rotateCode() {
        code = "%06d".format(Random().nextInt(1_000_000))
        codeExpireAt = System.currentTimeMillis() + CODE_TTL_MS
    }

    fun codeValid(): Boolean = System.currentTimeMillis() <= codeExpireAt

    private fun tryBind(): ServerSocket? {
        for (p in PORT_START..PORT_START + PORT_RANGE) {
            runCatching { return ServerSocket(p) }
        }
        return null
    }

    private fun acceptLoop() {
        while (running) {
            val client = runCatching { server?.accept() }.getOrNull() ?: break
            pool?.execute { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        runCatching {
            client.use { sock ->
                val ins = sock.getInputStream()
                val requestLine = ins.readLine() ?: return@use
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: return@use
                val target = parts.getOrNull(1) ?: return@use

                var contentLength = 0
                while (true) {
                    val line = ins.readLine() ?: break
                    if (line.isBlank()) break
                    val idx = line.indexOf(':')
                    if (idx > 0 && line.substring(0, idx).trim().equals("Content-Length", true)) {
                        contentLength = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                    }
                }

                val body = if (contentLength > 0) {
                    val buf = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = ins.read(buf, read, contentLength - read)
                        if (n == -1) break
                        read += n
                    }
                    String(buf, 0, read, Charsets.UTF_8)
                } else ""

                val path = target.substringBefore('?')
                val query = target.substringAfter('?', "")
                val qp = parseQuery(query)

                when {
                    path == "/info" -> {
                        val ok = codeMatch(qp["code"])
                        respond(sock, 200, """{"ok":true,"codeOk":$ok,"device":"${jsonEscape(deviceName)}"}""")
                    }
                    path == "/sync/config" -> {
                        if (!codeMatch(qp["code"])) {
                            respond(sock, 403, """{"ok":false,"message":"配对码无效或已过期"}""")
                            return@use
                        }
                        val summary = runCatching { runBlocking { onImport(body) } }.getOrNull()
                        lastResult = if (summary != null) {
                            "站点 ${summary.sites} · 解析 ${summary.parses} · 直播源 ${summary.iptv} · 频道 ${summary.channels} · 历史 ${summary.history} · 收藏 ${summary.stars}"
                        } else {
                            "导入失败：备份格式无法识别"
                        }
                        respond(sock, 200, summaryJson(summary, lastResult!!))
                    }
                    path == "/sync/backup" -> {
                        if (!codeMatch(qp["code"])) {
                            respond(sock, 403, """{"ok":false,"message":"配对码无效或已过期"}""")
                            return@use
                        }
                        val backup = runCatching { runBlocking { onExport() } }.getOrNull()
                        if (backup == null) {
                            respond(sock, 200, """{"ok":false,"message":"本机备份导出失败"}""")
                        } else {
                            respond(sock, 200, """{"ok":true,"message":"ok","backup":"${jsonEscape(backup)}"}""")
                        }
                    }
                    else -> respond(sock, 404, """{"ok":false,"message":"not found"}""")
                }
            }
        }
    }

    private fun codeMatch(input: String?): Boolean {
        if (!codeValid()) return false
        val v = input?.trim().orEmpty()
        return v.isNotEmpty() && v == code
    }

    private fun summaryJson(summary: BackupSummary?, message: String): String {
        if (summary == null) {
            return """{"ok":false,"message":"${jsonEscape(message)}"}"""
        }
        return """{"ok":true,"message":"${jsonEscape(message)}","sites":${summary.sites},"parses":${summary.parses},"iptv":${summary.iptv},"channels":${summary.channels},"history":${summary.history},"stars":${summary.stars},"settingsApplied":${summary.settingsApplied}}"""
    }

    private fun respond(sock: Socket, status: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val os = sock.getOutputStream()
        val reason = if (status == 200) "OK" else "Error"
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "Access-Control-Allow-Origin: *\r\n\r\n"
        os.write(head.toByteArray(Charsets.UTF_8))
        os.write(bytes)
        os.flush()
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split("&").filter { it.isNotEmpty() }.mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@mapNotNull null
            val k = pair.substring(0, idx)
            val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            k to v
        }.toMap()

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    /** 从输入流按行读取（CRLF / LF 兼容），避免 BufferedReader 缓冲吞掉请求体 */
    private fun InputStream.readLine(): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8)
            if (b == '\r'.code) continue
            if (b == '\n'.code) return buf.toString(Charsets.UTF_8)
            buf.write(b)
        }
    }

    companion object {
        private const val PORT_START = 9123
        private const val PORT_RANGE = 10
        private const val CODE_TTL_MS = 120_000L
    }
}
