package com.hoopoe.zyfuntv.ui.component.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileColor
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileSize
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

/** 星级评分条（手机端，0–10 分制，5 星）；纯 Canvas 绘制，不依赖图片资源 */
@Composable
fun RatingBar(
    rating: Float,
    modifier: Modifier = Modifier,
    size: Dp = MobileSize.smallIcon,
    spaceBetween: Dp = MobileSize.miniMargin
) {
    val filled = (rating / 2f).coerceIn(0f, 5f)
    val solid = floor(filled).toInt()
    val half = round(filled - solid).toInt().coerceIn(0, 5 - solid)
    val empty = 5 - solid - half
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(solid) { Star(size, filled = true, half = false) }
        repeat(half) { Star(size, filled = false, half = true) }
        repeat(empty) { Star(size, filled = false, half = false) }
        val gaps = solid + half + empty - 1
        repeat(gaps) { Spacer(Modifier.width(spaceBetween)) }
    }
}

@Composable
private fun Star(size: Dp, filled: Boolean, half: Boolean) {
    Canvas(modifier = Modifier.size(size)) {
        val px = size.toPx()
        val center = Offset(px / 2f, px / 2f)
        val radius = px / 2f
        val path = starPath(center, radius)
        when {
            filled -> drawPath(path, color = MobileColor.selectedColor)
            half -> drawPath(path, color = MobileColor.selectedColor.copy(alpha = 0.55f))
            else -> drawPath(
                path,
                color = MobileColor.borderColor,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/** 五角星路径（顶点朝上） */
private fun starPath(center: Offset, radius: Float): Path {
    val path = Path()
    val inner = radius * 0.382f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else inner
        val angle = -Math.PI / 2 + i * Math.PI / 5
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
