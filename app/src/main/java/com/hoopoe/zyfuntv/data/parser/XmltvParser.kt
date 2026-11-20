package com.hoopoe.zyfuntv.data.parser

import com.hoopoe.zyfuntv.data.remote.model.EpgItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * XMLTV 整单节目单解析。
 * 两遍读取：先建 channel id -> display-name 映射，再按频道匹配 programme。
 */
object XmltvParser {

    private fun normalize(name: String): String =
        name.replace("-", "").replace(" ", "").uppercase()

    private fun parseTime(raw: String?): String {
        if (raw == null || raw.length < 12) return ""
        val hh = raw.substring(8, 10)
        val mm = raw.substring(10, 12)
        return "$hh:$mm"
    }

    fun parse(xml: String, program: String): List<EpgItem> {
        val target = normalize(program)
        if (target.isEmpty()) return emptyList()

        // 第一遍：channel 映射
        val channelMap = mutableMapOf<String, String>()
        runCatching {
            val p = newParser(xml)
            var event = p.eventType
            var currentId: String? = null
            var inDisplay = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "channel" -> currentId = p.getAttributeValue(null, "id")
                        "display-name" -> inDisplay = true
                    }
                    XmlPullParser.TEXT -> if (inDisplay && currentId != null) {
                        channelMap[currentId] = p.text?.trim().orEmpty()
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "display-name" -> inDisplay = false
                        "channel" -> currentId = null
                    }
                }
                event = p.next()
            }
        }.getOrNull()

        val matchedId = channelMap.entries
            .find { normalize(it.value) == target }
            ?.key ?: return emptyList()

        // 第二遍：programme
        val out = mutableListOf<EpgItem>()
        runCatching {
            val p = newParser(xml)
            var event = p.eventType
            var currentChannel: String? = null
            var inTitle = false
            var title = ""
            var start = ""
            var stop = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "programme" -> {
                            currentChannel = p.getAttributeValue(null, "channel")
                            start = parseTime(p.getAttributeValue(null, "start"))
                            stop = parseTime(p.getAttributeValue(null, "stop"))
                            title = ""
                        }
                        "title" -> inTitle = true
                    }
                    XmlPullParser.TEXT -> if (inTitle) title += p.text?.orEmpty().orEmpty()
                    XmlPullParser.END_TAG -> when (p.name) {
                        "title" -> inTitle = false
                        "programme" -> {
                            if (currentChannel == matchedId && title.isNotBlank()) {
                                out.add(EpgItem(title.trim(), start, stop))
                            }
                            currentChannel = null
                        }
                    }
                }
                event = p.next()
            }
        }.getOrNull()

        return out
    }

    private fun newParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val p = factory.newPullParser()
        p.setInput(java.io.StringReader(xml))
        return p
    }
}
