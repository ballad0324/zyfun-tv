package com.hoopoe.zyfuntv.core.network

import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS 兼容工具。
 *
 * 第三方影视/解析源常出现证书链不全、使用较新根（如 ISRG Root X2 / YE2）或自签证书，
 * 在部分模拟器 / 旧设备系统 CA 库中会报 Trust anchor for certification path not found。
 *
 * [relax] 为 true 时安装信任所有证书的 TrustManager 并放行主机名校验，仅用于兼容这类源；
 * 默认 false，保持系统严格校验。该开关对应设置中的“忽略 HTTPS 证书校验”，默认关闭。
 */

private val trustAllManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private val relaxedHostnameVerifier = HostnameVerifier { _, _ -> true }

/** 在 builder 上按需应用放宽 TLS 校验；relax=false 时不做任何改动，沿用系统默认信任链。 */
fun OkHttpClient.Builder.applyTls(relax: Boolean): OkHttpClient.Builder {
    if (!relax) return this
    return runCatching {
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), java.security.SecureRandom())
        }
        sslSocketFactory(sslContext.socketFactory, trustAllManager)
        hostnameVerifier(relaxedHostnameVerifier)
    }.getOrElse { this }
}
