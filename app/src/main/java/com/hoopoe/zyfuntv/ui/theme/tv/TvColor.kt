package com.hoopoe.zyfuntv.ui.theme.tv

import androidx.compose.ui.graphics.Color

/**
 * TV 端跨组件固定色令牌。
 * 随明暗变化的语义色一律走 MaterialTheme.colorScheme；这里只保留固定值（评分金、描边灰等）。
 * 初值与原共用 ThemeColor 保持一致。
 */
object TvColor {
    val activeColor = Color(0xFF157A66)
    val selectedColor = Color(0xFFFFB422)
    val borderColor = Color(0xFFC3CFC9)
    val warnColor = Color(0xFFBA1A1A)
    val transparent = Color.Transparent
}
