package com.hoopoe.zyfuntv.ui.theme.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** 墨绿影院 · 明亮主题：中性灰白阶 + 克制墨绿强调色（TV 端独立色板） */
private val TvLightColorScheme = lightColorScheme(
    primary = Color(0xFF157A66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEEE6),
    onPrimaryContainer = Color(0xFF0B3D33),
    secondary = Color(0xFF58655F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE5DF),
    onSecondaryContainer = Color(0xFF151D19),
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF191D1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191D1B),
    surfaceVariant = Color(0xFFE6EDE9),
    onSurfaceVariant = Color(0xFF4A5550),
    outline = Color(0xFFC3CFC9),
    outlineVariant = Color(0xFFDDE5E1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

/** 墨绿影院 · 深色主题：近黑墨绿底 + 亮墨绿强调，卡片分层（TV 端独立色板） */
private val TvDarkColorScheme = darkColorScheme(
    primary = Color(0xFF74D8C1),
    onPrimary = Color(0xFF04352C),
    primaryContainer = Color(0xFF124E41),
    onPrimaryContainer = Color(0xFFBFEDE1),
    secondary = Color(0xFFBCCBC3),
    onSecondary = Color(0xFF24342D),
    secondaryContainer = Color(0xFF3A4B43),
    onSecondaryContainer = Color(0xFFD6E7DE),
    background = Color(0xFF0D100F),
    onBackground = Color(0xFFE1E7E3),
    surface = Color(0xFF151917),
    onSurface = Color(0xFFE1E7E3),
    surfaceVariant = Color(0xFF252C29),
    onSurfaceVariant = Color(0xFFAFBBB5),
    outline = Color(0xFF3E4743),
    outlineVariant = Color(0xFF2B332F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/** 卡片容器：surface 底 + 圆角 + 统一内边距，随明暗主题取色（TV 端）。 */
@Composable
fun Modifier.tvCardDecoration(): Modifier = this
    .fillMaxWidth()
    .background(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.shapes.medium
    )
    .padding(TvSize.containerPadding)

/** TV 端根主题：注入电视独立色板 / 形状 / 字体。 */
@Composable
fun TvAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TvDarkColorScheme else TvLightColorScheme,
        shapes = AppTvShapes,
        typography = AppTvTypography,
        content = content
    )
}
