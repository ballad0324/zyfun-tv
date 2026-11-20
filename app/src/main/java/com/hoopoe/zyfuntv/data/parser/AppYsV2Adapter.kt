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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * AppYsV2 适配器（type=11，T3_APPYSV2）。
 * 两套子协议：
 *  - type1（ext 串含 ".vod"）：/types、/vodPhbAll、?tid=&page=、/detail?vod_id=、?wd=&page=，列表在 resp.data.list
 *  - type2：/nav、/index_video、/video?tid=&pg=、/video_detail?id=、/search?text=&pg=，列表在 resp.list||resp.data
 * 纯 GET JSON，无加密 / JS 注入。参考 zyfun adapter/t3AppYsV2.ts。
 */
class AppYsV2Adapter(
    private val fetcher: HttpFetcher,
    api: String?,
    ext: String?,
    private val referer: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** 源端以 ext 作为接口地址；兼容 ext 为 JSON {server:...} 的情况 */
    private val rawExt = ext?.trim().orEmpty()
    private val root: String = runCatching {
        val o = json.parseToJsonElement(rawExt).jsonObject
        (jstr(o["server"]).ifEmpty { jstr(o["host"]) }).ifEmpty { api ?: "" }
    }.getOrDefault(api ?: rawExt).trimEnd('/')

    /** ext 含 ".vod" 判 type1，否则 type2（对齐源端 this.type 判定） */
    private val type1 = rawExt.contains(".vod") || api?.contains(".vod") == true

    private val mediaExt = Regex("""\.(m3u8|mp4|mpd|flv|mkv)""", RegexOption.IGNORE_CASE)

    private fun getHome(url: String): String = runCatching {
        val u = URI(url)
        "${u.scheme}://${u.host}" + (if (u.port > 0 && u.port != 80 && u.port != 443) ":${u.port}" else "")
    }.getOrDefault(root)

    private fun fixPic(pic: String): String {
        if (pic.isEmpty()) return ""
        if (pic.startsWith("http")) return pic
        if (pic.startsWith("//")) return "https:$pic"
        val home = getHome(root)
        return if (pic.startsWith("/")) home + pic else "$home/$pic"
    }

    private suspend fun get(path: String): JsonObject? {
        val url = if (path.startsWith("?") || path.startsWith("/")) "$root$path" else "$root/$path"
        val text = fetcher.fetchText(url, referer) ?: return null
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    /** 分类 */
    suspend fun categories(): List<CmsCategory> {
        val obj = get(if (type1) "/types" else "/nav") ?: return emptyList()
        val src = if (type1) {
            obj["data"]?.jsonObject?.let { it["typelist"] ?: it["list"] }
        } else {
            obj["list"] ?: obj["data"]
        }
        val arr = runCatching { src?.jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val id = jstr(o["type_id"]).trim()
            val name = jstr(o["type_name"]).trim()
            if (id.isEmpty() || name.isEmpty()) null else CmsCategory(id, name)
        }.distinctBy { it.typeId }
    }

    /** 分类列表 / 搜索 */
    suspend fun list(tid: String, wd: String, page: Int): CmsPage {
        val obj = if (type1) {
            if (wd.isNotEmpty()) get("?wd=${enc(wd)}&page=$page")
            else get("?tid=${enc(tid)}&page=$page&class=&area=&lang=&letter=&year=&by=")
        } else {
            if (wd.isNotEmpty()) get("/search?text=${enc(wd)}&pg=$page")
            else get("/video?tid=${enc(tid)}&pg=$page&class=&area=&lang=&year=&by=")
        } ?: return CmsPage(page, 0, 0, emptyList())

        val srcList = if (type1) obj["data"]?.jsonObject?.get("list") else obj["list"] ?: obj["data"]
        val arr = runCatching { srcList?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        val dataObj = if (type1) obj["data"]?.jsonObject else null
        val list = arr.map { el ->
            val o = el.jsonObject
            CmsVod(
                id = jstr(o["vod_id"]),
                name = jstr(o["vod_name"]),
                pic = fixPic(jstr(o["vod_pic"])),
                remarks = jstr(o["vod_remarks"]),
                blurb = (jstr(o["vod_blurb"]).ifEmpty { jstr(o["vod_content"]) }).trim()
            )
        }.filter { it.id.isNotEmpty() }
        val total = jstr(dataObj?.get("total")).toIntOrNull() ?: 0
        val rawPage = jstr(dataObj?.get("page")).toIntOrNull() ?: page
        val limit = jstr(dataObj?.get("limit")).toIntOrNull() ?: 0
        val pageCount = when {
            total > 0 && limit > 0 -> Math.ceil(total.toDouble() / limit).toInt()
            list.isNotEmpty() -> 1
            else -> 0
        }
        return CmsPage(rawPage, pageCount, total, list)
    }

    /** 详情 */
    suspend fun detail(ids: String): CmsDetail? {
        val resp: JsonObject? = if (type1) get("/detail?vod_id=${enc(ids)}") else get("/video_detail?id=${enc(ids)}")
        val obj: JsonObject = resp ?: return null
        val dataObj: JsonObject? = asObject(obj["data"])
        val info: JsonObject? = asObject(dataObj?.get("vod_info"))
        val item: JsonObject = if (type1) (info ?: dataObj ?: obj) else (dataObj ?: obj)

        val sources = parseSources(item)
        return CmsDetail(
            id = jstr(item["vod_id"]).ifEmpty { ids },
            name = jstr(item["vod_name"]),
            pic = fixPic(jstr(item["vod_pic"])),
            remarks = jstr(item["vod_remarks"]),
            year = jstr(item["vod_year"]),
            lang = jstr(item["vod_lang"]),
            area = jstr(item["vod_area"]),
            score = jstr(item["vod_score"]).ifEmpty { jstr(item["vod_douban_score"]) },
            state = jstr(item["vod_state"]),
            klass = jstr(item["vod_class"]),
            actor = jstr(item["vod_actor"]),
            director = jstr(item["vod_director"]),
            content = jstr(item["vod_content"]).trim(),
            blurb = jstr(item["vod_blurb"]).trim(),
            typeName = jstr(item["type_name"]),
            sources = sources
        )
    }

    private fun parseSources(item: JsonObject): List<CmsPlaySource> {
        val episodes = if (type1) {
            runCatching { item["vod_play_list"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        } else {
            runCatching { item["vod_url_with_player"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        }
        val byLine = LinkedHashMap<String, MutableList<CmsEpisode>>()
        episodes.forEach { el ->
            val e = el.jsonObject
            val from = if (type1) {
                e["player_info"]?.jsonObject?.let { jstr(it["from"]).ifEmpty { jstr(it["show"]) } }
                    ?: jstr(e["from"]).ifEmpty { jstr(e["show"]) }
            } else {
                jstr(e["code"]).ifEmpty { jstr(e["name"]) }
            }
            val url = jstr(e["url"])
            if (from.isNotEmpty() && url.isNotEmpty()) {
                byLine.getOrPut(from) { mutableListOf() }.add(CmsEpisode(from, url))
            }
        }
        return byLine.map { (name, eps) -> CmsPlaySource(name, eps) }
    }

    /** 播放：非媒体后缀标记需解析；直链直放 */
    fun play(play: String): PlayResult {
        return if (mediaExt.containsMatchIn(play)) PlayResult(play, false, null)
        else PlayResult(play, true, "AppYsV2 需解析")
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    private fun jstr(el: kotlinx.serialization.json.JsonElement?): String =
        el?.let { runCatching { it.jsonPrimitive.content }.getOrDefault("") } ?: ""

    private fun asObject(el: kotlinx.serialization.json.JsonElement?): JsonObject? =
        runCatching { el?.jsonObject }.getOrNull()
}
