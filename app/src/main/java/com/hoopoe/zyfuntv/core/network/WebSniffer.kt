package com.hoopoe.zyfuntv.core.network

import android.content.Context
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

/** 嗅探结果：媒体直链 + 需回传给播放器的请求头（Referer / Cookie / UA） */
data class SniffResult(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * 轻量 WebView 嗅探兜底：分享页 / 播放器页面无法用正则从静态文本直出时，
 * 加载隐藏 WebView，拦截媒体网络请求并执行 JS 提取 video/source 与常见全局播放变量。
 * 完整 CDP 嗅探仍后置；本类仅提取 m3u8/mp4/mpd/flv/mkv 直链，并回传 Referer/Cookie。
 */
object WebSniffer {

    private val mediaRegex =
        Regex("""(https?://[^\s"'\\<>]+?\.(?:m3u8|mp4|mpd|flv|mkv)(?:\?[^\s"'<>\\]*)?)""", RegexOption.IGNORE_CASE)

    /** JS 播放变量赋值：var main = "/xxx/index.m3u8?sign=" / url:"/xxx.mp4" 等 */
    private val varMediaRegex = Regex(
        """(?:var\s+|let\s+|const\s+)?(main|url|play|src|source|video|file|path|playUrl|videoUrl)\s*[:=]\s*["']([^"']+?\.(?:m3u8|mp4|mpd|flv|mkv)(?:\?[^"']*)?)["']""",
        RegexOption.IGNORE_CASE
    )

    /** 相对路径媒体：以 / 开头的站点根相对地址（协议相对 // 开头也兼容） */
    private val relMediaRegex = Regex(
        """["']?((?:/|//)[^\s"'\\<>]+?\.(?:m3u8|mp4|mpd|flv|mkv)(?:\?[^\s"'<>\\]*)?)["']?""",
        RegexOption.IGNORE_CASE
    )

    /** 静态文本正则提取首个媒体直链；[baseUrl] 非空时把相对路径拼接为绝对地址。无命中返回 null。 */
    fun staticExtract(html: String?, baseUrl: String? = null): String? {
        if (html.isNullOrEmpty()) return null
        mediaRegex.find(html)?.let { return it.value }
        varMediaRegex.find(html)?.let { m -> return resolve(baseUrl, m.groupValues[2]) }
        relMediaRegex.find(html)?.let { m -> return resolve(baseUrl, m.groupValues[1]) }
        return null
    }

    /** 相对路径按 baseUrl 解析为绝对地址；已是 http(s) 原样返回，base 缺失时保留原串 */
    internal fun resolve(baseUrl: String?, rel: String): String {
        if (rel.startsWith("http://", true) || rel.startsWith("https://", true)) return rel
        if (rel.startsWith("//")) return "https:$rel"
        if (baseUrl.isNullOrBlank()) return rel
        return runCatching { java.net.URL(java.net.URL(baseUrl), rel).toString() }.getOrDefault(rel)
    }

    private val extractJs = """
        (function(){
          var out = [];
          function push(v){ if (v && typeof v === 'string' && v.trim()) out.push(v.trim()); }
          try {
            document.querySelectorAll('video,source,audio').forEach(function(v){
              push(v.src); push(v.currentSrc);
            });
          } catch(e){}
          var keys = ['player','__player__','vodUrl','main','url','play','src','source','video','file','path','playUrl','videoUrl','__playinfo'];
          keys.forEach(function(k){
            try {
              var o = window[k];
              if (!o) return;
              if (typeof o === 'string') push(o);
              else { push(o.src); push(o.url); push(o.playUrl); push(o.videoUrl); }
            } catch(e){}
          });
          return out.join('|');
        })();
    """.trimIndent()

    /**
     * 嗅探页面首个媒体直链，连同请求头一起返回；超时或未命中返回 null。必须在持有 Context 的环境调用。
     */
    suspend fun sniff(
        context: Context,
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 12000L
    ): SniffResult? = withContext(Dispatchers.Main) {
        if (url.isEmpty()) return@withContext null
        val appCtx = context.applicationContext
        suspendCancellableCoroutine { cont ->
            val web = WebView(appCtx)
            // 命中记录：媒体地址 -> 命中请求携带的 Referer（可能为空）
            val found = CopyOnWriteArrayList<Pair<String, String?>>()
            var finished = false

            fun finish() {
                if (finished) return
                finished = true
                runCatching { web.destroy() }
                if (!cont.isActive) return
                val hit = found.firstOrNull()
                cont.resume(
                    hit?.let { (mediaUrl, referer) ->
                        val h = mutableMapOf<String, String>()
                        headers["User-Agent"]?.let { h["User-Agent"] = it }
                        runCatching {
                            CookieManager.getInstance().getCookie(url)
                        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { h["Cookie"] = it }
                        h["Referer"] = referer ?: url
                        SniffResult(mediaUrl, h)
                    }
                )
            }

            runCatching {
                web.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    headers["User-Agent"]?.let { userAgentString = it }
                }
                web.webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        // 调试期：模拟器根证书不全导致解析站证书不被信任，放行以保证嗅探可加载
                        handler?.proceed()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val u = request?.url?.toString().orEmpty()
                        if (u.isNotEmpty() && mediaRegex.containsMatchIn(u)) {
                            val referer = request?.requestHeaders?.get("Referer")
                            ?: request?.requestHeaders?.get("referer")
                        found.add(u to referer)
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        val base = finishedUrl ?: url
                        view?.evaluateJavascript(extractJs) { value ->
                            value?.let { v ->
                                v.split("|").forEach { raw ->
                                    val cleaned = raw.trim().removeSurrounding("\"").removeSurrounding("'")
                                    if (cleaned.isNotEmpty()) {
                                        val resolved = resolve(base, cleaned)
                                        if (mediaRegex.containsMatchIn(resolved)) {
                                            found.add(resolved to null)
                                        }
                                    }
                                }
                            }
                            if (found.isNotEmpty()) finish()
                        }
                    }
                }
                val extra = headers.filterKeys { !it.equals("User-Agent", true) }
                web.loadUrl(url, extra)

                val timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(timeoutMs)
                    finish()
                }
                cont.invokeOnCancellation {
                    timeoutJob.cancel()
                    runCatching { web.destroy() }
                }
            }.onFailure { finish() }
        }
    }
}
