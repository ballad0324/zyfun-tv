package com.hoopoe.zyfuntv.data.parser

import com.hoopoe.zyfuntv.core.network.HttpFetcher
import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsEpisode
import com.hoopoe.zyfuntv.data.remote.model.CmsPage
import com.hoopoe.zyfuntv.data.remote.model.CmsPlaySource
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.remote.model.PlayResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CatVod / TVBox 自定义 JSON-RPC 适配器（type=8）。
 * 协议：POST JSON 到 /init /home /category /detail /search /play，响应字段与 MacCMS 同构。
 * 参考 zyfun adapter/t4Catvod.ts。
 */
class CatvodAdapter(
    private val fetcher: HttpFetcher,
    private val api: String,
    private val referer: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val root = api.trimEnd('/')

    private fun url(path: String) = "$root$path"

    private suspend fun post(path: String, body: String): JsonObject? {
        val text = fetcher.postJson(url(path), body, referer) ?: return null
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    /** 分类：POST /home {} */
    suspend fun categories(): List<CmsCategory> {
        val obj = post("/home", "{}") ?: return emptyList()
        val arr = obj["class"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val id = jstr(o["type_id"]).trim()
            val name = jstr(o["type_name"]).trim()
            if (id.isEmpty() || name.isEmpty()) null else CmsCategory(id, name)
        }
    }

    /** 分类列表：POST /category {id,page,filters}；搜索：POST /search {wd,pg} */
    suspend fun list(tid: String, wd: String, page: Int): CmsPage {
        val obj = if (wd.isNotEmpty()) {
            post("/search", """{"wd":${jsonStr(wd)},"pg":$page}""")
        } else {
            post("/category", """{"id":${jsonStr(tid)},"page":$page,"filters":{}}""")
        } ?: return CmsPage(page, 0, 0, emptyList())
        return parsePage(obj, page)
    }

    /** 详情：POST /detail {id} */
    suspend fun detail(ids: String): CmsDetail? {
        val obj = post("/detail", """{"id":${jsonStr(ids)}}""") ?: return null
        val arr = obj["list"]?.jsonArray ?: return null
        val o = arr.firstOrNull()?.jsonObject ?: return null
        return CmsDetail(
            id = jstr(o["vod_id"]).ifEmpty { ids },
            name = jstr(o["vod_name"]),
            pic = jstr(o["vod_pic"]),
            remarks = jstr(o["vod_remarks"]),
            year = jstr(o["vod_year"]),
            lang = jstr(o["vod_lang"]),
            area = jstr(o["vod_area"]),
            score = jstr(o["vod_score"]).ifEmpty { jstr(o["vod_douban_score"]) },
            state = jstr(o["vod_state"]),
            klass = jstr(o["vod_class"]),
            actor = jstr(o["vod_actor"]),
            director = jstr(o["vod_director"]),
            content = jstr(o["vod_content"]),
            blurb = jstr(o["vod_blurb"]),
            typeName = jstr(o["type_name"]),
            sources = parseSources(jstr(o["vod_play_from"]), jstr(o["vod_play_url"]))
        )
    }

    /** 播放：POST /play {flag,id}；返回直链与 headers */
    suspend fun play(flag: String, playId: String): PlayResult {
        val obj = post("/play", """{"flag":${jsonStr(flag)},"id":${jsonStr(playId)}}""")
            ?: return PlayResult("", true, "CatVod 播放接口无响应")
        var raw = jstr(obj["url"])
        // js2p 代理地址：取 /proxy/<flag>/<id> 末段（对齐源端正则）
        if (raw.startsWith("js2p")) {
            val m = Regex("""/proxy/[^/]+/([^/]+)""").find(raw)
            raw = m?.groupValues?.getOrNull(1) ?: ""
        }
        val headers = parseHeaderObj(obj["headers"]) ?: parseHeaderObj(obj["header"]) ?: emptyMap()
        return if (raw.isNotEmpty()) PlayResult(raw, false, null, headers)
        else PlayResult("", true, "CatVod 未返回播放地址", headers)
    }

    private fun parsePage(obj: JsonObject, page: Int): CmsPage {
        val arr = obj["list"]?.jsonArray ?: emptyList()
        val list = arr.map { el ->
            val o = el.jsonObject
            CmsVod(
                id = jstr(o["vod_id"]),
                name = jstr(o["vod_name"]),
                pic = jstr(o["vod_pic"]),
                remarks = jstr(o["vod_remarks"]),
                blurb = jstr(o["vod_blurb"])
            )
        }.filter { it.id.isNotEmpty() }
        return CmsPage(
            page = jstr(obj["page"]).toIntOrNull() ?: page,
            pageCount = jstr(obj["pagecount"]).toIntOrNull() ?: 0,
            total = jstr(obj["total"]).toIntOrNull() ?: 0,
            list = list
        )
    }

    private fun parseSources(from: String, playUrl: String): List<CmsPlaySource> =
        PlaySourceParser.parse(from, playUrl)

    private fun parseHeaderObj(el: kotlinx.serialization.json.JsonElement?): Map<String, String>? {
        val o = runCatching { el?.jsonObject }.getOrNull() ?: return null
        return o.entries.associate { (k, v) -> k to jstr(v) }.takeIf { it.isNotEmpty() }
    }

    private fun jstr(el: kotlinx.serialization.json.JsonElement?): String =
        el?.let { runCatching { it.jsonPrimitive.content }.getOrDefault("") } ?: ""

    private fun jsonStr(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}
