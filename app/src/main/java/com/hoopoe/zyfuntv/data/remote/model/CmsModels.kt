package com.hoopoe.zyfuntv.data.remote.model

/** 站点分类（T1 ac=class 的 class 数组项） */
data class CmsCategory(
    val typeId: String,
    val typeName: String
)

/** 影片条目（vod_* 字段归一化） */
data class CmsVod(
    val id: String,
    val name: String,
    val pic: String,
    val remarks: String,
    val blurb: String
)

/** 分页影片结果 */
data class CmsPage(
    val page: Int,
    val pageCount: Int,
    val total: Int,
    val list: List<CmsVod>
)

/** 单集 */
data class CmsEpisode(val title: String, val url: String)

/** 一条播放线路（含多集） */
data class CmsPlaySource(val name: String, val episodes: List<CmsEpisode>)

/** 影片详情 */
data class CmsDetail(
    val id: String,
    val name: String,
    val pic: String,
    val remarks: String,
    val year: String,
    val lang: String,
    val area: String,
    val score: String,
    val state: String,
    val klass: String,
    val actor: String,
    val director: String,
    val content: String,
    val blurb: String,
    val typeName: String,
    val sources: List<CmsPlaySource>
)

/** 单条 EPG 节目 */
data class EpgItem(val title: String, val start: String, val end: String)

/** 播放地址解析结果 */
data class PlayResult(
    val url: String,
    val needParse: Boolean,
    val message: String?,
    val headers: Map<String, String> = emptyMap()
)

/** 跨站聚合搜索结果：标注命中来源站点 */
data class AggregateVod(
    val siteId: String,
    val siteName: String,
    val vod: CmsVod
)

/** 榜单条目（对齐上游 IRecommHot：片名 / 热度或评分 / 海报 / 副标题 / 名次） */
data class RankItem(
    val id: String,
    val name: String,
    val hot: String,
    val pic: String,
    val remarks: String,
    val rank: Int
)

/** 热榜数据源：key 对齐上游 fetchHot 键；categories 支持的分类（1 电影 2 电视剧 3 综艺 4 动漫） */
data class RankSource(
    val key: String,
    val label: String,
    val categories: List<Int>
)

/** 媒体格式判定结果：是否可由当前内核直解、是否需兜底内核 */
enum class MediaKind { HLS, DASH, MP4, TS, FLV, MKV, UNKNOWN }

object MediaKindClassifier {
    fun classify(url: String): MediaKind {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") || path.contains(".m3u8") -> MediaKind.HLS
            path.endsWith(".mpd") || path.contains(".mpd") -> MediaKind.DASH
            path.endsWith(".mp4") -> MediaKind.MP4
            path.endsWith(".flv") || path.contains(".flv") -> MediaKind.FLV
            path.endsWith(".mkv") -> MediaKind.MKV
            path.endsWith(".ts") || path.contains(".ts") -> MediaKind.TS
            else -> MediaKind.UNKNOWN
        }
    }

    /** 当前 Media3 内核可直解；FLV/部分 TS/MKV 需兜底内核（占位） */
    fun supportedByMedia3(kind: MediaKind): Boolean =
        kind == MediaKind.HLS || kind == MediaKind.DASH || kind == MediaKind.MP4
}
