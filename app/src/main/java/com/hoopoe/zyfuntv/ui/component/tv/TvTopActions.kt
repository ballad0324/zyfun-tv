package com.hoopoe.zyfuntv.ui.component.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.BuildConfig
import com.hoopoe.zyfuntv.ui.nav.Dest
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize
import kotlin.math.cos
import kotlin.math.sin

/**
 * TV 首页顶部快捷操作行：右侧一排三个聚焦可达图标——
 * 设置入口、手机 / 电视端切换、明暗主题切换。
 */
@Composable
fun TvTopActionRow(
    onOpenSetting: () -> Unit,
    onSwitchToPhone: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
    ) {
        Box(Modifier.weight(1f))
        ActionIconButton(onClick = onOpenSetting, description = "设置") { tint ->
            NavBarIcon(Dest.Setting, tint = tint, modifier = Modifier.size(TvSize.smallIcon))
        }
        // tv 变体已强制锁定电视端，隐藏切手机入口；universal 保留切换
        if (BuildConfig.FORCED_UI_MODE.trim().lowercase() != "tv") {
            ActionIconButton(onClick = onSwitchToPhone, description = "切换手机端") { tint ->
                PhoneGlyph(tint = tint, modifier = Modifier.size(TvSize.smallIcon))
            }
        }
        ActionIconButton(onClick = onToggleTheme, description = "切换明暗主题") { tint ->
            if (darkTheme) SunGlyph(tint, Modifier.size(TvSize.smallIcon))
            else MoonGlyph(tint, Modifier.size(TvSize.smallIcon))
        }
    }
}

/** 顶部小图标按钮：聚焦描边高亮，遥控器 / 键盘可达 */
@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    description: String,
    icon: @Composable (Color) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val tint = if (focused) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        icon(tint)
    }
}

/** 手机机身图标：竖屏机身 + 听筒线，提示可切换到手机端 */
@Composable
private fun PhoneGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.width / 24f
        val stroke = 1.7f * u
        drawRect(
            color = tint,
            topLeft = Offset(8f * u, 3f * u),
            size = Size(8f * u, 18f * u),
            style = Stroke(stroke)
        )
        drawLine(
            tint,
            Offset(10.5f * u, 5.5f * u),
            Offset(13.5f * u, 5.5f * u),
            stroke,
            StrokeCap.Round
        )
    }
}

/** 太阳图标：中心圆 + 八道射线，用于暗色态提示可切回明亮 */
@Composable
private fun SunGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.width / 24f
        val stroke = 1.7f * u
        val cap = StrokeCap.Round
        val cx = 12f * u
        val cy = 12f * u
        drawCircle(color = tint, radius = 4.4f * u, center = Offset(cx, cy), style = Stroke(stroke))
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            val r1 = 6.6f * u
            val r2 = 9f * u
            drawLine(
                tint,
                Offset(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat()),
                Offset(cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat()),
                stroke, cap
            )
        }
    }
}

/** 月亮（月牙）图标：外缘弧 + 内缘弧围成，用于明亮态提示可切到黑暗 */
@Composable
private fun MoonGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.width / 24f
        val moon = Path().apply {
            moveTo(15.5f * u, 4.2f * u)
            arcTo(Rect(4f * u, 4f * u, 20f * u, 20f * u), -55f, -290f, false)
            arcTo(Rect(7.6f * u, 6.4f * u, 19.2f * u, 17.6f * u), 58f, 252f, true)
            close()
        }
        drawPath(moon, tint)
    }
}
