package com.hoopoe.zyfuntv.data.parser

import com.hoopoe.zyfuntv.core.network.HttpFetcher
import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsEpisode
import com.hoopoe.zyfuntv.data.remote.model.CmsPage
import com.hoopoe.zyfuntv.data.remote.model.CmsPlaySource
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Alist 网盘目录适配器（type=13，T3_ALIST）。
 * 协议：Alist v3 / v2 公开 REST。
 *  - 探测：GET  /api/public/settings（v3 data 为对象，v2 data 为数组）
 *  - 列表：POST /api/fs/list（v3）/ /api/public/path（v2）
 *  - 详情：POST /api/fs/get（v3）/ /api/public/path（v2）
 *  - 搜索：POST /api/fs/search（仅 v3）
 * 配置 ext(JSON)：{server, startPage, showAll, params, username, password}；server 也可直接填 api。
 * 参考 zyfun adapter/t3Alist.ts。登录 token 与目录密码本轮后置。
 */
class AlistAdapter(
    private val fetcher: HttpFetcher,
    api: String?,
    ext: String?,
    private val referer: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val cfg = parseExt(ext)
    private val server = (cfg["server"] ?: api ?: "").trimEnd('/')
    private val startPage = cfg["startPage"] ?: "/"
    private val showAll = cfg["showAll"].let { it == "true" || it == "1" }
    private val username = cfg["username"] ?: ""
    private val password = cfg["password"] ?: ""

    private var v3: Boolean = true
    private var pageSize = 30
    private var title = ""
    private var token: String? = null

    private val videoExt = Regex(
        """.*\.(m3u8|mp4|mpd|flv|mkv|avi|ts|mov|wmv|rmvb|webm)""",
        RegexOption.IGNORE_CASE
    )

    /** 探测站点版本与设置；不可达返回 false。需在 IO 协程内调用 */
    suspend fun init(): Boolean {
        if (server.isEmpty()) return false
        val text = fetcher.fetchText("$server/api/public/settings", referer) ?: return false
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        val data = obj["data"]
        val arr = runCatching { data?.jsonArray }.getOrNull()
        if (arr != null) {
            v3 = false
            title = arr.firstOrNull { jstr(it.jsonObject["key"]) == "title" }
                ?.let { jstr(it.jsonObject["value"]) } ?: ""
            pageSize = arr.firstOrNull { jstr(it.jsonObject["key"]) == "default page size" }
                ?.let { jstr(it.jsonObject["value"]).toIntOrNull() } ?: 30
        } else {
            val d = runCatching { data?.jsonObject }.getOrNull()
            v3 = true
            title = d?.let { jstr(it["site_title"]) } ?: ""
            pageSize = d?.let { jstr(it["default_page_size"]).toIntOrNull() } ?: 30
        }
        loginIfNeeded()
        return true
    }

    /** 账号密码非空时登录，缓存 token；公开盘跳过 */
    private suspend fun loginIfNeeded() {
        if (username.isBlank()) return
        val body = """{"username":${jsonStr(username)},"password":${jsonStr(password)}}"""
        val obj = post("/api/auth/login", body) ?: return
        token = jstr(obj["data"]?.jsonObject?.get("token")).ifEmpty { null }
    }

    /** 分类：网盘目录单一“默认”入口 */
    fun categories(): List<CmsCategory> = listOf(CmsCategory("default", "默认"))

    /** 目录列表；wd 非空走搜索 */
    suspend fun list(tid: String, wd: String, page: Int): CmsPage {
        if (server.isEmpty()) return CmsPage(page, 0, 0, emptyList())
        val path = if (tid == "default" || tid.isEmpty()) startPage else tid
        val rawList: JsonArray
        val total: Int
        if (wd.isNotEmpty()) {
            if (!v3) return CmsPage(page, 0, 0, emptyList())
            val body = """{"keywords":${jsonStr(wd)},"parent":"/","page":$page,"per_page":$pageSize,"scope":0}"""
            val obj = post("/api/fs/search", body) ?: return CmsPage(page, 0, 0, emptyList())
            rawList = runCatching { obj["data"]?.jsonObject?.get("content")?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
            total = jstr(obj["data"]?.jsonObject?.get("total")).toIntOrNull() ?: 0
        } else {
            val body = if (v3) {
                """{"path":${jsonStr(path)},"page":$page,"per_page":$pageSize,"refresh":false}"""
            } else {
                """{"path":${jsonStr(path)},"page_num":$page,"page_size":$pageSize}"""
            }
            val obj = post(if (v3) "/api/fs/list" else "/api/public/path", body)
                ?: return CmsPage(page, 0, 0, emptyList())
            val data = obj["data"]?.jsonObject
            rawList = if (v3) {
                runCatching { data?.get("content")?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
            } else {
                runCatching { data?.get("files")?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonArray }
                    .getOrNull() ?: JsonArray(emptyList())
            }
            total = jstr(data?.get("total")).toIntOrNull() ?: 0
        }
        val parent = if (path.endsWith("/")) path else "$path/"
        val vods = rawList.mapNotNull { el ->
            val o = el.jsonObject
            val name = jstr(o["name"])
            if (name.isEmpty()) return@mapNotNull null
            val folder = isFolder(o)
            if (!showAll && !folder && !isVideo(o, name)) return@mapNotNull null
            val id = parent + name + if (folder) "/" else ""
            CmsVod(
                id = id,
                name = name.replace("$", "_").replace("#", "_"),
                pic = getPic(o),
                remarks = formatSize(jstr(o["size"]).toLongOrNull() ?: 0),
                blurb = ""
            )
        }
        val pageCount = if (total > 0) Math.ceil(total.toDouble() / pageSize).toInt() else 0
        return CmsPage(page, pageCount, total, vods)
    }

    /** 详情：ids 为文件完整路径，取直链 raw_url(v3)/url(v2) */
    suspend fun detail(ids: String): CmsDetail? {
        if (server.isEmpty() || ids.isEmpty()) return null
        val body = """{"path":${jsonStr(ids)}}"""
        val obj = post(if (v3) "/api/fs/get" else "/api/public/path", body)
            ?: return null
        val data = obj["data"]?.jsonObject ?: return null
        val file = if (v3) data
        else runCatching { data["files"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull() ?: return null
        val name = jstr(file["name"]).ifEmpty { ids.substringAfterLast('/') }
        val url = jstr(file[if (v3) "raw_url" else "url"])
        if (url.isEmpty()) return null
        val source = CmsPlaySource(
            name = title.ifEmpty { "Alist" },
            episodes = listOf(CmsEpisode(name, url))
        )
        return CmsDetail(
            id = ids,
            name = name,
            pic = getPic(file),
            remarks = formatSize(jstr(file["size"]).toLongOrNull() ?: 0),
            year = "", lang = "", area = "", score = "0.0", state = "",
            klass = "", actor = "", director = "", content = "", blurb = "",
            typeName = "", sources = listOf(source)
        )
    }

    /** 上传文本内容到网盘指定路径（v3 PUT /api/fs/put，As-Target-Path 指定目标） */
    suspend fun uploadText(targetPath: String, content: String): Boolean {
        val headers = (authHeaders() ?: emptyMap()) + ("As-Target-Path" to targetPath)
        return fetcher.putText("$server/api/fs/put", content, referer, headers)
    }

    /** 下载网盘文本文件内容；失败返回 null */
    suspend fun downloadText(remotePath: String): String? {
        val body = """{"path":${jsonStr(remotePath)}}"""
        val obj = post(if (v3) "/api/fs/get" else "/api/public/path", body) ?: return null
        val data = obj["data"]?.jsonObject ?: return null
        val file = if (v3) data
        else runCatching { data["files"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull() ?: return null
        val url = jstr(file[if (v3) "raw_url" else "url"])
        if (url.isEmpty()) return null
        return fetcher.fetchText(url, referer, authHeaders())
    }

    private fun isFolder(o: JsonObject): Boolean =
        jstr(o["type"]).toIntOrNull()?.let { it == 1 } ?: false

    private fun isVideo(o: JsonObject, name: String): Boolean {
        val t = jstr(o["type"]).toIntOrNull()
        val typeVideo = if (v3) t == 2 else t == 3
        return typeVideo || videoExt.matches(name)
    }

    private fun getPic(o: JsonObject): String {
        val thumb = jstr(o[if (v3) "thumb" else "thumbnail"])
        return thumb
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return ""
        val units = arrayOf("KB", "MB", "GB", "TB")
        var sz = size.toDouble() / 1024
        var i = 0
        while (sz >= 1024 && i < units.size - 1) { sz /= 1024; i++ }
        return "%.2f %s".format(sz, units[i])
    }

    private fun authHeaders(): Map<String, String>? =
        token?.let { mapOf("Authorization" to it) }

    /** 暴露登录 token，供播放直链注入鉴权头；公开盘返回 null */
    fun authToken(): String? = token

    private suspend fun post(path: String, body: String): JsonObject? {
        val text = fetcher.postJson("$server$path", body, referer, authHeaders()) ?: return null
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    private fun parseExt(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val o = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return o.entries.associate { (k, v) -> k to jstr(v) }
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
