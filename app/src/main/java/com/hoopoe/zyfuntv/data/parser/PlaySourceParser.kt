package com.hoopoe.zyfuntv.data.parser

import android.util.Log
import com.hoopoe.zyfuntv.data.remote.model.CmsEpisode
import com.hoopoe.zyfuntv.data.remote.model.CmsPlaySource

/**
 * 统一解析 vod_play_from / vod_play_url 为线路与剧集。
 * - 线路名（play_from）与线路块（play_url）均兼容 `$$$` 与逗号分隔；
 * - 剧集以 `#` 分隔，剧集内以首个 `$` 拆标题与地址；
 * - 纯地址（无标题）剧集兜底为「第 N 集」；
 * - 线路名数量不足时按序号补默认名。
 */
object PlaySourceParser {
    // 线路分隔符：标准 `$$$`，部分源用逗号；剧集内仍以 `#` 分集、`$` 拆标题与地址。
    // 注意：Kotlin 原始字符串中 `$` 仍会触发模板插值，故这里直接用字面字符串 split，不用正则，
    // 避免 `${3}` 被求值成数字后退化成正则反向引用而抛 PatternSyntaxException。
    private const val SOURCE_SEP = "$$$"

    fun parse(from: String, playUrl: String): List<CmsPlaySource> {
        if (from.isBlank() && playUrl.isBlank()) return emptyList()
        val names = from.split(SOURCE_SEP, ",").map { it.trim() }.filter { it.isNotEmpty() }
        val blocks = playUrl.split(SOURCE_SEP).map { it.trim() }.filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return emptyList()

        val result = blocks.mapIndexed { i, block ->
            val name = names.getOrElse(i) { "线路 ${i + 1}" }
            val eps = block.split("#").mapIndexed { idx, ep ->
                val e = ep.trim()
                if (e.isEmpty()) return@mapIndexed null
                val parts = e.split("$", limit = 2)
                val title = parts[0].trim().ifEmpty { "第 ${idx + 1} 集" }
                val url = if (parts.size == 2) parts[1].trim() else parts[0].trim()
                if (url.isEmpty()) null else CmsEpisode(title, url)
            }.filterNotNull()
            if (eps.isEmpty()) null else CmsPlaySource(name, eps)
        }.filterNotNull()

        Log.d(
            "ZyCat",
            "PlaySourceParser: names=${names.size} blocks=${blocks.size} sources=${result.size} " +
                "episodes=${result.sumOf { it.episodes.size }}"
        )
        return result
    }
}
