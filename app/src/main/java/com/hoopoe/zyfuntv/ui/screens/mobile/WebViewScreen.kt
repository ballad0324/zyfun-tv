package com.hoopoe.zyfuntv.ui.screens.mobile

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 内置带登录态浏览器容器：开启 JS 与 Cookie 持久化，供站点登录、扫码、授权回跳使用。
 * 注：导航接线与返回栈回退二期接入；当前为可复用容器。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    title: String? = null,
    onBack: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val target = request?.url?.toString() ?: return false
                            view?.loadUrl(target)
                            return true
                        }
                    }
                    if (url.isNotBlank()) loadUrl(url)
                }
            },
            update = { web ->
                if (title != null) {
                    // 标题由外层顶部栏展示；此处保留扩展位
                }
            }
        )
    }
}
