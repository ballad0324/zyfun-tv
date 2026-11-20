package com.hoopoe.zyfuntv.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class TvboxSiteItem(
    val name: String = "",
    val key: String = "",
    val type: Int = 0,
    val api: String = "",
    val playUrl: String = "",
    val quickSearch: Int = 0,
    val searchable: Int = 1,
    val filterable: Int = 0,
    val ext: JsonElement? = null,
    val categories: List<String> = emptyList()
)

@Serializable
data class TvboxParseItem(
    val name: String = "",
    val type: Int = 1,
    val url: String = "",
    val header: JsonObject? = null,
    val ext: JsonObject? = null
)

/** 旧版直播：分组 + 频道 urls */
@Serializable
data class TvboxLiveOldItem(
    val group: String = "",
    val channels: List<TvboxLiveChannel> = emptyList()
)

@Serializable
data class TvboxLiveChannel(
    val name: String = "",
    val urls: List<String> = emptyList()
)

/** 新版直播：远程/本地/手动源 */
@Serializable
data class TvboxLiveNewItem(
    val name: String = "",
    val type: Int = 1,
    val url: String = "",
    val epg: String = "",
    val logo: String = "",
    val ua: String = "",
    val timeout: Int = 15,
    val playerType: Int = 0,
    val headers: JsonObject? = null,
    val header: JsonObject? = null
)

@Serializable
data class TvboxConfig(
    val sites: List<TvboxSiteItem> = emptyList(),
    val parses: List<TvboxParseItem> = emptyList(),
    val lives: List<JsonObject> = emptyList(),
    val flags: List<String> = emptyList(),
    val ads: List<String> = emptyList()
)

@Serializable
data class CatvodSiteItem(
    val key: String = "",
    val name: String = "",
    val type: Int = 0,
    val api: String = "",
    val playUrl: String = "",
    val search: Int = 1,
    val ext: JsonElement? = null,
    val categories: List<String> = emptyList(),
    val headers: JsonObject? = null,
    val header: JsonObject? = null
)

@Serializable
data class CatvodSection(val sites: List<CatvodSiteItem> = emptyList())

@Serializable
data class CatvodConfig(
    val video: CatvodSection = CatvodSection(),
    val read: CatvodSection = CatvodSection(),
    val comic: CatvodSection = CatvodSection(),
    val music: CatvodSection = CatvodSection(),
    val pan: CatvodSection = CatvodSection()
)
