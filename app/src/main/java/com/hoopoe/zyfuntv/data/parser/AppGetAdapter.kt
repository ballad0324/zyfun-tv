package com.hoopoe.zyfuntv.data.parser

import com.hoopoe.zyfuntv.core.crypto.Crypto
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
import java.net.URLEncoder

/**
 * AppGet / AppQi / AppFox 适配器（type=15/16/17，T3_APPGET/APPQI/APPFOX）。
 * 协议：AES-CBC/PKCS7 登录取 token；initV / typeFilterVodList / vodDetail / searchList / vodParse 响应体解密。
 * ext JSON：{ "appConfig": { host, username, password, key, iv, lazyheader } }。
 * 参考 zyfun adapter/t3AppGet.ts。会员时长 eval 表达式本轮不移植（仅影响 VIP 到期提示，不影响取片）。
 */
class AppGetAdapter(
    private val fetcher: HttpFetcher,
    api: String?,
    ext: String?,
    siteType: Int,
    private val referer: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val cfg: JsonObject = runCatching {
        json.parseToJsonElement(ext ?: "").jsonObject["appConfig"]?.jsonObject
    }.getOrNull() ?: JsonObject(emptyMap())

    private val host = jstr(cfg["host"]).trimEnd('/')
    private val username = jstr(cfg["username"])
    private val password = jstr(cfg["password"])
    private val key = jstr(cfg["key"])
    private val iv = jstr(cfg["iv"])
    private val lazyHeader: Map<String, String> = runCatching {
        cfg["lazyheader"]?.jsonObject?.entries?.associate { (k, v) -> k to jstr(v) }
    }.getOrNull() ?: emptyMap()

    /** csp_AppGet / type15 → AppGet 路径表；其余（AppQi/AppFox）→ AppQiji 路径表 */
    private val mubanAppGet = api == "csp_AppGet" || siteType == 15

    private val headers = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.105 MUOUAPP/10.8.4506.400"
    )

    private val apiPaths = if (mubanAppGet) APP_GET else APP_QIJI

    private val mediaExt = Regex("""\.(m3u8|mp4|mpd|flv|mkv)""", RegexOption.IGNORE_CASE)

    private fun buildUrl(path: String, vararg params: Pair<String, String>): String {
        val base = if (path.startsWith("http")) path else "$host/$path"
        if (params.isEmpty()) return base
        val qs = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return if (base.contains("?")) "$base&$qs" else "$base?$qs"
    }

    /** POST 并返回原始响应文本（detail 整体解密需要原始密文） */
    private suspend fun postText(path: String, body: String, vararg params: Pair<String, String>): String? {
        val url = buildUrl(path, *params)
        return fetcher.postJson(url, body, referer, headers)
    }

    private suspend fun post(path: String, body: String, vararg params: Pair<String, String>): JsonObject? {
        val text = postText(path, body, *params) ?: return null
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    /** 登录：有账号密码时换取 app-user-token；失败不阻断后续请求 */
    suspend fun init(): Boolean {
        if (host.isEmpty()) return false
        if (username.isNotEmpty() && password.isNotEmpty()) {
            val body = """{"password":${jsonStr(password)},"code":"","device_id":"","user_name":${jsonStr(username)},"invite_code":"","key":"","is_emulator":0}"""
            val resp = post(apiPaths["appLogin"] ?: return false, body)
            val data = decryptField(resp)
            val token = jstr(data?.get("user")?.jsonObject?.get("auth_token"))
            if (token.isNotEmpty()) headers["app-user-token"] = token
        }
        return true
    }

    /** 分类：initV 解密 type_list */
    suspend fun categories(): List<CmsCategory> {
        val resp = post(apiPaths["initV"] ?: return emptyList(), "{}")
        val data = decryptField(resp) ?: return emptyList()
        val arr = runCatching { data["type_list"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val id = jstr(o["type_id"]).trim()
            val name = jstr(o["type_name"]).trim()
            if (id.isEmpty() || name.isEmpty()) null else CmsCategory(id, name)
        }.distinctBy { it.typeId }
    }

    /** 分类列表 / 搜索 */
    suspend fun list(tid: String, wd: String, page: Int): CmsPage {
        val resp = if (wd.isNotEmpty()) {
            post(apiPaths["searchList"] ?: return CmsPage(page, 0, 0, emptyList()), "{}",
                "keywords" to wd, "page" to page.toString(), "type_id" to "0")
        } else {
            post(apiPaths["typeFilterVodList"] ?: return CmsPage(page, 0, 0, emptyList()), "{}",
                "area" to "全部", "year" to "全部", "type_id" to tid, "page" to page.toString(),
                "sort" to "最新", "lang" to "全部", "class" to "全部")
        } ?: return CmsPage(page, 0, 0, emptyList())
        val data = decryptField(resp)
        val key = if (wd.isNotEmpty()) "search_list" else "recommend_list"
        val arr = runCatching { data?.get(key)?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        val list = arr.map { el ->
            val o = el.jsonObject
            CmsVod(
                id = jstr(o["vod_id"]),
                name = jstr(o["vod_name"]),
                pic = jstr(o["vod_pic"]),
                remarks = jstr(o["vod_remarks"]),
                blurb = (jstr(o["vod_blurb"]).ifEmpty { jstr(o["vod_content"]) }).trim()
            )
        }.filter { it.id.isNotEmpty() }
        return CmsPage(page, if (list.isNotEmpty()) 1 else 0, list.size, list)
    }

    /** 详情：vodDetail 解密整体响应取 data.vod */
    suspend fun detail(ids: String): CmsDetail? {
        val id = ids.split(",", "，").firstOrNull()?.trim().orEmpty()
        val rawText = postText(apiPaths["vodDetail"] ?: return null, "{}", "vod_id" to id) ?: return null
        // 源端 detail 直接 decrypt(resp 整体)，再取 data.vod；失败回退按 data 字段解密
        val decrypted = decryptRaw(rawText)
            ?: runCatching { json.parseToJsonElement(rawText).jsonObject }.getOrNull()
                ?.let { decryptField(it) }
            ?: return null
        val item = decrypted["data"]?.jsonObject?.get("vod")?.jsonObject ?: return null

        val sources = parseSources(item)
        return CmsDetail(
            id = jstr(item["vod_id"]).ifEmpty { id },
            name = jstr(item["vod_name"]),
            pic = jstr(item["vod_pic"]),
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
        val playlist = runCatching { item["vod_play_list"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        val byLine = LinkedHashMap<String, MutableList<CmsEpisode>>()
        playlist.forEach { el ->
            val p = el.jsonObject
            val show = jstr(p["player_info"]?.jsonObject?.get("show"))
            val urls = runCatching { p["urls"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
            val eps = urls.mapNotNull { u ->
                val uo = u.jsonObject
                val epName = jstr(uo["name"])
                // 整段 JSON 作为播放串，play 阶段再解析 url/token/parse_api_url
                val epJson = uo.toString()
                if (epName.isEmpty()) null else CmsEpisode(epName, epJson)
            }
            if (show.isNotEmpty() && eps.isNotEmpty()) byLine[show] = eps.toMutableList()
        }
        return byLine.map { (name, eps) -> CmsPlaySource(name, eps) }
    }

    /**
     * 播放：play 为详情阶段存入的 urls[] 项 JSON。
     * 取 url / token / parse_api_url；parse_api_url 直连或走 vodParse 二次解析。
     */
    suspend fun play(play: String): PlayResult {
        val o = runCatching { json.parseToJsonElement(play).jsonObject }.getOrNull()
            ?: return PlayResult(play, !mediaExt.containsMatchIn(play), "AppGet 播放串非 JSON", lazyHeader)
        val url = jstr(o["url"])
        val token = jstr(o["token"])
        var parseApi = jstr(o["parse_api_url"])

        // 分支一：parse_api_url 自带 url= 且为 http，直接请求解析接口
        if (parseApi.startsWith("http") && parseApi.contains("url=")) {
            val text = fetcher.fetchText(parseApi, referer, headers)
            val parsed = runCatching { json.parseToJsonElement(text ?: "").jsonObject }.getOrNull()
            val pUrl = jstr(parsed?.get("url"))
            if (pUrl.isNotEmpty()) {
                val finalUrl = if (mediaExt.containsMatchIn(pUrl)) pUrl else "$pUrl&type=m3u8"
                return PlayResult(finalUrl, false, null, lazyHeader)
            }
            return PlayResult(parseApi, true, "AppGet 解析接口未返回 url", lazyHeader)
        }

        // 分支二：走 vodParse，url 先 AES 加密再 URL 编码
        if (parseApi.isNotEmpty() && url.isNotEmpty()) {
            parseApi = parseApi.replace(url, "")
            val encUrl = URLEncoder.encode(encrypt(url) ?: "", "UTF-8")
            val body = """{"parse_api":${jsonStr(parseApi)},"url":${jsonStr(encUrl)},"token":${jsonStr(token)}}"""
            val resp = post(apiPaths["vodParse"] ?: return PlayResult(url, true, "无 vodParse 路径", lazyHeader), body)
            val decrypted = decryptField(resp)
            val playJson = runCatching {
                decrypted?.get("json")?.let { json.parseToJsonElement(jstr(it)) }?.jsonObject
            }.getOrNull()
            val playUrl = jstr(playJson?.get("url"))
            if (playUrl.isNotEmpty()) {
                val finalUrl = if (mediaExt.containsMatchIn(playUrl)) playUrl else "$playUrl&type=m3u8"
                return PlayResult(finalUrl, false, null, lazyHeader)
            }
        }

        if (mediaExt.containsMatchIn(url)) return PlayResult(url, false, null, lazyHeader)
        return PlayResult(url, true, "AppGet 需网页嗅探", lazyHeader)
    }

    /** 解密响应：先取 data 字段再 AES 解密为 JSON 对象 */
    private fun decryptField(resp: JsonObject?): JsonObject? {
        if (resp == null) return null
        val data = jstr(resp["data"])
        if (data.isEmpty()) return null
        val plain = decrypt(data) ?: return null
        return runCatching { json.parseToJsonElement(plain).jsonObject }.getOrNull()
    }

    /** 解密响应整体字符串（detail 分支） */
    private fun decryptRaw(text: String): JsonObject? {
        val plain = decrypt(text) ?: return null
        return runCatching { json.parseToJsonElement(plain).jsonObject }.getOrNull()
    }

    private fun encrypt(value: String): String? =
        if (key.isEmpty()) null else Crypto.aesCbcEncrypt(value, key, iv)

    private fun decrypt(value: String): String? = runCatching {
        if (key.isEmpty()) return@runCatching value
        Crypto.aesCbcDecrypt(value, key, iv) ?: value
    }.getOrDefault(value)

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

    private companion object {
        val APP_GET = mapOf(
            "appLogin" to "api.php/getappapi.index/appLogin",
            "initV" to "api.php/getappapi.index/initV119",
            "typeFilterVodList" to "api.php/getappapi.index/typeFilterVodList",
            "vodDetail" to "api.php/getappapi.index/vodDetail",
            "searchList" to "api.php/getappapi.index/searchList",
            "vodParse" to "api.php/getappapi.index/vodParse"
        )
        val APP_QIJI = mapOf(
            "appLogin" to "api.php/qijiappapi.index/appLogin",
            "initV" to "api.php/qijiappapi.index/initV120",
            "typeFilterVodList" to "api.php/qijiappapi.index/typeFilterVodList",
            "vodDetail" to "api.php/qijiappapi.index/vodDetail2",
            "searchList" to "api.php/qijiappapi.index/searchList",
            "vodParse" to "api.php/qijiappapi.index/vodParse"
        )
    }
}
