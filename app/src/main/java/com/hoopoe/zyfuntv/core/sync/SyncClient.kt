package com.hoopoe.zyfuntv.core.sync

import com.hoopoe.zyfuntv.data.repository.BackupSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** 手机端局域网同步客户端：与电视端 SyncServer 握手并推送全量备份 */
object SyncClient {

    data class Endpoint(val host: String, val port: Int)
    data class Probe(val ok: Boolean, val message: String, val device: String = "")
    data class Push(val ok: Boolean, val message: String, val summary: BackupSummary? = null)
    data class Pull(val ok: Boolean, val message: String, val backup: String? = null)
    data class DiscoveredDevice(val host: String, val port: Int, val device: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 扫描专用短超时客户端，避免逐台等待拖慢发现过程 */
    private val scanClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析用户输入，支持 `192.168.1.20` 或 `192.168.1.20:9123` */
    fun parseEndpoint(input: String): Endpoint? {
        val s = input.trim().removePrefix("http://").removeSuffix("/")
        if (s.isEmpty()) return null
        val idx = s.lastIndexOf(':')
        return if (idx > 0 && s.substring(idx + 1).all { it.isDigit() }) {
            Endpoint(s.substring(0, idx), s.substring(idx + 1).toIntOrNull() ?: DEFAULT_PORT)
        } else {
            Endpoint(s, DEFAULT_PORT)
        }
    }

    /**
     * 扫描同局域网内在线的电视设备：取本机 IPv4 网段，并发探测 192.168/10/172 网段，
     * 依次尝试 9123 起的端口，GET /info 取回设备名。不带配对码（服务端仍返回 device 名）。
     */
    suspend fun discover(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val local = LanAddress.localIpv4() ?: return@withContext emptyList()
        val prefix = local.substringBeforeLast('.', local)
        if (prefix == local) return@withContext emptyList()
        coroutineScope {
            (1..254).map { i ->
                async {
                    val host = "$prefix.$i"
                    if (host == local) return@async null
                    for (port in DISCOVER_PORTS) {
                        fetchDevice(host, port)?.let { return@async it }
                    }
                    null
                }
            }.awaitAll().filterNotNull().sortedBy { it.host }
        }
    }

    private fun fetchDevice(host: String, port: Int): DiscoveredDevice? = runCatching {
        val url = "http://$host:$port/info"
        val resp = scanClient.newCall(Request.Builder().url(url).get().build()).execute()
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful || body.isEmpty()) return@runCatching null
        val root = json.parseToJsonElement(body).jsonObject
        val device = strOf(root, "device")
        if (device.isNotEmpty()) DiscoveredDevice(host, port, device) else null
    }.getOrNull()

    /** 握手：校验配对码并取回电视设备名 */
    fun probe(endpoint: Endpoint, code: String): Probe {
        return runCatching {
            val url = "http://${endpoint.host}:${endpoint.port}/info?code=${encode(code)}"
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val codeOk = boolOf(root, "codeOk")
            val device = strOf(root, "device")
            when {
                !resp.isSuccessful -> Probe(false, "连接失败（HTTP ${resp.code}）")
                !codeOk -> Probe(false, "配对码无效或已过期")
                else -> Probe(true, "已连接：$device", device)
            }
        }.getOrElse { Probe(false, "无法连接电视：${it.message ?: "网络错误"}") }
    }

    /** 推送全量备份文本，电视端落库后返回导入统计 */
    fun push(endpoint: Endpoint, code: String, backup: String): Push {
        return runCatching {
            val url = "http://${endpoint.host}:${endpoint.port}/sync/config?code=${encode(code)}"
            val req = Request.Builder()
                .url(url)
                .post(backup.toRequestBody(MEDIA_TYPE))
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val ok = boolOf(root, "ok")
            val message = strOf(root, "message")
            if (!ok) return Push(false, message.ifEmpty { "推送被拒绝" })
            val summary = BackupSummary(
                sites = intOf(root, "sites"),
                parses = intOf(root, "parses"),
                iptv = intOf(root, "iptv"),
                channels = intOf(root, "channels"),
                history = intOf(root, "history"),
                stars = intOf(root, "stars"),
                settingsApplied = boolOf(root, "settingsApplied")
            )
            Push(true, message.ifEmpty { "同步完成" }, summary)
        }.getOrElse { Push(false, "推送失败：${it.message ?: "网络错误"}") }
    }

    /** 从对端拉取全量备份文本，由调用方本地落库 */
    fun pull(endpoint: Endpoint, code: String): Pull {
        return runCatching {
            val url = "http://${endpoint.host}:${endpoint.port}/sync/backup?code=${encode(code)}"
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string().orEmpty()
            val root = json.parseToJsonElement(body).jsonObject
            val ok = boolOf(root, "ok")
            val message = strOf(root, "message")
            if (!ok) return Pull(false, message.ifEmpty { "拉取被拒绝" })
            val backup = strOf(root, "backup")
            if (backup.isEmpty()) Pull(false, "对端备份为空") else Pull(true, "已获取对端备份", backup)
        }.getOrElse { Pull(false, "拉取失败：${it.message ?: "网络错误"}") }
    }

    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s.trim(), "UTF-8")

    private fun strOf(root: JsonObject, key: String): String =
        runCatching { root[key]!!.jsonPrimitive.content }.getOrDefault("")

    private fun intOf(root: JsonObject, key: String): Int =
        runCatching { root[key]!!.jsonPrimitive.content.toInt() }.getOrDefault(0)

    private fun boolOf(root: JsonObject, key: String): Boolean =
        runCatching { root[key]!!.jsonPrimitive.content.toBooleanStrictOrNull() ?: false }
            .getOrDefault(false)

    private const val DEFAULT_PORT = 9123
    private val DISCOVER_PORTS = (9123..9133).toList()
    private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
}
