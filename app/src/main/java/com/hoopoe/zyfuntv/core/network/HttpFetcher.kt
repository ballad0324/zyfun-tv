package com.hoopoe.zyfuntv.core.network

import android.util.Log
import com.hoopoe.zyfuntv.core.debug.DebugLog
import com.hoopoe.zyfuntv.core.model.ProxyType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一 HTTP 抓取封装。UA / Referer / 超时 / 代理在此注入。
 * 代理：direct 直连；system 跟随系统；custom 走自定义 host:port。
 * 所有方法均为 suspend，阻塞 IO 调用收敛在 withContext(Dispatchers.IO) 内，不在主线程 runBlocking。
 */
class HttpFetcher(
    @Volatile private var userAgent: String = "",
    @Volatile private var timeoutMs: Long = 15000L,
    @Volatile private var proxyType: String = ProxyType.DIRECT,
    @Volatile private var customProxy: String = "",
    @Volatile private var ignoreSsl: Boolean = false
) {
    @Volatile private var client: OkHttpClient = buildClient()

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .applyTls(ignoreSsl)
        when (proxyType) {
            ProxyType.SYSTEM -> builder.proxySelector(ProxySelector.getDefault())
            ProxyType.CUSTOM -> parseProxy(customProxy)?.let { builder.proxy(it) }
            // DIRECT：保持默认直连
        }
        return builder.build()
    }

    private fun parseProxy(raw: String): Proxy? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val hostPort = trimmed.replace("http://", "").replace("https://", "")
            val idx = hostPort.lastIndexOf(':')
            if (idx > 0) {
                val host = hostPort.substring(0, idx)
                val port = hostPort.substring(idx + 1).toInt()
                Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
            } else null
        }.getOrNull()
    }

    /** 按最新设置重建客户端（UA / 超时 / 代理 / 忽略证书） */
    fun configure(ua: String, timeoutMs: Long, proxyType: String, customProxy: String, ignoreSsl: Boolean = false) {
        userAgent = ua
        this.timeoutMs = timeoutMs.coerceIn(3000L, 60000L)
        this.proxyType = proxyType
        this.customProxy = customProxy
        this.ignoreSsl = ignoreSsl
        client = buildClient()
    }

    /** 抓取文本；失败返回 null。不抛异常到 UI 层。headers 为额外请求头（token/自定义等）。 */
    suspend fun fetchText(url: String, referer: String? = null, headers: Map<String, String>? = null): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url).get()
            if (userAgent.isNotEmpty()) builder.header("User-Agent", userAgent)
            if (!referer.isNullOrEmpty()) builder.header("Referer", referer)
            headers?.forEach { (k, v) -> if (k.isNotEmpty() && v.isNotEmpty()) builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string()
                Log.d("ZyNet", "GET $url -> code=${resp.code} len=${body?.length ?: -1}")
                DebugLog.d("ZyNet", "GET ${resp.code} len=${body?.length ?: -1} $url")
                if (resp.isSuccessful) body else {
                    DebugLog.w("ZyNet", "GET 非成功 code=${resp.code} $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("ZyNet", "GET $url FAIL ${e.javaClass.simpleName}: ${e.message}")
            DebugLog.w("ZyNet", "GET 失败 ${e.javaClass.simpleName}: ${e.message} $url")
            null
        }
    }

    /** POST JSON 并返回响应文本；失败返回 null。body 为已序列化的 JSON 字符串。headers 为额外请求头。 */
    suspend fun postJson(
        url: String,
        body: String,
        referer: String? = null,
        headers: Map<String, String>? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val media = "application/json; charset=utf-8".toMediaType()
            val reqBody = body.toRequestBody(media)
            val builder = Request.Builder().url(url).post(reqBody)
            if (userAgent.isNotEmpty()) builder.header("User-Agent", userAgent)
            if (!referer.isNullOrEmpty()) builder.header("Referer", referer)
            headers?.forEach { (k, v) -> if (k.isNotEmpty() && v.isNotEmpty()) builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val respBody = resp.body?.string()
                Log.d("ZyNet", "POST $url -> code=${resp.code} len=${respBody?.length ?: -1}")
                DebugLog.d("ZyNet", "POST ${resp.code} len=${respBody?.length ?: -1} $url")
                if (resp.isSuccessful) respBody else {
                    DebugLog.w("ZyNet", "POST 非成功 code=${resp.code} $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("ZyNet", "POST $url FAIL ${e.javaClass.simpleName}: ${e.message}")
            DebugLog.w("ZyNet", "POST 失败 ${e.javaClass.simpleName}: ${e.message} $url")
            null
        }
    }

    /** POST 表单（application/x-www-form-urlencoded）并返回响应文本；失败返回 null。 */
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        referer: String? = null,
        headers: Map<String, String>? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val body = form.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val reqBody = body.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val builder = Request.Builder().url(url).post(reqBody)
            if (userAgent.isNotEmpty()) builder.header("User-Agent", userAgent)
            if (!referer.isNullOrEmpty()) builder.header("Referer", referer)
            headers?.forEach { (k, v) -> if (k.isNotEmpty() && v.isNotEmpty()) builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val respBody = resp.body?.string()
                Log.d("ZyNet", "POST(form) $url -> code=${resp.code} len=${respBody?.length ?: -1}")
                DebugLog.d("ZyNet", "POST(form) ${resp.code} len=${respBody?.length ?: -1} $url")
                if (resp.isSuccessful) respBody else {
                    DebugLog.w("ZyNet", "POST(form) 非成功 code=${resp.code} $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("ZyNet", "POST(form) $url FAIL ${e.javaClass.simpleName}: ${e.message}")
            DebugLog.w("ZyNet", "POST(form) 失败 ${e.javaClass.simpleName}: ${e.message} $url")
            null
        }
    }

    /** PUT 文本内容（用于网盘上传文件）；成功返回 true。headers 为额外请求头。 */
    suspend fun putText(
        url: String,
        body: String,
        referer: String? = null,
        headers: Map<String, String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBody = body.toRequestBody("application/octet-stream".toMediaType())
            val builder = Request.Builder().url(url).put(reqBody)
            if (userAgent.isNotEmpty()) builder.header("User-Agent", userAgent)
            if (!referer.isNullOrEmpty()) builder.header("Referer", referer)
            headers?.forEach { (k, v) -> if (k.isNotEmpty() && v.isNotEmpty()) builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val ok = resp.isSuccessful
                Log.d("ZyNet", "PUT $url -> code=${resp.code}")
                if (ok) DebugLog.d("ZyNet", "PUT ${resp.code} len=${body.length} $url")
                else DebugLog.w("ZyNet", "PUT 非成功 code=${resp.code} $url")
                ok
            }
        } catch (e: Exception) {
            Log.w("ZyNet", "PUT $url FAIL ${e.javaClass.simpleName}: ${e.message}")
            DebugLog.w("ZyNet", "PUT 失败 ${e.javaClass.simpleName}: ${e.message} $url")
            false
        }
    }

    companion object {
        fun newId(): UUID = UUID.randomUUID()
    }
}
