package com.hoopoe.zyfuntv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 影视 CMS 源（tbl_site），字段对齐 docs/01 §1.1 */
@Entity(tableName = "tbl_site")
data class SiteEntity(
    @PrimaryKey val id: String,
    val key: String,
    val name: String,
    val api: String? = null,
    val playUrl: String? = null,
    val search: Boolean = true,
    @ColumnInfo(name = "group_name") val groupName: String? = null,
    val type: Int = 0,
    val ext: String? = null,
    val categories: String? = null,
    val headers: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 直播源（tbl_iptv） */
@Entity(tableName = "tbl_iptv")
data class IptvEntity(
    @PrimaryKey val id: String,
    val key: String,
    val name: String,
    val api: String,
    val type: Int = 1,
    val epg: String? = null,
    val logo: String? = null,
    val headers: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 频道（tbl_channel） */
@Entity(tableName = "tbl_channel")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val api: String,
    val logo: String? = null,
    val playback: String? = null,
    val headers: String? = null,
    @ColumnInfo(name = "group_name") val groupName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 解析源（tbl_analyze） */
@Entity(tableName = "tbl_analyze")
data class AnalyzeEntity(
    @PrimaryKey val id: String,
    val key: String,
    val name: String,
    val api: String,
    val type: Int = 1,
    val flag: String? = null,
    val headers: String? = null,
    val script: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 播放历史（tbl_history） */
@Entity(tableName = "tbl_history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val type: Int,
    val relateId: String? = null,
    val siteSource: String? = null,
    val playEnd: Boolean = false,
    val videoId: String? = null,
    val videoImage: String? = null,
    val videoName: String? = null,
    val videoIndex: String? = null,
    val watchTime: Double = 0.0,
    val duration: Double = 0.0,
    val skipTimeInEnd: Double = 0.0,
    val skipTimeInStart: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 搜索历史（tbl_search_history）：聚合搜索关键词，按时间倒序去重 */
@Entity(tableName = "tbl_search_history")
data class SearchHistoryEntity(
    @PrimaryKey val keyword: String,
    val count: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 收藏（tbl_star） */
@Entity(tableName = "tbl_star")
data class StarEntity(
    @PrimaryKey val id: String,
    val type: Int,
    val relateId: String? = null,
    val videoId: String? = null,
    val videoImage: String? = null,
    val videoName: String? = null,
    val videoType: String? = null,
    val videoRemarks: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
