package com.hoopoe.zyfuntv.ui.nav

import com.hoopoe.zyfuntv.R

/** 主导航目的地，对齐 zyfun 路由（router/modules/homepage.ts） */
enum class Dest(
    val titleRes: Int,
    val subtitle: String
) {
    Home(R.string.nav_home, "首页 · 全局搜索（暂定）"),
    Film(R.string.nav_film, "站点分类 · 影片列表 · 详情剧集 · 播放"),
    Live(R.string.nav_live, "分组频道 · EPG 节目单 · 直播播放"),
    Rank(R.string.nav_rank, "豆瓣热榜 · 电影 / 电视剧 / 综艺 · 搜索或解析兜底"),
    Parse(R.string.nav_parse, "解析源管理 · 网页嗅探 / JSON 解析"),
    Cloud(R.string.nav_cloud, "Alist 网盘 · 站点管理 · 云备份 / 还原"),
    Moment(R.string.nav_moment, "播放历史 · 我的收藏"),
    Setting(R.string.nav_setting, "源管理 · 界面模式 · 播放器 · 网络"),
    Lab(R.string.nav_setting, "加解密工具 · 嗅探 · 扩展调试（二期）");

    companion object {
        /** 主导航展示顺序（实验室并入设置，不在主导航栏展示） */
        val navItems: List<Dest> = listOf(Film, Live, Rank, Parse, Cloud, Moment, Setting)
    }
}
