package com.hoopoe.zyfuntv.ui.theme.mobile

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 手机端尺寸令牌：间距 / 圆角 / 海报与图标尺寸 / 字号 / 控件高度。
 * 手机竖屏/小屏场景下可在此独立调整间距、海报与字号，不影响 TV 端。
 * 初值与原共用 ThemeSize 保持一致，迁移期视觉不变。
 */
object MobileSize {
    // ---- 间距 ----
    val miniMargin = 6.dp
    val smallMargin = 12.dp
    val containerPadding = 16.dp

    // ---- 圆角 ----
    val middleRadius = 12.dp
    val bigRadius = 16.dp
    val superRadius = 20.dp

    // ---- 海报 ----
    val movieWidth = 120.dp
    val movieHeight = 160.dp

    // ---- 图标 ----
    val smallIcon = 20.dp
    val middleIcon = 30.dp
    val bigIcon = 40.dp

    // ---- 头像 ----
    val middleAvater = 40.dp
    val bigAvater = 60.dp

    // ---- 字号 ----
    val normalFontSize = 12.sp
    val smallFontSize = 14.sp
    val middleFontSize = 16.sp
    val bigFontSize = 20.sp

    // ---- 控件 ----
    val borderWidth = 1.dp
    val buttonHeight = 50.dp
    val inputHeight = 50.dp
    val lineHeight = 30.dp
    val lineWidth = 3.dp
}
