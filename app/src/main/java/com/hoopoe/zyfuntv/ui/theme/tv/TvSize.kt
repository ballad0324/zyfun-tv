package com.hoopoe.zyfuntv.ui.theme.tv

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TV 端尺寸令牌：间距 / 圆角 / 海报与图标尺寸 / 字号 / 控件高度。
 * 大屏（10 英尺）场景下独立放大间距、海报与焦点尺寸，约为手机基线的 1.2 倍，不影响手机端。
 */
object TvSize {
    // ---- 间距 ----
    val miniMargin = 8.dp
    val smallMargin = 16.dp
    val containerPadding = 20.dp

    // ---- 圆角 ----
    val middleRadius = 14.dp
    val bigRadius = 18.dp
    val superRadius = 22.dp

    // ---- 海报 ----
    val movieWidth = 150.dp
    val movieHeight = 200.dp
    /** 榜单/解析等内嵌播放器横幅的紧凑限高，避免宽屏 16:9 把海报网格挤出屏幕 */
    val playerHeight = 220.dp

    // ---- 图标 ----
    val smallIcon = 24.dp
    val middleIcon = 36.dp
    val bigIcon = 48.dp

    // ---- 头像 ----
    val middleAvater = 48.dp
    val bigAvater = 72.dp

    // ---- 字号 ----
    val normalFontSize = 14.sp
    val smallFontSize = 16.sp
    val middleFontSize = 18.sp
    val bigFontSize = 24.sp

    // ---- 控件 ----
    val borderWidth = 1.dp
    val buttonHeight = 58.dp
    val inputHeight = 58.dp
    val lineHeight = 36.dp
    val lineWidth = 4.dp

    // ---- 左侧竖排导航栏 ----
    val sideBarWidth = 150.dp
    val sideBarItemHeight = 52.dp
    val sideBarIcon = 20.dp
    /** 侧栏条目之间的上下间距 */
    val sideBarItemSpacing = 6.dp
    /** 侧栏条目内部上下留白 */
    val sideBarItemVPadding = 4.dp
    /** 侧栏条目文字字号 / 行高（横屏高度紧张，收紧避免被裁剪） */
    val navLabelFontSize = 15.sp
    val navLabelLineHeight = 20.sp
}
