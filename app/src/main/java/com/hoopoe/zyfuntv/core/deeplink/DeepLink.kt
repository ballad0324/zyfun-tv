package com.hoopoe.zyfuntv.core.deeplink

import android.net.Uri

/** 深链解析目标 */
sealed class DeepLinkTarget {
    /** 订阅配置链接：导入远程 TVBox/Catvod 配置 */
    data class Subscribe(val url: String, val label: String? = null) : DeepLinkTarget()

    /** 无法识别的链接 */
    object Unknown : DeepLinkTarget()
}

/**
 * 解析外部拉起 / 粘贴的深链。
 * 兼容：
 *  - `zy://subscribe?url=<encoded>`、`zy://schemaSub?url=<encoded>`（对齐源项目 schemaSub）
 *  - 普通 `http(s)://...` 配置链接
 */
object DeepLink {

    private val schemes = listOf("subscribe", "schemasub", "sub")

    fun parse(raw: String?): DeepLinkTarget {
        if (raw.isNullOrBlank()) return DeepLinkTarget.Unknown
        val trimmed = raw.trim()
        return runCatching {
            val uri = Uri.parse(trimmed)
            when (uri.scheme?.lowercase()) {
                "zy" -> {
                    val host = uri.host?.lowercase().orEmpty()
                    val target = uri.getQueryParameter("url")
                        ?: uri.getQueryParameter("sub")
                        ?: return DeepLinkTarget.Unknown
                    if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
                        DeepLinkTarget.Subscribe(target, host)
                    } else DeepLinkTarget.Unknown
                }
                "http", "https" -> DeepLinkTarget.Subscribe(trimmed)
                else -> DeepLinkTarget.Unknown
            }
        }.getOrDefault(DeepLinkTarget.Unknown)
    }

    fun isSubscribeUrl(raw: String?): Boolean = parse(raw) is DeepLinkTarget.Subscribe
}
