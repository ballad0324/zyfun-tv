package com.hoopoe.zyfuntv.core.model

/** 影视站点类型，对齐 zyfun SITE_TYPE（见 docs/01 §3） */
object SiteType {
    const val T0_XML = 0
    const val T1_JSON = 1
    const val T4_DRPYJS0 = 2
    const val T4_DRPYS = 6
    const val T3_DRPY = 7
    const val T4_CATVOD = 8
    const val T3_XBPQ = 9
    const val T3_XYQ = 10
    const val T3_APPYSV2 = 11
    const val T3_PY = 12
    const val T3_ALIST = 13
    const val T3_CATOPEN = 14
    const val T3_APPGET = 15
    const val T3_APPQI = 16
    const val T3_APPFOX = 17
}

/** 直播源类型 */
object IptvType {
    const val REMOTE = 1
    const val LOCAL = 2
    const val MANUAL = 3
}

/** 解析源类型：1 网页嗅探 / 2 JSON 接口 */
object AnalyzeType {
    const val WEB = 1
    const val JSON = 2
}

/** 代理类型 */
object ProxyType {
    const val DIRECT = "direct"
    const val SYSTEM = "system"
    const val CUSTOM = "custom"
}

/** 界面模式：自动（按设备判定）/ 电视端 / 手机端；本期先实现电视端，手机端布局预留 */
object UiMode {
    const val AUTO = "auto"
    const val TV = "tv"
    const val PHONE = "phone"
}

/** 历史 / 收藏记录类型：1 影视 / 2 直播 */
object RecordType {
    const val FILM = 1
    const val LIVE = 2
}

/** 主题外观：明亮 / 黑暗 / 跟随系统；默认明亮 */
object ThemeMode {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"
}
