package com.hoopoe.zyfuntv.data.repository

import android.content.Context
import android.util.Log
import com.hoopoe.zyfuntv.core.crypto.Crypto
import com.hoopoe.zyfuntv.core.debug.DebugLog
import com.hoopoe.zyfuntv.core.archive.ArchiveImporter
import com.hoopoe.zyfuntv.core.deeplink.DeepLink
import com.hoopoe.zyfuntv.core.deeplink.DeepLinkTarget
import com.hoopoe.zyfuntv.core.model.AnalyzeType
import com.hoopoe.zyfuntv.core.model.IptvType
import com.hoopoe.zyfuntv.core.model.ProxyType
import com.hoopoe.zyfuntv.core.model.RecordType
import com.hoopoe.zyfuntv.core.model.SiteType
import com.hoopoe.zyfuntv.core.model.ThemeMode
import com.hoopoe.zyfuntv.core.model.UiMode
import com.hoopoe.zyfuntv.core.network.HttpFetcher
import com.hoopoe.zyfuntv.core.network.WebSniffer
import com.hoopoe.zyfuntv.data.local.AnalyzeEntity
import com.hoopoe.zyfuntv.data.local.AppDatabase
import com.hoopoe.zyfuntv.data.local.Converters
import com.hoopoe.zyfuntv.data.local.ChannelEntity
import com.hoopoe.zyfuntv.data.local.HistoryEntity
import com.hoopoe.zyfuntv.data.local.IptvEntity
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.local.SearchHistoryEntity
import com.hoopoe.zyfuntv.data.local.StarEntity
import com.hoopoe.zyfuntv.data.parser.AlistAdapter
import com.hoopoe.zyfuntv.data.parser.AppGetAdapter
import com.hoopoe.zyfuntv.data.parser.AppYsV2Adapter
import com.hoopoe.zyfuntv.data.parser.CatvodAdapter
import com.hoopoe.zyfuntv.data.parser.ChannelParser
import com.hoopoe.zyfuntv.data.parser.PlaySourceParser
import com.hoopoe.zyfuntv.data.parser.T0XmlParser
import com.hoopoe.zyfuntv.data.parser.XmltvParser
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.prefs.SettingsRepository
import com.hoopoe.zyfuntv.core.webdav.WebDavClient
import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsEpisode
import com.hoopoe.zyfuntv.data.remote.model.CmsPage
import com.hoopoe.zyfuntv.data.remote.model.CmsPlaySource
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.remote.model.AggregateVod
import com.hoopoe.zyfuntv.data.remote.model.EpgItem
import com.hoopoe.zyfuntv.data.remote.model.PlayResult
import com.hoopoe.zyfuntv.data.remote.model.RankItem
import com.hoopoe.zyfuntv.data.remote.model.RankSource
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/** 备份导入结果统计；settingsApplied 表示偏好是否随备份还原 */
data class BackupSummary(
    val sites: Int,
    val parses: Int,
    val iptv: Int,
    val channels: Int,
    val history: Int,
    val stars: Int,
    val settingsApplied: Boolean
)

/** 聚合仓库：向上层页面暴露 Room Flow，集中配置导入与频道刷新 */
class AppRepository(
    context: Context,
    private val settingsRepo: SettingsRepository? = null
) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val fetcher = HttpFetcher()
    private val importer = ConfigImporter(
        db.siteDao(), db.iptvDao(), db.channelDao(), db.analyzeDao()
    )
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Volatile private var settingsSnapshot: AppSettings? = null

    /** 当前设置快照（供播放器等读取超时/UA） */
    fun currentSettings(): AppSettings = settingsSnapshot ?: AppSettings()

    init {
        settingsRepo?.let { repo ->
            scope.launch {
                repo.settings.collect {
                    settingsSnapshot = it
                    fetcher.configure(it.ua, it.timeoutMs, it.proxyType, it.customProxy, it.ignoreSsl)
                }
            }
        }
        scope.launch { ensureDefaultAnalyze() }
        scope.launch { ensureDefaultIptv() }
        scope.launch { ensureDefaultAlistSite() }
    }

    companion object {
        /** 内置默认解析源固定 ID，便于稳定引用与去重 */
        private const val SEED_ANALYZE_ID = "seed_lziplayer"
        /** 内置默认直播源固定 ID，便于稳定引用与去重 */
        private const val SEED_IPTV_ID = "seed_fmml_index"
        /** 内置测试网盘固定 ID，便于稳定引用与去重 */
        private const val SEED_ALIST_ID = "seed_alist_test"

        /** 热榜数据源清单，分类 1 电影 2 电视剧 3 综艺 4 动漫（对齐上游 HOT_CONFIG） */
        val rankSources: List<RankSource> = listOf(
            RankSource("douban", "豆瓣", listOf(1, 2, 3)),
            RankSource("baidu", "百度", listOf(1, 2)),
            RankSource("enlightent", "云合", listOf(1, 2, 3, 4)),
            RankSource("hisense", "海信", listOf(1, 2, 3, 4)),
            RankSource("huantv", "欢网", listOf(1, 2, 3)),
            RankSource("kylive", "酷云", listOf(2, 3)),
            RankSource("komect", "未来电视", listOf(1, 2, 3, 4)),
            RankSource("quark", "夸克", listOf(1, 2, 3)),
        )
    }

    /** 首次启动（解析源表为空）时写入官方默认解析源并设为默认；不覆盖用户已导入/已添加的源 */
    private suspend fun ensureDefaultAnalyze() = withContext(Dispatchers.IO) {
        if (db.analyzeDao().count() > 0) return@withContext
        val now = System.currentTimeMillis()
        val seed = AnalyzeEntity(
            id = SEED_ANALYZE_ID,
            key = SEED_ANALYZE_ID,
            name = "量子解析",
            api = "https://lziplayer.com/?url=",
            type = AnalyzeType.WEB,
            flag = null,
            headers = null,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        db.analyzeDao().upsert(seed)
        settingsRepo?.setDefaultAnalyze(SEED_ANALYZE_ID)
    }

    /** 首次启动（直播源表为空）时写入默认央视/卫视直播源并设为默认；不覆盖用户已导入/已添加的源 */
    private suspend fun ensureDefaultIptv() = withContext(Dispatchers.IO) {
        if (db.iptvDao().count() > 0) return@withContext
        val now = System.currentTimeMillis()
        val seed = IptvEntity(
            id = SEED_IPTV_ID,
            key = SEED_IPTV_ID,
            name = "央视卫视",
            api = "https://m3u.ibert.me/fmml_index.m3u",
            type = IptvType.REMOTE,
            epg = null,
            logo = null,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        db.iptvDao().upsert(seed)
        settingsRepo?.setDefaultIptv(SEED_IPTV_ID)
        runCatching { refreshChannels(seed) }
    }

    /** 首次启动且尚无 Alist 网盘时，写入一条公开只读测试网盘；不覆盖用户已添加的盘，可在设置中删除或替换为自建 Alist */
    private suspend fun ensureDefaultAlistSite() = withContext(Dispatchers.IO) {
        if (db.siteDao().countByType(SiteType.T3_ALIST) > 0) return@withContext
        val now = System.currentTimeMillis()
        val ext = buildJsonObject {
            put("startPage", "/")
            put("showAll", "false")
        }.toString()
        val seed = SiteEntity(
            id = SEED_ALIST_ID,
            key = SEED_ALIST_ID,
            name = "测试网盘",
            api = "https://alist.azad.asia",
            type = SiteType.T3_ALIST,
            ext = ext,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        db.siteDao().upsert(seed)
    }


    val sites: Flow<List<SiteEntity>> = db.siteDao().observeAll()
    val iptvs: Flow<List<IptvEntity>> = db.iptvDao().observeAll()
    val channels: Flow<List<ChannelEntity>> = db.channelDao().observeAll()
    val analyzes: Flow<List<AnalyzeEntity>> = db.analyzeDao().observeAll()
    val history: Flow<List<HistoryEntity>> = db.historyDao().observeAll()
    val stars: Flow<List<StarEntity>> = db.starDao().observeAll()

    /** 拉取远程 TVBox/Catvod 配置并落库；两种格式自动判别合并；含远程直播源时自动刷新频道 */
    suspend fun importConfigUrl(url: String): ImportSummary? = withContext(Dispatchers.IO) {
        val text = fetcher.fetchText(url) ?: return@withContext null
        val tv = runCatching { importer.importTvbox(text) }.getOrNull()
        val catN = runCatching { importer.importCatvod(text) }.getOrDefault(0)
        if (tv == null && catN == 0) return@withContext null
        val base = tv ?: ImportSummary(0, 0, 0, 0)
        val merged = base.copy(sites = base.sites + catN)
        if (merged.iptv > 0) {
            db.iptvDao().getFirst()?.let { runCatching { refreshChannels(it) } }
        }
        merged
    }

    /** 读取本地配置文本（TVBox/Catvod 双格式判别合并），供本地文件选择器导入 */
    suspend fun importText(text: String): ImportSummary? {
        val tv = runCatching { importer.importTvbox(text) }.getOrNull()
        val catN = runCatching { importer.importCatvod(text) }.getOrDefault(0)
        if (tv == null && catN == 0) return null
        val base = tv ?: ImportSummary(0, 0, 0, 0)
        val merged = base.copy(sites = base.sites + catN)
        if (merged.iptv > 0) {
            db.iptvDao().getFirst()?.let { runCatching { refreshChannels(it) } }
        }
        return merged
    }

    /** 导出当前站点 / 解析 / 直播源为 TVBox 风格 JSON 文本 */
    suspend fun exportConfig(): String {
        val siteList = sites.first()
        val parseList = analyzes.first()
        val iptvList = iptvs.first()
        val root = buildJsonObject {
            putJsonArray("sites") {
                siteList.forEach { s ->
                    add(buildJsonObject {
                        put("key", s.key)
                        put("name", s.name)
                        put("type", s.type)
                        put("api", s.api ?: "")
                        put("playUrl", s.playUrl ?: "")
                        put("search", if (s.search) 1 else 0)
                        s.groupName?.let { put("group", it) }
                        s.ext?.takeIf { it.isNotBlank() }?.let { raw ->
                            runCatching { put("ext", json.parseToJsonElement(raw)) }
                        }
                        s.headers?.takeIf { it.isNotBlank() }?.let { raw ->
                            runCatching { put("headers", json.parseToJsonElement(raw)) }
                        }
                    })
                }
            }
            putJsonArray("parses") {
                parseList.forEach { p ->
                    add(buildJsonObject {
                        put("name", p.name)
                        put("type", p.type)
                        put("url", p.api)
                        p.flag?.let { put("flag", it) }
                        p.headers?.takeIf { it.isNotBlank() }?.let { raw ->
                            runCatching { put("header", json.parseToJsonElement(raw)) }
                        }
                    })
                }
            }
            putJsonArray("lives") {
                iptvList.forEach { i ->
                    add(buildJsonObject {
                        put("name", i.name)
                        put("type", i.type)
                        put("url", i.api)
                        i.epg?.let { put("epg", it) }
                        i.logo?.let { put("logo", it) }
                        i.headers?.takeIf { it.isNotBlank() }?.let { raw ->
                            runCatching { put("headers", json.parseToJsonElement(raw)) }
                        }
                    })
                }
            }
        }
        return root.toString()
    }

    /** 把当前全量备份推送到配置的 WebDAV 目录；成功返回 true。未配置地址返回 false。 */
    suspend fun backupToWebDav(): Boolean = withContext(Dispatchers.IO) {
        val s = currentSettings()
        if (s.davUrl.isBlank()) return@withContext false
        val content = exportBackup()
        val client = WebDavClient(s.davUrl, s.davUser, s.davPass, s.davPath)
        client.uploadText("zyfun-backup.json", content)
    }

    /** 从配置的 WebDAV 目录拉取备份并还原；失败 / 未配置返回 null。 */
    suspend fun restoreFromWebDav(): BackupSummary? = withContext(Dispatchers.IO) {
        val s = currentSettings()
        if (s.davUrl.isBlank()) return@withContext null
        val client = WebDavClient(s.davUrl, s.davUser, s.davPass, s.davPath)
        val text = client.downloadText("zyfun-backup.json") ?: return@withContext null
        runCatching { importBackup(text) }.getOrNull()
    }

    /** 把当前全量备份推送到指定 Alist 网盘（固定目录 /app-backup）；成功返回 true */
    suspend fun backupToAlist(site: SiteEntity, fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (site.type != SiteType.T3_ALIST) return@withContext false
        val content = exportBackup()
        val a = AlistAdapter(fetcher, site.api, site.ext, siteReferer(site))
        if (!a.init()) return@withContext false
        a.uploadText("/app-backup/$fileName", content)
    }

    /** 从指定 Alist 网盘拉取备份并还原；失败返回 null */
    suspend fun restoreFromAlist(site: SiteEntity, fileName: String): BackupSummary? = withContext(Dispatchers.IO) {
        if (site.type != SiteType.T3_ALIST) return@withContext null
        val a = AlistAdapter(fetcher, site.api, site.ext, siteReferer(site))
        if (!a.init()) return@withContext null
        val text = a.downloadText("/app-backup/$fileName") ?: return@withContext null
        runCatching { importBackup(text) }.getOrNull()
    }

    /** 全量备份：偏好 + 站点/解析/直播源 + 历史 + 收藏，输出自有 JSON */
    suspend fun exportBackup(): String {
        val s = settingsSnapshot ?: AppSettings()
        val siteList = sites.first()
        val parseList = analyzes.first()
        val liveList = iptvs.first()
        val histList = history.first()
        val starList = stars.first()
        val root = buildJsonObject {
            put("app", "zyfuntv")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            putJsonObject("settings") {
                put("defaultSite", s.defaultSite)
                put("defaultIptv", s.defaultIptv)
                put("defaultAnalyze", s.defaultAnalyze)
                put("ua", s.ua)
                put("epg", s.epg)
                put("logo", s.logo)
                put("proxyType", s.proxyType)
                put("customProxy", s.customProxy)
                put("timeoutMs", s.timeoutMs)
                put("debug", s.debug)
                put("uiMode", s.uiMode)
                put("themeMode", s.themeMode)
                put("liveIpMark", s.liveIpMark)
                put("liveDelay", s.liveDelay)
                put("liveThumbnail", s.liveThumbnail)
                put("language", s.language)
            }
            putJsonArray("sites") {
                siteList.forEach { it ->
                    add(buildJsonObject {
                        put("id", it.id); put("key", it.key); put("name", it.name)
                        it.api?.let { v -> put("api", v) }
                        it.playUrl?.let { v -> put("playUrl", v) }
                        put("search", it.search)
                        it.groupName?.let { v -> put("group", v) }
                        put("type", it.type)
                        it.ext?.let { v -> put("ext", v) }
                        it.categories?.let { v -> put("categories", v) }
                        it.headers?.let { v -> put("headers", v) }
                        put("isActive", it.isActive)
                        put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                    })
                }
            }
            putJsonArray("parses") {
                parseList.forEach { it ->
                    add(buildJsonObject {
                        put("id", it.id); put("key", it.key); put("name", it.name)
                        put("api", it.api); put("type", it.type)
                        it.flag?.let { v -> put("flag", v) }
                        it.headers?.let { v -> put("headers", v) }
                        it.script?.let { v -> put("script", v) }
                        put("isActive", it.isActive)
                        put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                    })
                }
            }
            putJsonArray("lives") {
                liveList.forEach { it ->
                    add(buildJsonObject {
                        put("id", it.id); put("key", it.key); put("name", it.name)
                        put("api", it.api); put("type", it.type)
                        it.epg?.let { v -> put("epg", v) }
                        it.logo?.let { v -> put("logo", v) }
                        it.headers?.let { v -> put("headers", v) }
                        put("isActive", it.isActive)
                        put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                    })
                }
            }
            putJsonArray("history") {
                histList.forEach { it ->
                    add(buildJsonObject {
                        put("id", it.id); put("type", it.type)
                        it.relateId?.let { v -> put("relateId", v) }
                        it.siteSource?.let { v -> put("siteSource", v) }
                        put("playEnd", it.playEnd)
                        it.videoId?.let { v -> put("videoId", v) }
                        it.videoImage?.let { v -> put("videoImage", v) }
                        put("videoName", it.videoName)
                        it.videoIndex?.let { v -> put("videoIndex", v) }
                        put("watchTime", it.watchTime); put("duration", it.duration)
                        put("skipTimeInEnd", it.skipTimeInEnd); put("skipTimeInStart", it.skipTimeInStart)
                        put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                    })
                }
            }
            putJsonArray("stars") {
                starList.forEach { it ->
                    add(buildJsonObject {
                        put("id", it.id); put("type", it.type)
                        it.relateId?.let { v -> put("relateId", v) }
                        it.videoId?.let { v -> put("videoId", v) }
                        it.videoImage?.let { v -> put("videoImage", v) }
                        put("videoName", it.videoName)
                        it.videoType?.let { v -> put("videoType", v) }
                        it.videoRemarks?.let { v -> put("videoRemarks", v) }
                        put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                    })
                }
            }
        }
        return root.toString()
    }

    /**
     * 导入备份：自有全量备份（settings/history/stars/sites/parses/lives）直接还原；
     * 非自有格式回退到 TVBox/Catvod 配置解析。
     */
    suspend fun importBackup(text: String): BackupSummary {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val isBackup = root != null && (
            jstr(root["app"]) == "zyfuntv" ||
                root.containsKey("settings") || root.containsKey("history") || root.containsKey("stars")
            )
        if (!isBackup) {
            val sum = importText(text)
            return BackupSummary(
                sites = sum?.sites ?: 0, parses = sum?.parses ?: 0,
                iptv = sum?.iptv ?: 0, channels = sum?.channels ?: 0,
                history = 0, stars = 0, settingsApplied = false
            )
        }

        var siteN = 0; var parseN = 0; var liveN = 0
        var histN = 0; var starN = 0; var settingsApplied = false

        root["settings"]?.jsonObject?.let { o ->
            val s = AppSettings(
                defaultSite = jstr(o["defaultSite"]),
                defaultIptv = jstr(o["defaultIptv"]),
                defaultAnalyze = jstr(o["defaultAnalyze"]),
                ua = jstr(o["ua"]),
                epg = jstr(o["epg"]).ifBlank { AppSettings().epg },
                logo = jstr(o["logo"]).ifBlank { AppSettings().logo },
                proxyType = jstr(o["proxyType"]).ifBlank { ProxyType.DIRECT },
                customProxy = jstr(o["customProxy"]),
                timeoutMs = jlong(o["timeoutMs"], 15000L),
                debug = jbool(o["debug"]),
                uiMode = jstr(o["uiMode"]).ifBlank { UiMode.PHONE },
                themeMode = jstr(o["themeMode"]).ifBlank { ThemeMode.SYSTEM },
                liveIpMark = jbool(o["liveIpMark"], true),
                liveDelay = jbool(o["liveDelay"]),
                liveThumbnail = jbool(o["liveThumbnail"]),
                language = jstr(o["language"]).ifBlank { "zh" }
            )
            settingsRepo?.applySettings(s)
            settingsApplied = true
        }

        root["sites"]?.jsonArray?.forEach { el ->
            val o = el.jsonObject
            db.siteDao().upsert(
                SiteEntity(
                    id = jstr(o["id"]).ifBlank { UUID.randomUUID().toString() },
                    key = jstr(o["key"]).ifBlank { UUID.randomUUID().toString() },
                    name = jstr(o["name"]).ifBlank { "未命名站点" },
                    api = jstr(o["api"]).ifBlank { null },
                    playUrl = jstr(o["playUrl"]).ifBlank { null },
                    search = jbool(o["search"], true),
                    groupName = jstr(o["group"]).ifBlank { null },
                    type = jint(o["type"]),
                    ext = jstr(o["ext"]).ifBlank { null },
                    categories = jstr(o["categories"]).ifBlank { null },
                    headers = jstr(o["headers"]).ifBlank { null },
                    isActive = jbool(o["isActive"], true),
                    createdAt = jlong(o["createdAt"]),
                    updatedAt = jlong(o["updatedAt"])
                )
            )
            siteN++
        }

        root["parses"]?.jsonArray?.forEach { el ->
            val o = el.jsonObject
            db.analyzeDao().upsert(
                AnalyzeEntity(
                    id = jstr(o["id"]).ifBlank { UUID.randomUUID().toString() },
                    key = jstr(o["key"]).ifBlank { UUID.randomUUID().toString() },
                    name = jstr(o["name"]).ifBlank { "未命名解析" },
                    api = jstr(o["api"]),
                    type = jint(o["type"], AnalyzeType.JSON),
                    flag = jstr(o["flag"]).ifBlank { null },
                    headers = jstr(o["headers"]).ifBlank { null },
                    script = jstr(o["script"]).ifBlank { null },
                    isActive = jbool(o["isActive"], true),
                    createdAt = jlong(o["createdAt"]),
                    updatedAt = jlong(o["updatedAt"])
                )
            )
            parseN++
        }

        root["lives"]?.jsonArray?.forEach { el ->
            val o = el.jsonObject
            db.iptvDao().upsert(
                IptvEntity(
                    id = jstr(o["id"]).ifBlank { UUID.randomUUID().toString() },
                    key = jstr(o["key"]).ifBlank { UUID.randomUUID().toString() },
                    name = jstr(o["name"]).ifBlank { "未命名直播源" },
                    api = jstr(o["api"]),
                    type = jint(o["type"], IptvType.REMOTE),
                    epg = jstr(o["epg"]).ifBlank { null },
                    logo = jstr(o["logo"]).ifBlank { null },
                    headers = jstr(o["headers"]).ifBlank { null },
                    isActive = jbool(o["isActive"], true),
                    createdAt = jlong(o["createdAt"]),
                    updatedAt = jlong(o["updatedAt"])
                )
            )
            liveN++
        }

        root["history"]?.jsonArray?.forEach { el ->
            val o = el.jsonObject
            db.historyDao().upsert(
                HistoryEntity(
                    id = jstr(o["id"]).ifBlank { UUID.randomUUID().toString() },
                    type = jint(o["type"], RecordType.FILM),
                    relateId = jstr(o["relateId"]).ifBlank { null },
                    siteSource = jstr(o["siteSource"]).ifBlank { null },
                    playEnd = jbool(o["playEnd"]),
                    videoId = jstr(o["videoId"]).ifBlank { null },
                    videoImage = jstr(o["videoImage"]).ifBlank { null },
                    videoName = jstr(o["videoName"]),
                    videoIndex = jstr(o["videoIndex"]).ifBlank { null },
                    watchTime = jdouble(o["watchTime"]),
                    duration = jdouble(o["duration"]),
                    skipTimeInEnd = jdouble(o["skipTimeInEnd"]),
                    skipTimeInStart = jdouble(o["skipTimeInStart"]),
                    createdAt = jlong(o["createdAt"]),
                    updatedAt = jlong(o["updatedAt"])
                )
            )
            histN++
        }

        root["stars"]?.jsonArray?.forEach { el ->
            val o = el.jsonObject
            db.starDao().upsert(
                StarEntity(
                    id = jstr(o["id"]).ifBlank { UUID.randomUUID().toString() },
                    type = jint(o["type"], RecordType.FILM),
                    relateId = jstr(o["relateId"]).ifBlank { null },
                    videoId = jstr(o["videoId"]).ifBlank { null },
                    videoImage = jstr(o["videoImage"]).ifBlank { null },
                    videoName = jstr(o["videoName"]),
                    videoType = jstr(o["videoType"]).ifBlank { null },
                    videoRemarks = jstr(o["videoRemarks"]).ifBlank { null },
                    createdAt = jlong(o["createdAt"]),
                    updatedAt = jlong(o["updatedAt"])
                )
            )
            starN++
        }

        return BackupSummary(siteN, parseN, liveN, 0, histN, starN, settingsApplied)
    }

    /** 按直播源地址拉取 M3U/TXT 并刷新频道表（先清后写）；手动型直接解析粘贴文本 */
    suspend fun refreshChannels(iptv: IptvEntity) = withContext(Dispatchers.IO) {
        val text = if (iptv.type == IptvType.MANUAL) {
            iptv.api
        } else {
            fetcher.fetchText(iptv.api) ?: return@withContext
        }
        val raw = if (text.contains("#EXTM3U", ignoreCase = true)) {
            ChannelParser.parseM3U(text)
        } else {
            ChannelParser.parseTxt(text)
        }
        db.channelDao().clear()
        raw.forEach { ch ->
            db.channelDao().upsert(
                ChannelEntity(
                    id = UUID.randomUUID().toString(),
                    name = ch.name,
                    api = ch.url,
                    logo = ch.logo,
                    groupName = ch.group,
                    headers = if (ch.headers.isEmpty()) null else ch.headers.toString()
                )
            )
        }
    }

    // ---- 解析源管理 ----
    suspend fun addAnalyze(name: String, api: String, type: Int, flag: String? = null) {
        db.analyzeDao().upsert(
            AnalyzeEntity(
                id = UUID.randomUUID().toString(),
                key = UUID.randomUUID().toString(),
                name = name.ifEmpty { "未命名解析" },
                api = api.trim(),
                type = type,
                flag = flag,
                isActive = true
            )
        )
    }

    suspend fun deleteAnalyze(item: AnalyzeEntity) {
        db.analyzeDao().delete(item)
        if (settingsSnapshot?.defaultAnalyze == item.id) settingsRepo?.setDefaultAnalyze("")
    }

    /** 编辑现有解析源：保留 id/key/headers/script，仅更新名称/接口/类型/适配线路 */
    suspend fun updateAnalyze(item: AnalyzeEntity, name: String, api: String, type: Int, flag: String? = null) {
        db.analyzeDao().upsert(item.copy(
            name = name.ifEmpty { "未命名解析" },
            api = api.trim(),
            type = type,
            flag = flag,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun setDefaultAnalyze(id: String) = settingsRepo?.setDefaultAnalyze(id)
    suspend fun setAnalyzeActive(id: String, active: Boolean) = db.analyzeDao().setActive(id, active)

    suspend fun setDefaultSite(id: String) = settingsRepo?.setDefaultSite(id)
    suspend fun setDefaultIptv(id: String) = settingsRepo?.setDefaultIptv(id)

    // ---- 影视站点管理 ----
    suspend fun addSite(name: String, api: String, type: Int, groupName: String? = null, search: Boolean = true, playUrl: String? = null) {
        db.siteDao().upsert(
            SiteEntity(
                id = UUID.randomUUID().toString(),
                key = UUID.randomUUID().toString(),
                name = name.ifBlank { "未命名站点" },
                api = api.trim().ifBlank { null },
                playUrl = playUrl?.trim()?.ifBlank { null },
                search = search,
                groupName = groupName?.trim()?.ifBlank { null },
                type = type,
                isActive = true
            )
        )
    }

    suspend fun deleteSite(item: SiteEntity) {
        db.siteDao().delete(item)
        if (settingsSnapshot?.defaultSite == item.id) settingsRepo?.setDefaultSite("")
    }

    /** 编辑现有影视站点：保留 id/key/ext/categories/headers，仅更新基础字段 */
    suspend fun updateSite(
        item: SiteEntity,
        name: String,
        api: String,
        type: Int,
        groupName: String? = null,
        search: Boolean = true,
        playUrl: String? = null
    ) {
        db.siteDao().upsert(item.copy(
            name = name.ifBlank { "未命名站点" },
            api = api.trim().ifBlank { null },
            playUrl = playUrl?.trim()?.ifBlank { null },
            search = search,
            groupName = groupName?.trim()?.ifBlank { null },
            type = type,
            updatedAt = System.currentTimeMillis()
        ))
    }
    suspend fun setSiteActive(id: String, active: Boolean) = db.siteDao().setActive(id, active)

    /** 新增或编辑 Alist 网盘站点；配置（起始路径/显示范围/账号密码）写入 ext JSON */
    suspend fun upsertAlistSite(
        id: String?,
        name: String,
        server: String,
        startPage: String,
        showAll: Boolean,
        username: String,
        password: String
    ) {
        val ext = buildJsonObject {
            put("startPage", startPage.ifBlank { "/" })
            put("showAll", showAll.toString())
            if (username.isNotBlank()) put("username", username.trim())
            if (password.isNotBlank()) put("password", password)
        }.toString()
        val existing = id?.let { db.siteDao().getById(it) }
        val entity = (existing ?: SiteEntity(
            id = UUID.randomUUID().toString(),
            key = UUID.randomUUID().toString(),
            name = name.ifBlank { "未命名网盘" }
        )).copy(
            name = name.ifBlank { "未命名网盘" },
            api = server.trim().ifBlank { null },
            type = SiteType.T3_ALIST,
            ext = ext,
            isActive = true,
            updatedAt = System.currentTimeMillis()
        )
        db.siteDao().upsert(entity)
    }

    // ---- 直播源管理 ----
    suspend fun addIptv(name: String, api: String, type: Int, epg: String? = null, logo: String? = null) {
        val entity = IptvEntity(
            id = UUID.randomUUID().toString(),
            key = UUID.randomUUID().toString(),
            name = name.ifBlank { "未命名直播源" },
            api = api.trim(),
            type = type,
            epg = epg?.trim()?.ifBlank { null },
            logo = logo?.trim()?.ifBlank { null },
            isActive = true
        )
        db.iptvDao().upsert(entity)
        runCatching { refreshChannels(entity) }
    }

    /** 编辑现有直播源：保留 id/key/headers，更新后重新拉取频道表 */
    suspend fun updateIptv(item: IptvEntity, name: String, api: String, type: Int, epg: String? = null, logo: String? = null) {
        val entity = item.copy(
            name = name.ifBlank { "未命名直播源" },
            api = api.trim(),
            type = type,
            epg = epg?.trim()?.ifBlank { null },
            logo = logo?.trim()?.ifBlank { null },
            updatedAt = System.currentTimeMillis()
        )
        db.iptvDao().upsert(entity)
        runCatching { refreshChannels(entity) }
    }

    suspend fun deleteIptv(item: IptvEntity) {
        db.iptvDao().delete(item)
        if (settingsSnapshot?.defaultIptv == item.id) settingsRepo?.setDefaultIptv("")
    }
    suspend fun setIptvActive(id: String, active: Boolean) = db.iptvDao().setActive(id, active)

    // ---- 历史 / 收藏清理 ----
    suspend fun clearHistory() = db.historyDao().clear()
    suspend fun clearStars() = db.starDao().clear()
    suspend fun deleteHistory(item: HistoryEntity) = db.historyDao().delete(item)
    suspend fun deleteStar(item: StarEntity) = db.starDao().delete(item)

    // ---- 源分类清空 / 恢复出厂 ----
    suspend fun clearSites() = db.siteDao().clear()
    suspend fun clearIptvs() { db.iptvDao().clear(); db.channelDao().clear() }
    suspend fun clearAnalyzes() = db.analyzeDao().clear()

    /** 恢复出厂：清空全部源 / 频道 / 历史 / 收藏，偏好回默认 */
    suspend fun resetAll() {
        db.siteDao().clear()
        db.iptvDao().clear()
        db.channelDao().clear()
        db.analyzeDao().clear()
        db.historyDao().clear()
        db.starDao().clear()
        settingsRepo?.reset()
    }

    // ---- 设置项透传到 DataStore ----
    suspend fun setDavConfig(url: String, user: String, pass: String, path: String) =
        settingsRepo?.setDavConfig(url, user, pass, path)
    suspend fun setUa(ua: String) = settingsRepo?.setUa(ua)
    suspend fun setTimeoutMs(ms: Long) = settingsRepo?.setTimeoutMs(ms)
    suspend fun setProxyType(type: String) = settingsRepo?.setProxyType(type)
    suspend fun setCustomProxy(host: String) = settingsRepo?.setCustomProxy(host)
    suspend fun setEpg(url: String) = settingsRepo?.setEpg(url)
    suspend fun setLogo(url: String) = settingsRepo?.setLogo(url)
    suspend fun setDebug(debug: Boolean) = settingsRepo?.setDebug(debug)
    suspend fun setLiveIpMark(v: Boolean) = settingsRepo?.setLiveIpMark(v)
    suspend fun setLiveDelay(v: Boolean) = settingsRepo?.setLiveDelay(v)
    suspend fun setLiveThumbnail(v: Boolean) = settingsRepo?.setLiveThumbnail(v)
    suspend fun setDnsMode(mode: String) = settingsRepo?.setDnsMode(mode)
    suspend fun setDnsServer(server: String) = settingsRepo?.setDnsServer(server)
    suspend fun setDanmuEnabled(v: Boolean) = settingsRepo?.setDanmuEnabled(v)
    suspend fun setAutoPlayNext(v: Boolean) = settingsRepo?.setAutoPlayNext(v)
    suspend fun setUseExternalPlayer(v: Boolean) = settingsRepo?.setUseExternalPlayer(v)
    suspend fun setExternalPlayerPkg(pkg: String) = settingsRepo?.setExternalPlayerPkg(pkg)
    suspend fun setKeepBackground(v: Boolean) = settingsRepo?.setKeepBackground(v)
    suspend fun setIgnoreSsl(v: Boolean) = settingsRepo?.setIgnoreSsl(v)

    /** 拉取站点分类；T0 XML / CatVod POST / Drpy GET+extend / 其余 MacCMS 同构 JSON */
    suspend fun fetchCategories(site: SiteEntity): List<CmsCategory> = withContext(Dispatchers.IO) {
        DebugLog.d("ZyCat", "分类请求 站点=${site.name} type=${site.type} api=${site.api}")
        if (isRuleAdapterPending(site.type)) {
            Log.w("ZyCat", "rule adapter pending type=${site.type} api=${site.api}")
            DebugLog.w("ZyCat", "规则适配器暂未支持 type=${site.type} 站点=${site.name}")
            return@withContext emptyList()
        }
        if (site.type == SiteType.T3_ALIST) {
            val a = AlistAdapter(fetcher, site.api, site.ext, siteReferer(site))
            if (!a.init()) return@withContext emptyList()
            return@withContext a.categories()
        }
        if (site.type == SiteType.T3_APPYSV2) {
            return@withContext AppYsV2Adapter(fetcher, site.api, site.ext, siteReferer(site)).categories()
        }
        if (isAppGet(site.type)) {
            val a = AppGetAdapter(fetcher, site.api, site.ext, site.type, siteReferer(site))
            a.init()
            return@withContext a.categories()
        }
        val api = site.api ?: return@withContext emptyList()
        if (site.type == SiteType.T4_CATVOD) {
            return@withContext CatvodAdapter(fetcher, api, siteReferer(site)).categories()
        }
        val url = if (isDrpy(site.type)) {
            urlWith(api, "extend" to (site.ext ?: ""), "filter" to "true")
        } else {
            urlWith(api, "ac" to "class")
        }
        val fetched = fetcher.fetchText(url, siteReferer(site))
        if (fetched == null) {
            Log.w("ZyCat", "fetchCategories text=null url=$url")
            DebugLog.w("ZyCat", "分类请求返回空（网络失败/非2xx/被拦截）url=$url")
            return@withContext emptyList()
        }
        Log.d("ZyCat", "fetchCategories url=$url len=${fetched.length}")
        val cats = if (site.type == SiteType.T0_XML) {
            T0XmlParser.parseCategories(fetched)
        } else {
            parseJsonCategories(fetched)
        }
        Log.d("ZyCat", "categories=${cats.size} type=${site.type}")
        DebugLog.d("ZyCat", "分类解析完成 站点=${site.name} 条数=${cats.size} 响应长度=${fetched.length}")
        cats
    }

    /** 分类影片分页列表；本期仅实现 T1 JSON */
    suspend fun fetchVodList(site: SiteEntity, tid: String, page: Int): CmsPage =
        fetchList(site, tid, "", page)

    /** 关键词搜索；T1 JSON 走 ac=videolist&wd= */
    suspend fun fetchSearch(site: SiteEntity, wd: String, page: Int): CmsPage =
        fetchList(site, "", wd.trim(), page)

    private suspend fun fetchList(site: SiteEntity, tid: String, wd: String, page: Int): CmsPage = withContext(Dispatchers.IO) {
        if (isRuleAdapterPending(site.type)) {
            Log.w("ZyCat", "rule adapter pending list type=${site.type} api=${site.api}")
            return@withContext CmsPage(page, 0, 0, emptyList())
        }
        if (site.type == SiteType.T3_ALIST) {
            val a = AlistAdapter(fetcher, site.api, site.ext, siteReferer(site))
            if (!a.init()) return@withContext CmsPage(page, 0, 0, emptyList())
            return@withContext a.list(tid, wd, page)
        }
        if (site.type == SiteType.T3_APPYSV2) {
            return@withContext AppYsV2Adapter(fetcher, site.api, site.ext, siteReferer(site)).list(tid, wd, page)
        }
        if (isAppGet(site.type)) {
            val a = AppGetAdapter(fetcher, site.api, site.ext, site.type, siteReferer(site))
            a.init()
            return@withContext a.list(tid, wd, page)
        }
        val api = site.api ?: return@withContext CmsPage(page, 0, 0, emptyList())
        if (site.type == SiteType.T4_CATVOD) {
            return@withContext CatvodAdapter(fetcher, api, siteReferer(site)).list(tid, wd, page)
        }
        val params = mutableListOf("pg" to page.toString())
        if (tid.isNotEmpty()) params.add("t" to tid)
        if (wd.isNotEmpty()) params.add("wd" to wd)
        if (isDrpy(site.type)) params.add("extend" to (site.ext ?: ""))
        val url = if (site.type == SiteType.T0_XML) {
            val ac = if (wd.isNotEmpty()) "list" else "videolist"
            urlWith(api, "ac" to ac, *params.toTypedArray())
        } else {
            params.add(0, "ac" to "videolist")
            urlWith(api, *params.toTypedArray())
        }
        val text = fetcher.fetchText(url, siteReferer(site))
        if (text == null) {
            DebugLog.w("ZyCat", "列表请求返回空 站点=${site.name} tid=$tid wd=$wd url=$url")
            return@withContext CmsPage(page, 0, 0, emptyList())
        }
        val result = if (site.type == SiteType.T0_XML) T0XmlParser.parseList(text, page)
        else parseJsonList(text, page)
        DebugLog.d(
            "ZyCat",
            "列表解析完成 站点=${site.name} tid=$tid wd=$wd 条数=${result.list.size} 总=${result.total} 响应长度=${text.length}"
        )
        result
    }

    /** 拉取影片详情；CatVod POST /detail，T0 XML，Drpy GET+extend，其余同构 JSON */
    suspend fun fetchDetail(site: SiteEntity, vodId: String): CmsDetail? = withContext(Dispatchers.IO) {
        if (isRuleAdapterPending(site.type)) {
            Log.w("ZyCat", "rule adapter pending detail type=${site.type} api=${site.api}")
            return@withContext null
        }
        if (site.type == SiteType.T3_ALIST) {
            val a = AlistAdapter(fetcher, site.api, site.ext, siteReferer(site))
            if (!a.init()) return@withContext null
            return@withContext a.detail(vodId)
        }
        if (site.type == SiteType.T3_APPYSV2) {
            return@withContext AppYsV2Adapter(fetcher, site.api, site.ext, siteReferer(site)).detail(vodId)
        }
        if (isAppGet(site.type)) {
            val a = AppGetAdapter(fetcher, site.api, site.ext, site.type, siteReferer(site))
            a.init()
            return@withContext a.detail(vodId)
        }
        val api = site.api ?: return@withContext null
        if (site.type == SiteType.T4_CATVOD) {
            return@withContext CatvodAdapter(fetcher, api, siteReferer(site)).detail(vodId)
        }
        val params = mutableListOf("ids" to vodId)
        if (isDrpy(site.type)) params.add("extend" to (site.ext ?: ""))
        val url = urlWith(api, "ac" to "detail", *params.toTypedArray())
        val text = fetcher.fetchText(url, siteReferer(site))
        if (text == null) {
            DebugLog.w("ZyCat", "详情请求返回空 vodId=$vodId url=$url")
            return@withContext null
        }
        val detail = if (site.type == SiteType.T0_XML) T0XmlParser.parseDetail(text, vodId)
        else parseJsonDetail(text, vodId)
        DebugLog.d("ZyCat", "详情解析完成 站点=${site.name} vodId=$vodId 线路=${detail?.sources?.size ?: 0} 名称=${detail?.name ?: "解析为空"}")
        detail
    }

    /** MacCMS 同构 JSON：分类 */
    /** 部分 CMS 把 class/list 包在 result/data 子对象内；顶层取不到时自动下钻一层 */
    private fun unwrapCms(root: JsonObject): JsonObject {
        listOf("result", "data").forEach { k ->
            val child = root[k]?.jsonObject ?: return@forEach
            if (child["class"] != null || child["list"] != null) {
                DebugLog.d("ZyCat", "响应被包在 `$k` 键内，已下钻解析")
                return child
            }
        }
        return root
    }

    private fun parseJsonCategories(text: String): List<CmsCategory> = runCatching {
        val obj = unwrapCms(json.parseToJsonElement(text).jsonObject)
        val classArr = obj["class"]?.jsonArray
        if (classArr != null && classArr.isNotEmpty()) {
            Log.d("ZyCat", "parse class-array n=${classArr.size}")
            return@runCatching classArr.map { el ->
                val o = el.jsonObject
                CmsCategory(
                    typeId = jstr(o["type_id"]).trim(),
                    typeName = jstr(o["type_name"]).trim()
                )
            }.filter { it.typeId.isNotEmpty() && it.typeName.isNotEmpty() }
        }
        // 非标准源：ac=class 直接回影片 list，从条目 type_id/type_name 去重提取分类
        val listArr = obj["list"]?.jsonArray
        Log.d("ZyCat", "parse fallback list=${listArr?.size ?: -1}")
        if (listArr == null) return@runCatching emptyList()
        val map = LinkedHashMap<String, String>()
        listArr.forEach { el ->
            val o = el.jsonObject
            val tid = jstr(o["type_id"]).trim()
            val tname = jstr(o["type_name"]).trim()
            if (tid.isNotEmpty() && tname.isNotEmpty()) map.putIfAbsent(tid, tname)
        }
        Log.d("ZyCat", "fallback cats=${map.size}")
        map.map { CmsCategory(typeId = it.key, typeName = it.value) }
    }.onFailure { Log.w("ZyCat", "parseJsonCategories FAIL ${it.javaClass.simpleName}: ${it.message}") }
        .getOrDefault(emptyList())

    /** MacCMS 同构 JSON：列表 */
    private fun parseJsonList(text: String, page: Int): CmsPage = runCatching {
        val obj = unwrapCms(json.parseToJsonElement(text).jsonObject)
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
        CmsPage(
            page = jstr(obj["page"]).toIntOrNull() ?: page,
            pageCount = jstr(obj["pagecount"]).toIntOrNull() ?: 0,
            total = jstr(obj["total"]).toIntOrNull() ?: 0,
            list = list
        )
    }.getOrDefault(CmsPage(page, 0, 0, emptyList()))

    /** MacCMS 同构 JSON：详情 */
    private fun parseJsonDetail(text: String, fallbackId: String): CmsDetail? = runCatching {
        val obj = unwrapCms(json.parseToJsonElement(text).jsonObject)
        val arr = obj["list"]?.jsonArray
        if (arr == null) {
            Log.w("ZyCat", "parseJsonDetail 无 list 键；顶层键=${obj.keys.joinToString(",")}")
            DebugLog.w("ZyCat", "详情解析：响应无 list 键，顶层键=${obj.keys.joinToString(",")}")
            return@runCatching null
        }
        val o = arr.firstOrNull()?.jsonObject
        if (o == null) {
            Log.w("ZyCat", "parseJsonDetail list 为空或首项非对象 n=${arr.size}")
            DebugLog.w("ZyCat", "详情解析：list 为空或首项非对象 n=${arr.size}")
            return@runCatching null
        }
        CmsDetail(
            id = jstr(o["vod_id"]).ifEmpty { fallbackId },
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
            sources = parsePlaySources(jstr(o["vod_play_from"]), jstr(o["vod_play_url"]))
        )
    }.onFailure {
        val cause = it.cause
        val trace = it.stackTrace.take(10).joinToString("\n") { "    at $it" }
        Log.w(
            "ZyCat",
            "parseJsonDetail FAIL ${it.javaClass.simpleName}: ${it.message} | " +
                "cause=${cause?.javaClass?.simpleName}: ${cause?.message}\n$trace"
        )
        DebugLog.w(
            "ZyCat",
            "详情解析异常 ${it.javaClass.simpleName}: ${it.message} cause=${cause?.javaClass?.simpleName}: ${cause?.message}"
        )
    }.getOrNull()

    /** 解析 play_from / play_url 为线路与剧集（统一委托 PlaySourceParser） */
    private fun parsePlaySources(from: String, playUrl: String): List<CmsPlaySource> =
        PlaySourceParser.parse(from, playUrl)

    private val directExt = Regex("""\.(m3u8|mp4|mpd|flv|mkv)""", RegexOption.IGNORE_CASE)

    /** 播放地址解析：编码兜底解码 → 直链直放 → 站点 json: 前缀 → analyze 表（JSON / 正则嗅探兜底） */
    suspend fun resolvePlay(site: SiteEntity, raw: String, sourceName: String? = null): PlayResult = withContext(Dispatchers.IO) {
        if (raw.isEmpty()) return@withContext PlayResult("", true, "空地址")
        val decoded = decodeIfEncoded(raw)
        val siteDec = trySiteDecrypt(site, decoded)
        val siteH = siteHeaders(site)
        if (directExt.containsMatchIn(siteDec)) return@withContext PlayResult(siteDec, false, null, siteH)
        if (site.type == SiteType.T3_ALIST && siteDec.startsWith("http")) {
            val a = AlistAdapter(fetcher, site.api, site.ext, siteReferer(site))
            a.init()
            val alistHeaders = siteH + a.authToken()?.let { mapOf("Authorization" to it) }.orEmpty()
            return@withContext PlayResult(siteDec, false, null, alistHeaders)
        }

        if (site.type == SiteType.T4_CATVOD) {
            val adapter = CatvodAdapter(fetcher, site.api ?: "", siteReferer(site))
            val r = adapter.play(sourceName ?: "", siteDec)
            if (r.url.isNotEmpty()) return@withContext r.copy(headers = siteH + r.headers)
        }

        if (isAppGet(site.type)) {
            val adapter = AppGetAdapter(fetcher, site.api, site.ext, site.type, siteReferer(site))
            val r = adapter.play(siteDec)
            if (r.url.isNotEmpty()) return@withContext r.copy(headers = siteH + r.headers)
        }

        val prefix = site.playUrl ?: ""
        var target = siteDec
        if (prefix.startsWith("json:")) {
            val r = parseJsonAnalyze(prefix.removePrefix("json:") + siteDec, siteH)
            if (r.url.isNotEmpty()) return@withContext r
        } else if (prefix.isNotEmpty()) {
            target = prefix + siteDec
        }

        val analyze = pickAnalyze(sourceName)
        if (analyze != null) {
            val headers = siteH + parseHeaderString(analyze.headers)
            val r = when (analyze.type) {
                AnalyzeType.JSON -> parseJsonAnalyze(analyze.api + target, headers)
                else -> parseWebSniff(analyze.api + target, headers)
            }
            if (r.url.isNotEmpty()) return@withContext r
        }
        PlayResult(target, true, "解析失败，需网页嗅探", siteH)
    }

    /** 解析页直连：用指定解析源解析目标链接并返回可播地址，不经过站点线路 */
    suspend fun resolveWithAnalyze(analyze: AnalyzeEntity, target: String): PlayResult = withContext(Dispatchers.IO) {
        if (target.isBlank()) {
            DebugLog.w("ZyParse", "解析目标为空 源=${analyze.name}")
            return@withContext PlayResult("", true, "空地址")
        }
        DebugLog.d("ZyParse", "开始解析 源=${analyze.name} type=${analyze.type} 目标=${target.take(120)}")
        val headers = parseHeaderString(analyze.headers)
        val r = when (analyze.type) {
            AnalyzeType.JSON -> parseJsonAnalyze(analyze.api + target, headers)
            else -> parseWebSniff(analyze.api + target, headers)
        }
        val out = if (r.url.isNotEmpty()) r
        else PlayResult(analyze.api + target, true, r.message ?: "解析失败，需网页嗅探", headers)
        DebugLog.d(
            "ZyParse",
            "解析结果 源=${analyze.name} 直链=${if (out.url.isNotEmpty()) out.url.take(120) else "无"} needParse=${out.needParse} msg=${out.message ?: "-"}"
        )
        out
    }

    /** 编码兜底：非 http 串尝试 base64 / hex 还原为直链；失败原样返回 */
    private fun decodeIfEncoded(raw: String): String {
        if (raw.isBlank()) return raw
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return raw
        runCatching {
            val b64 = Crypto.b64DecodeText(raw)
            if (b64.startsWith("http://", true) || b64.startsWith("https://", true)) return b64
        }
        runCatching {
            val hex = String(Crypto.hexDecode(raw), Charsets.UTF_8)
            if (hex.startsWith("http://", true) || hex.startsWith("https://", true)) return hex
        }
        return raw
    }

    /** 站点级 AES 解密钩子：ext 内带 aesKey/key 与 aesIv/iv 时，对非 http 串尝试 CBC（有 iv）/ECB 解密；还原失败原样返回 */
    private fun trySiteDecrypt(site: SiteEntity, raw: String): String {
        if (raw.isBlank() || raw.startsWith("http://", true) || raw.startsWith("https://", true)) return raw
        val ext = runCatching { json.parseToJsonElement(site.ext ?: "").jsonObject }.getOrNull() ?: return raw
        val key = jstr(ext["aesKey"]).ifEmpty { jstr(ext["key"]) }
        if (key.isEmpty()) return raw
        val iv = jstr(ext["aesIv"]).ifEmpty { jstr(ext["iv"]) }
        val out = runCatching {
            if (iv.isNotEmpty()) Crypto.aesCbcDecrypt(raw, key, iv)
            else Crypto.aesEcbDecrypt(raw, key)
        }.getOrNull()
        return out?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) } ?: raw
    }

    /** 选解析源：默认源优先 → flag 命中当前线路名 → 无 flag 兜底 → 首条启用 */
    private suspend fun pickAnalyze(sourceName: String?): AnalyzeEntity? {
        val sid = settingsSnapshot?.defaultAnalyze ?: ""
        if (sid.isNotEmpty()) {
            db.analyzeDao().getById(sid)?.takeIf { it.isActive }?.let { return it }
        }
        val active = db.analyzeDao().getActiveAll()
        if (active.isEmpty()) return null
        active.firstOrNull { flagMatch(it.flag, sourceName) }?.let { return it }
        active.firstOrNull { it.flag.isNullOrBlank() }?.let { return it }
        return active.firstOrNull()
    }

    private fun flagMatch(flag: String?, sourceName: String?): Boolean {
        if (flag.isNullOrBlank() || sourceName.isNullOrBlank()) return false
        val f = flag.trim().lowercase()
        val s = sourceName.trim().lowercase()
        return s.contains(f) || f.contains(s)
    }

    /** JSON 解析接口：GET `api+url`，取 $.url 与 $.headers/$.header */
    private suspend fun parseJsonAnalyze(url: String, headers: Map<String, String>): PlayResult = withContext(Dispatchers.IO) {
        val text = fetcher.fetchText(url) ?: return@withContext PlayResult("", true, "解析接口无响应", headers)
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return@withContext PlayResult("", true, "解析返回非 JSON", headers)
        val u = decodeIfEncoded(jstr(obj["url"]))
        val h = parseHeaderJson(obj["headers"]) ?: parseHeaderJson(obj["header"]) ?: headers
        if (u.isNotEmpty()) PlayResult(u, false, null, h)
        else PlayResult("", true, "JSON 解析未取到 url", headers)
    }

    /** 网页嗅探兜底：先静态文本正则提取（含相对路径 / JS 播放变量，按页面地址拼接）；
     *  包装型解析页（?url=分享页）再对原始分享页补一次静态提取；仍未命中再用隐藏 WebView 拦截媒体请求 / JS 变量 */
    private suspend fun parseWebSniff(url: String, headers: Map<String, String>): PlayResult = withContext(Dispatchers.IO) {
        val text = fetcher.fetchText(url)
        WebSniffer.staticExtract(text, url)?.let {
            return@withContext PlayResult(it, false, null, headers + ("Referer" to url))
        }
        // 包装解析页：还原 ?url= 指向的原始分享页，直接解析其页面内的相对路径 m3u8 / 播放变量
        val orig = runCatching { android.net.Uri.parse(url).getQueryParameter("url") }
            .getOrNull()
            ?.takeIf { it.startsWith("http", true) && it != url }
        if (orig != null) {
            val origText = fetcher.fetchText(orig)
            WebSniffer.staticExtract(origText, orig)?.let {
                return@withContext PlayResult(it, false, null, headers + ("Referer" to orig))
            }
        }
        val sniffed = WebSniffer.sniff(appContext, url, headers)
            ?: return@withContext PlayResult("", true, "网页嗅探未提取到媒体地址", headers)
        PlayResult(sniffed.url, false, null, headers + sniffed.headers)
    }

    private fun parseHeaderJson(el: kotlinx.serialization.json.JsonElement?): Map<String, String>? {
        val o = runCatching { el?.jsonObject }.getOrNull() ?: return null
        return o.entries.associate { (k, v) -> k to jstr(v) }.takeIf { it.isNotEmpty() }
    }

    /** 公开：解析实体中以 Map.toString() 存储的 headers，供播放器注入 */
    fun parseHeaders(raw: String?): Map<String, String> = parseHeaderString(raw)

    /** 站点自定义 headers（JSON 或 k=v 字符串） */
    private fun siteHeaders(site: SiteEntity): Map<String, String> = Converters.jsonToMap(site.headers)

    private fun siteReferer(site: SiteEntity): String? =
        siteHeaders(site).let { it["Referer"] ?: it["referer"] }

    /** Drpy 系列：GET 协议，请求带 extend 参数，响应同构 JSON */
    private fun isDrpy(type: Int): Boolean =
        type == SiteType.T4_DRPYJS0 || type == SiteType.T4_DRPYS || type == SiteType.T3_DRPY

    /** AppGet 套系：AppGet(15) / AppQi(16) / AppFox(17)，AES 登录 + 响应解密 */
    private fun isAppGet(type: Int): Boolean =
        type == SiteType.T3_APPGET || type == SiteType.T3_APPQI || type == SiteType.T3_APPFOX

    /**
     * 规则型适配器（二期）：Py(12) gRPC Python 蜘蛛 / CatOpen(14) workerpool JS 脚本。
     * 原生端无 Python / JS 运行时，先显式拦截，避免误走同构 JSON 兜底产生脏数据。
     * XBPQ(9) / XYQ(10) 暂放行走 MacCMS 同构 JSON 容错，规则钩子见对应 Adapter 骨架。
     */
    private fun isRuleAdapterPending(type: Int): Boolean =
        type == SiteType.T3_PY || type == SiteType.T3_CATOPEN

    /** 拉取远程文本（EPG / 节目单等），失败返回 null */
    suspend fun fetchRawText(url: String): String? = withContext(Dispatchers.IO) { fetcher.fetchText(url) }

    private fun parseHeaderString(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val cleaned = raw.trim().removePrefix("{").removeSuffix("}")
        return cleaned.split(",").mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx).trim() to it.substring(idx + 1).trim() else null
        }.toMap()
    }

    // ---- 历史 / 收藏 ----
    suspend fun findHistory(videoId: String, siteSource: String) =
        db.historyDao().findByVideo(videoId, siteSource)

    suspend fun saveHistory(
        siteSource: String,
        videoId: String,
        image: String?,
        name: String,
        index: String?,
        watchTime: Double = 0.0,
        duration: Double = 0.0,
        type: Int = RecordType.FILM
    ) {
        val existing = db.historyDao().findByVideo(videoId, siteSource)
        val now = System.currentTimeMillis()
        db.historyDao().upsert(
            HistoryEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                type = type,
                relateId = siteSource,
                siteSource = siteSource,
                videoId = videoId,
                videoImage = image,
                videoName = name,
                videoIndex = index,
                watchTime = watchTime.takeIf { it > 0 } ?: existing?.watchTime ?: 0.0,
                duration = duration.takeIf { it > 0 } ?: existing?.duration ?: 0.0,
                updatedAt = now
            )
        )
    }

    /** 播放中周期回写进度（秒）；不覆盖选集标题 */
    suspend fun updateProgress(videoId: String, siteSource: String, positionSec: Double, durationSec: Double) {
        val existing = db.historyDao().findByVideo(videoId, siteSource) ?: return
        db.historyDao().upsert(
            existing.copy(
                watchTime = positionSec,
                duration = durationSec,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun isStarred(videoId: String, relateId: String): Boolean =
        db.starDao().findByVideo(videoId, relateId) != null

    suspend fun toggleStar(
        siteSource: String,
        videoId: String,
        image: String?,
        name: String,
        remarks: String?,
        type: Int = RecordType.FILM
    ): Boolean {
        val existing = db.starDao().findByVideo(videoId, siteSource)
        return if (existing != null) {
            db.starDao().delete(existing)
            false
        } else {
            db.starDao().upsert(
                StarEntity(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    relateId = siteSource,
                    videoId = videoId,
                    videoImage = image,
                    videoName = name,
                    videoType = null,
                    videoRemarks = remarks
                )
            )
            true
        }
    }

    // ---- 搜索历史 / 聚合搜索 ----
    val searchHistory: Flow<List<SearchHistoryEntity>> = db.searchHistoryDao().observeRecent()

    suspend fun addSearchHistory(kw: String) {
        val key = kw.trim()
        if (key.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = db.searchHistoryDao().getByKeyword(key)
        db.searchHistoryDao().upsert(
            SearchHistoryEntity(
                keyword = key,
                count = (existing?.count ?: 0) + 1,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun removeSearchHistory(kw: String) = db.searchHistoryDao().delete(kw.trim())
    suspend fun clearSearchHistory() = db.searchHistoryDao().clear()

    suspend fun searchSuggest(kw: String, limit: Int = 8): List<String> {
        val key = kw.trim()
        if (key.isEmpty()) return emptyList()
        return db.searchHistoryDao().suggest("%$key%", limit).map { it.keyword }
    }

    /** 跨启用且允许搜索的站点聚合检索；每站取前若干条，结果标注来源 */
    suspend fun aggregateSearch(wd: String): List<AggregateVod> {
        val key = wd.trim()
        if (key.isEmpty()) return emptyList()
        addSearchHistory(key)
        val active = sites.first().filter { it.isActive && it.search }
        DebugLog.d("ZySearch", "聚合搜索 关键词=$key 参与站点=${active.size}")
        val result = mutableListOf<AggregateVod>()
        for (site in active) {
            runCatching {
                val page = fetchSearch(site, key, 1)
                page.list.take(10).forEach { vod ->
                    result.add(AggregateVod(site.id, site.name, vod))
                }
            }.onFailure {
                DebugLog.w("ZySearch", "站点 ${site.name} 搜索异常 ${it.javaClass.simpleName}: ${it.message}")
            }
        }
        DebugLog.d("ZySearch", "聚合搜索完成 关键词=$key 命中=${result.size}")
        return result
    }

    /** 相关推荐：本期回落到同站点首屏列表；后续可按分类 / 标签关联 */
    suspend fun relatedRecommend(site: SiteEntity, detail: CmsDetail): List<CmsVod> =
        runCatching { fetchVodList(site, "", 1).list.take(12) }.getOrDefault(emptyList())

    // ---- 热榜榜单（对齐上游 zyfun `rec/utils/hot.ts`：8 个数据源 × 4 分类） ----

    /**
     * 按数据源 key + 分类抓取榜单。category: 1 电影 2 电视剧 3 综艺 4 动漫。
     * 失败返回空列表，不抛到 UI。
     */
    suspend fun fetchRank(sourceKey: String, category: Int): List<RankItem> = withContext(Dispatchers.IO) {
        when (sourceKey) {
            "baidu" -> fetchBaiduRank(category)
            "enlightent" -> fetchEnlightentRank(category)
            "hisense" -> fetchHisenseRank(category)
            "huantv" -> fetchHuantvRank(category)
            "kylive" -> fetchKyliveRank(category)
            "komect" -> fetchKomectRank(category)
            "quark" -> fetchQuarkRank(category)
            else -> fetchDoubanRank(category)
        }
    }

    private fun qurl(base: String, vararg params: Pair<String, String>): String {
        val sep = if (base.contains("?")) "&" else "?"
        val q = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return base + sep + q
    }

    private fun fmtToday(pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date())

    private fun fmtYesterday(pattern: String): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat(pattern, Locale.US).format(c.time)
    }

    /** 豆瓣：1 movie_showing 2 tv_hot 3 tv_variety_show */
    private suspend fun fetchDoubanRank(category: Int): List<RankItem> {
        val collection = when (category) {
            1 -> "movie_showing"
            2 -> "tv_hot"
            3 -> "tv_variety_show"
            else -> "tv_hot"
        }
        val url = "https://m.douban.com/rexxar/api/v2/subject_collection/$collection/items?start=0&count=30"
        val text = fetcher.fetchText(url, referer = "https://movie.douban.com")
            ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        val arr = root["subject_collection_items"]?.jsonArray ?: return emptyList()
        return arr.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val title = jstr(o["title"])
            if (title.isEmpty()) return@mapIndexedNotNull null
            val type = jstr(o["type"])
            val pic = when (type) {
                "tv" -> jstr(o["pic"]?.jsonObject?.get("large")).ifEmpty {
                    jstr(o["pic"]?.jsonObject?.get("normal"))
                }
                else -> jstr(o["cover"]?.jsonObject?.get("url"))
            }
            val rating = o["rating"]?.jsonObject
            val hot = if (type == "movie") {
                jstr(rating?.get("value"))
            } else {
                val count = jint(rating?.get("count"))
                if (count > 0) "${count}人评价" else ""
            }
            RankItem(
                id = jstr(o["id"]).ifEmpty { title },
                name = title,
                hot = hot,
                pic = pic,
                remarks = jstr(o["card_subtitle"]),
                rank = idx + 1
            )
        }
    }

    /** 百度：1 电影 2 电视剧 */
    private suspend fun fetchBaiduRank(category: Int): List<RankItem> {
        val typeLabel = if (category == 2) "电视剧" else "电影"
        val url = qurl(
            "https://opendata.baidu.com/api.php",
            "resource_id" to "51274",
            "ks_from" to "aladdin",
            "new_need_di" to "1",
            "from_mid" to "1",
            "sort_type" to "1",
            "query" to "${typeLabel}排行榜",
            "tn" to "wisexmlnew",
            "dsp" to "iphone",
            "format" to "json",
            "ie" to "utf-8",
            "oe" to "utf-8",
            "sort_key" to "1",
            "stat0" to typeLabel,
            "stat1" to "全部",
            "stat2" to "全部",
            "stat3" to "全部",
            "rn" to "30",
            "pn" to "0",
            "trigger_srcid" to "51251"
        )
        val text = fetcher.fetchText(url) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        if (jint(root["ResultCode"]) != 0) return emptyList()
        val list = root["Result"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("DisplayData")?.jsonObject
            ?.get("resultData")?.jsonObject
            ?.get("tplData")?.jsonObject
            ?.get("result")?.jsonObject
            ?.get("result")?.jsonArray ?: return emptyList()
        return list.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val name = jstr(o["ename"])
            if (name.isEmpty()) return@mapIndexedNotNull null
            val score = jstr(o["douBanInfo"]?.jsonObject?.get("score"))
            RankItem(
                id = jstr(o["urlsign"]).ifEmpty { name },
                name = name,
                hot = score,
                pic = jstr(o["img"]),
                remarks = jstr(o["additional"]),
                rank = idx + 1
            )
        }
    }

    /** 云合：1 电影 2 电视剧 3 综艺 4 动漫；默认取昨日数据，POST 表单 */
    private suspend fun fetchEnlightentRank(category: Int): List<RankItem> {
        val channelType = when (category) {
            1 -> "movie"
            2 -> "tv"
            3 -> "art"
            4 -> "animation"
            else -> "movie"
        }
        val date = fmtYesterday("yyyy/MM/dd")
        val text = fetcher.postForm(
            "https://www.enlightent.cn/research/top/getTopRanking",
            form = mapOf(
                "channelType" to channelType,
                "sort" to "playTimesPredicted",
                "day" to "1",
                "channel" to "total",
                "date" to date
            )
        ) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        val list = root["content"]?.jsonArray ?: return emptyList()
        return list.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val name = jstr(o["name"])
            if (name.isEmpty()) return@mapIndexedNotNull null
            val count = jint(o["barrageCount"]).takeIf { it > 0 } ?: jint(o["commentCount"])
            RankItem(
                id = jstr(o["nameID"]).ifEmpty { name },
                name = name,
                hot = if (count > 0) "评论 $count" else "",
                pic = "",
                remarks = jstr(o["dayPlatform"]),
                rank = idx + 1
            )
        }
    }

    /** 海信：1 电影 2 电视剧 3 综艺 4 少儿 */
    private suspend fun fetchHisenseRank(category: Int): List<RankItem> {
        val categoryId = when (category) {
            1 -> "2002"
            2 -> "2003"
            3 -> "2004"
            4 -> "2007"
            else -> "2003"
        }
        val url = qurl(
            "https://wxtv-vod.hismarttv.com/category/api/search/v2/$categoryId",
            "version" to "3.6",
            "deviceType" to "2",
            "type" to "2",
            "languageId" to "0",
            "deviceid" to "",
            "distributeId" to "2001",
            "license" to "",
            "start" to "0",
            "rows" to "30",
            "show" to "false",
            "sort" to "325",
            "platid" to "2",
            "appVersion" to "6.3.10-0"
        )
        val text = fetcher.fetchText(url) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        val list = root["medias"]?.jsonArray ?: return emptyList()
        return list.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val name = jstr(o["title"])
            if (name.isEmpty()) return@mapIndexedNotNull null
            val score = jstr(o["douban_rate"]).ifEmpty { jstr(o["rate"]) }
            RankItem(
                id = jstr(o["id"]).ifEmpty { name },
                name = name,
                hot = score,
                pic = jstr(o["image_rec_url"]).ifEmpty { jstr(o["image_icon_url"]) },
                remarks = jstr(o["update_mark"]),
                rank = idx + 1
            )
        }
    }

    /** 欢网：1 电影 2 电视剧 3 综艺 */
    private suspend fun fetchHuantvRank(category: Int): List<RankItem> {
        val channelType = when (category) {
            1 -> 3
            2 -> 1
            3 -> 2
            else -> 1
        }
        val date = fmtToday("yyyy-MM-dd")
        val url = qurl(
            "https://tv-zone-api.huan.tv/tv-zone-mobile-api/v1/channel/demand/list/$date/json",
            "channel_type" to channelType.toString(),
            "duration" to "1"
        )
        val text = fetcher.fetchText(url) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        if (jbool(root["success"]).not()) return emptyList()
        val list = root["data"]?.jsonObject?.get("data_list")?.jsonArray ?: return emptyList()
        return list.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val name = jstr(o["programName"])
            if (name.isEmpty()) return@mapIndexedNotNull null
            val rate = jstr(o["playRate"])
            RankItem(
                id = jstr(o["wikiId"]).ifEmpty { jstr(o["wikiIdH"]).ifEmpty { name } },
                name = name,
                hot = if (rate.isNotEmpty()) "播放率 $rate" else "",
                pic = "",
                remarks = jstr(o["level"]),
                rank = idx + 1
            )
        }
    }

    /** 酷云：2 电视剧 3 综艺；按上月年月取收视榜 */
    private suspend fun fetchKyliveRank(category: Int): List<RankItem> {
        val vt = if (category == 3) 3 else 2
        val cal = Calendar.getInstance()
        val month0 = cal.get(Calendar.MONTH)
        var month = if (month0 == 0) 12 else month0
        var year = cal.get(Calendar.YEAR)
        if (month0 == 0) year -= 1
        val url = qurl(
            "https://www.ky.live/api/full",
            "dt" to "3",
            "y" to year.toString(),
            "m" to month.toString(),
            "vt" to vt.toString()
        )
        val text = fetcher.fetchText(url) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        if (jbool(root["status"]).not()) return emptyList()
        val list = root["data"]?.jsonArray ?: return emptyList()
        return list.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val name = jstr(o["epg"])
            if (name.isEmpty()) return@mapIndexedNotNull null
            val mr = jdouble(o["mr"])
            val remarks = if (mr > 0) "播放市占${(mr * 100).toFloat().toString().take(5)}%" else "播放市占0.00%"
            RankItem(
                id = jstr(o["caid"]).ifEmpty { name },
                name = name,
                hot = "热度 ${jint(o["count"])}",
                pic = "",
                remarks = remarks,
                rank = idx + 1
            )
        }
    }

    /** 未来电视（移动爱家）：1 电影 2 电视剧 3 综艺 4 少儿；POST JSON */
    private suspend fun fetchKomectRank(category: Int): List<RankItem> {
        val contentType = when (category) {
            1 -> "电影"
            2 -> "电视剧"
            3 -> "综艺"
            4 -> "少儿"
            else -> "电视剧"
        }
        val body = buildJsonObject {
            put("openId", "111")
            put("provCode", "42")
            put("licensedParty", "未来电视")
            put("deviceType", "502090")
            putJsonArray("contentTypeIndexs") {
                add(buildJsonObject {
                    put("contentType", contentType)
                    put("pageNum", 1)
                    put("pageSize", 30)
                })
            }
        }.toString()
        val headers = mapOf(
            "auth" to "3637df52d98ce8815fe47bbe49fe6459",
            "Origin" to "https://msi.nsoap.komect.com",
            "Referer" to "https://msi.nsoap.komect.com/minitvH5/index.html",
            "channelId" to "H5"
        )
        val text = fetcher.postJson(
            "https://msi.nsoap.komect.com/msi/cbiz/dp/contentInfo/homePage/list",
            body,
            headers = headers
        ) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        if (jint(root["recode"]) != 1) return emptyList()
        val list = root["data"]?.jsonObject
            ?.get("items")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("contentInfoList")?.jsonArray ?: return emptyList()
        return list.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val name = jstr(o["dpContentName"])
            if (name.isEmpty()) return@mapIndexedNotNull null
            val playCount = jlong(o["dpPlayCount"])
            RankItem(
                id = jstr(o["psId"]).ifEmpty { name },
                name = name,
                hot = if (playCount > 0) "播放 $playCount" else "",
                pic = jstr(o["dpContentPicUrl"]),
                remarks = jstr(o["dpContentShortDesc"]),
                rank = idx + 1
            )
        }
    }

    /** 夸克：1 电影 2 电视剧 3 综艺 */
    private suspend fun fetchQuarkRank(category: Int): List<RankItem> {
        val channel = when (category) {
            1 -> "电影"
            2 -> "电视剧"
            3 -> "综艺"
            else -> "电视剧"
        }
        val url = qurl(
            "https://quark.sm.cn/api/rest",
            "method" to "yingshi_list.getData",
            "q" to "default",
            "hit" to "30",
            "fi_channel" to channel,
            "vendor" to "100002",
            "belong" to "other",
            "start" to "1",
            "fi_rank_type" to "最新",
            "fi_cate" to "全部",
            "fi_area" to "全部",
            "fi_year" to "全部",
            "second_tag" to "true",
            "_" to System.currentTimeMillis().toString()
        )
        val text = fetcher.fetchText(url) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        val list = root["item"]?.jsonArray ?: return emptyList()
        return list.mapIndexedNotNull { idx, el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val name = jstr(o["title"])
            if (name.isEmpty()) return@mapIndexedNotNull null
            RankItem(
                id = name,
                name = name,
                hot = jstr(o["hot_score"]),
                pic = jstr(o["src"]),
                remarks = jstr(o["episode_count"]),
                rank = idx + 1
            )
        }
    }

    /** 榜单 / 兜底解析用：取默认（或首条启用）解析源；无可用源返回 null */
    suspend fun fallbackAnalyze(): AnalyzeEntity? = pickAnalyze(null)

    // ---- 压缩包 / 深链导入 ----

    /** 解压本地配置压缩包并导入；返回 (摘要, 错误提示)，二者其一非空 */
    suspend fun importArchiveBytes(fileName: String, bytes: ByteArray): Pair<ImportSummary?, String?> {
        val contents = ArchiveImporter.parse(fileName, bytes)
        if (contents.unsupported != null) return null to contents.unsupported
        val text = ArchiveImporter.merge(contents)
        if (text.isBlank()) return null to "压缩包内未找到可识别的配置文件"
        return importText(text) to null
    }

    /** 解析 zy:// 深链或普通订阅链接并导入配置 */
    suspend fun importDeepLink(raw: String): ImportSummary? {
        val target = DeepLink.parse(raw) as? DeepLinkTarget.Subscribe ?: return null
        return importConfigUrl(target.url)
    }

    /** 填充 EPG / 台标模板中的 {name} {date} */
    fun fillTemplate(template: String, name: String, date: String = today()): String =
        template.replace("{name}", name).replace("{date}", date)

    /** 拉取节目单：模板含 {name}/{date} 走 JSON(diyp)，否则按 XMLTV 整单解析 */
    suspend fun fetchEpg(api: String, program: String, date: String = today()): List<EpgItem> = withContext(Dispatchers.IO) {
        if (api.isBlank()) return@withContext emptyList()
        val isJson = api.contains("{name}") || api.contains("{date}")
        val url = if (isJson) fillTemplate(api, program, date) else api
        val text = fetcher.fetchText(url) ?: return@withContext emptyList()
        if (isJson) {
            runCatching {
                val obj = json.parseToJsonElement(text).jsonObject
                val arr = obj["epg_data"]?.jsonArray ?: return@runCatching emptyList()
                arr.map { el ->
                    val o = el.jsonObject
                    EpgItem(
                        title = jstr(o["title"]).ifEmpty { jstr(o["name"]) },
                        start = jstr(o["start"]),
                        end = jstr(o["end"])
                    )
                }.filter { it.title.isNotEmpty() }
            }.getOrDefault(emptyList())
        } else {
            XmltvParser.parse(text, program)
        }
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun jstr(el: kotlinx.serialization.json.JsonElement?): String =
        el?.let { runCatching { it.jsonPrimitive.content }.getOrDefault("") } ?: ""

    private fun jint(el: JsonElement?, def: Int = 0): Int =
        runCatching { el!!.jsonPrimitive.content.toInt() }.getOrDefault(def)

    private fun jlong(el: JsonElement?, def: Long = 0L): Long =
        runCatching { el!!.jsonPrimitive.content.toLong() }.getOrDefault(def)

    private fun jbool(el: JsonElement?, def: Boolean = false): Boolean =
        runCatching { el!!.jsonPrimitive.content.toBooleanStrictOrNull() ?: def }.getOrDefault(def)

    private fun jdouble(el: JsonElement?, def: Double = 0.0): Double =
        runCatching { el!!.jsonPrimitive.content.toDouble() }.getOrDefault(def)

    private fun urlWith(base: String, vararg params: Pair<String, String>): String {
        val sep = if (base.contains("?")) "&" else "?"
        val q = params.joinToString("&") { "${it.first}=${it.second}" }
        return base + sep + q
    }
}
