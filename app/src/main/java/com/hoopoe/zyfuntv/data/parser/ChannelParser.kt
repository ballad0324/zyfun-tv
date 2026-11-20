package com.hoopoe.zyfuntv.data.parser

/** 解析后的频道原始条目，落库前再关联直播源 */
data class RawChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val headers: Map<String, String> = emptyMap()
)

object ChannelParser {

    private fun attr(line: String, key: String): String? {
        val regex = Regex("""$key\s*=\s*"([^"]*)"""")
        return regex.find(line)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }

    /** 解析 M3U / M3U8 文本（规则见 docs/01 §5.2） */
    fun parseM3U(text: String): List<RawChannel> {
        val out = mutableListOf<RawChannel>()
        var pendingName = ""
        var logo: String? = null
        var group: String? = null
        val headers = mutableMapOf<String, String>()

        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pendingName = if (line.contains(",")) line.substringAfter(",").trim() else ""
                    logo = attr(line, "tvg-logo")
                    attr(line, "group-title")?.let { group = it }
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val opt = line.removePrefix("#EXTVLCOPT:")
                    val k = opt.substringBefore("=")
                    val v = opt.substringAfter("=")
                    when (k) {
                        "http-referrer" -> headers["Referer"] = v
                        "http-user-agent" -> headers["User-Agent"] = v
                    }
                }
                line.startsWith("#") -> Unit
                line.isNotEmpty() -> {
                    out += RawChannel(
                        name = pendingName.ifEmpty { "未命名" },
                        url = line,
                        logo = logo,
                        group = group,
                        headers = headers.toMap()
                    )
                    pendingName = ""
                    logo = null
                    headers.clear()
                }
            }
        }
        return out
    }

    /** 解析 TXT 文本：`分组名,#genre#` 标记分组，`频道名,http地址` 为一条 */
    fun parseTxt(text: String): List<RawChannel> {
        val out = mutableListOf<RawChannel>()
        var group: String? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.endsWith(",#genre#")) {
                group = line.substringBefore(",").trim()
                continue
            }
            val parts = line.split(",", limit = 2)
            if (parts.size == 2 && parts[1].trimStart().startsWith("http")) {
                out += RawChannel(
                    name = parts[0].trim(),
                    url = parts[1].trim(),
                    group = group
                )
            }
        }
        return out
    }
}
