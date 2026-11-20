package com.hoopoe.zyfuntv.data.parser

import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsEpisode
import com.hoopoe.zyfuntv.data.remote.model.CmsPage
import com.hoopoe.zyfuntv.data.remote.model.CmsPlaySource
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * T0 XML（MacCMS v10 / 苹果CMS）解析。
 * 结构：rss.class.ty[]、rss.list.video[]、video.dl.dd[]（flag 线路名 + 文本播放串）。
 */
object T0XmlParser {

    private fun parser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val p = factory.newPullParser()
        p.setInput(java.io.StringReader(xml))
        return p
    }

    /** 分类：<ty id="..">名称</ty> */
    fun parseCategories(xml: String): List<CmsCategory> = runCatching {
        val p = parser(xml)
        val out = mutableListOf<CmsCategory>()
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == "ty") {
                val id = p.getAttributeValue(null, "id").orEmpty()
                val text = p.nextText().trim()
                if (id.isNotEmpty() && text.isNotEmpty()) out.add(CmsCategory(id, text))
            }
            event = p.next()
        }
        out.distinctBy { it.typeId }
    }.getOrDefault(emptyList())

    /** 列表：rss.list.video[]，含分页 page/pagecount/recordcount */
    fun parseList(xml: String, fallbackPage: Int): CmsPage = runCatching {
        val p = parser(xml)
        val vods = mutableListOf<CmsVod>()
        var page = fallbackPage
        var pagecount = 0
        var recordcount = 0
        var event = p.eventType
        var current: MutableMap<String, String>? = null
        var inList = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "list" -> inList = true
                    "video" -> current = mutableMapOf()
                    "page" -> page = p.nextText().trim().toIntOrNull() ?: page
                    "pagecount" -> pagecount = p.nextText().trim().toIntOrNull() ?: 0
                    "recordcount" -> recordcount = p.nextText().trim().toIntOrNull() ?: 0
                    else -> {
                        val c = current
                        if (c != null && inList) {
                            val tag = p.name
                            c[tag] = p.nextText().trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "video" -> {
                        current?.let { vods.add(toVod(it)) }
                        current = null
                    }
                    "list" -> inList = false
                }
            }
            event = p.next()
        }
        CmsPage(page, pagecount, recordcount, vods.filter { it.id.isNotEmpty() })
    }.getOrDefault(CmsPage(fallbackPage, 0, 0, emptyList()))

    /** 详情：取首个 video，解析 dl.dd 线路 */
    fun parseDetail(xml: String, fallbackId: String): CmsDetail? = runCatching {
        val p = parser(xml)
        var event = p.eventType
        var current: MutableMap<String, String>? = null
        val dds = mutableListOf<Pair<String, String>>()
        var inDl = false
        var detailMap: MutableMap<String, String>? = null
        var detailDds: List<Pair<String, String>>? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "video" -> {
                        current = mutableMapOf()
                        dds.clear()
                        inDl = false
                    }
                    "dl" -> inDl = true
                    "dd" -> {
                        val flag = p.getAttributeValue(null, "flag").orEmpty()
                        dds.add(flag to p.nextText().trim())
                    }
                    else -> {
                        val c = current
                        if (c != null && !inDl) c[p.name] = p.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "dd" -> {}
                    "dl" -> inDl = false
                    "video" -> {
                        if (detailMap == null) {
                            detailMap = current
                            detailDds = dds.toList()
                        }
                        current = null
                    }
                }
            }
            event = p.next()
        }

        val m = detailMap ?: return@runCatching null
        val sources = parseDdSources(detailDds.orEmpty())
        CmsDetail(
            id = m["id"]?.ifEmpty { m["vod_id"] } ?: fallbackId,
            name = m["name"] ?: m["vod_name"].orEmpty(),
            pic = m["pic"] ?: m["vod_pic"].orEmpty(),
            remarks = m["note"] ?: m["vod_remarks"].orEmpty(),
            year = m["year"].orEmpty(),
            lang = m["lang"].orEmpty(),
            area = m["area"].orEmpty(),
            score = "0.0",
            state = m["state"].orEmpty(),
            klass = m["type"].orEmpty(),
            actor = m["actor"].orEmpty(),
            director = m["director"].orEmpty(),
            content = m["des"] ?: m["vod_content"].orEmpty(),
            blurb = m["des"] ?: m["vod_blurb"].orEmpty(),
            typeName = m["type"].orEmpty(),
            sources = sources
        )
    }.getOrNull()

    private fun toVod(m: Map<String, String>): CmsVod = CmsVod(
        id = m["id"] ?: m["vod_id"].orEmpty(),
        name = m["name"] ?: m["vod_name"].orEmpty(),
        pic = m["pic"] ?: m["vod_pic"].orEmpty(),
        remarks = m["note"] ?: m["vod_remarks"].orEmpty(),
        blurb = m["desc"] ?: m["vod_blurb"].orEmpty()
    )

    /** dd: flag 为线路名，文本为 `剧集$地址#...`，多线路以 dds 顺序对应 */
    private fun parseDdSources(dds: List<Pair<String, String>>): List<CmsPlaySource> {
        if (dds.isEmpty()) return emptyList()
        return dds.mapIndexed { idx, (flag, text) ->
            val name = flag.ifEmpty { "线路 ${idx + 1}" }
            val eps = text.split("#").mapIndexed { eIdx, ep ->
                val parts = ep.split("$", limit = 2)
                if (parts.size == 2 && parts[1].trim().isNotEmpty()) {
                    CmsEpisode(parts[0].ifEmpty { "第 ${eIdx + 1} 集" }, parts[1].trim())
                } else null
            }.filterNotNull()
            CmsPlaySource(name, eps)
        }.filter { it.episodes.isNotEmpty() }
    }
}
