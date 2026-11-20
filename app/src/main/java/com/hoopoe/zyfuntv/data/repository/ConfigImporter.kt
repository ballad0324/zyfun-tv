package com.hoopoe.zyfuntv.data.repository

import com.hoopoe.zyfuntv.data.local.AnalyzeEntity
import com.hoopoe.zyfuntv.data.local.AnalyzeDao
import com.hoopoe.zyfuntv.data.local.ChannelEntity
import com.hoopoe.zyfuntv.data.local.ChannelDao
import com.hoopoe.zyfuntv.data.local.IptvDao
import com.hoopoe.zyfuntv.data.local.IptvEntity
import com.hoopoe.zyfuntv.data.local.SiteDao
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.remote.model.CatvodConfig
import com.hoopoe.zyfuntv.data.remote.model.TvboxConfig
import com.hoopoe.zyfuntv.data.remote.model.TvboxLiveNewItem
import com.hoopoe.zyfuntv.data.remote.model.TvboxLiveOldItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

data class ImportSummary(
    val sites: Int,
    val parses: Int,
    val iptv: Int,
    val channels: Int
)

class ConfigImporter(
    private val siteDao: SiteDao,
    private val iptvDao: IptvDao,
    private val channelDao: ChannelDao,
    private val analyzeDao: AnalyzeDao
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun uuid() = UUID.randomUUID().toString()

    /** 导入 TVBox/Catvod 风格 JSON 配置 */
    suspend fun importTvbox(text: String): ImportSummary {
        val cfg = json.decodeFromString<TvboxConfig>(text)
        var siteN = 0
        var parseN = 0
        var iptvN = 0
        var chanN = 0

        for (item in cfg.sites) {
            val categories = JsonArray(item.categories.map { JsonPrimitive(it) }).toString()
            val siteHeaders = (item.ext as? JsonObject)?.get("header")?.toString()
                ?: (item.ext as? JsonObject)?.get("headers")?.toString()
            siteDao.upsert(
                SiteEntity(
                    id = uuid(),
                    key = item.key.ifEmpty { uuid() },
                    name = item.name.ifEmpty { "未命名站点" },
                    api = item.api.ifEmpty { null },
                    playUrl = item.playUrl.ifEmpty { null },
                    search = item.searchable != 0,
                    type = item.type,
                    ext = item.ext?.toString(),
                    categories = categories,
                    headers = siteHeaders
                )
            )
            siteN++
        }

        for (p in cfg.parses) {
            val flag = (p.ext as? JsonObject)?.get("flag")?.toString()
            val headers = p.header?.toString()
            analyzeDao.upsert(
                AnalyzeEntity(
                    id = uuid(),
                    key = p.url.ifEmpty { uuid() },
                    name = p.name.ifEmpty { "未命名解析" },
                    api = p.url,
                    type = p.type,
                    flag = flag,
                    headers = headers
                )
            )
            parseN++
        }

        for (obj in cfg.lives) {
            if (obj.containsKey("channels")) {
                val old = json.decodeFromJsonElement(TvboxLiveOldItem.serializer(), obj)
                for (ch in old.channels) {
                    val url = ch.urls.firstOrNull() ?: continue
                    channelDao.upsert(
                        ChannelEntity(
                            id = uuid(),
                            name = ch.name,
                            api = url,
                            groupName = old.group.ifEmpty { null }
                        )
                    )
                    chanN++
                }
            } else {
                val nw = json.decodeFromJsonElement(TvboxLiveNewItem.serializer(), obj)
                val liveHeaders = nw.headers?.toString() ?: nw.header?.toString()
                iptvDao.upsert(
                    IptvEntity(
                        id = uuid(),
                        key = uuid(),
                        name = nw.name.ifEmpty { "未命名直播源" },
                        api = nw.url,
                        type = nw.type,
                        epg = nw.epg.ifEmpty { null },
                        logo = nw.logo.ifEmpty { null },
                        headers = liveHeaders
                    )
                )
                iptvN++
            }
        }

        return ImportSummary(siteN, parseN, iptvN, chanN)
    }

    /** 导入 Catvod 配置（video/read/comic/music/pan 站点并入影视源） */
    suspend fun importCatvod(text: String): Int {
        val cfg = json.decodeFromString<CatvodConfig>(text)
        val all = cfg.video.sites + cfg.read.sites + cfg.comic.sites +
            cfg.music.sites + cfg.pan.sites
        for (s in all) {
            val categories = JsonArray(s.categories.map { JsonPrimitive(it) }).toString()
            val siteHeaders = s.headers?.toString() ?: s.header?.toString()
            siteDao.upsert(
                SiteEntity(
                    id = uuid(),
                    key = s.key.ifEmpty { uuid() },
                    name = s.name.ifEmpty { "未命名站点" },
                    api = s.api.ifEmpty { null },
                    playUrl = s.playUrl.ifEmpty { null },
                    search = s.search != 0,
                    type = s.type,
                    ext = s.ext?.toString(),
                    categories = categories,
                    headers = siteHeaders
                )
            )
        }
        return all.size
    }
}
