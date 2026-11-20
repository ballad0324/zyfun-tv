package com.hoopoe.zyfuntv.ui.component.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hoopoe.zyfuntv.ui.nav.Dest
import kotlin.math.cos
import kotlin.math.sin

/**
 * 手机底栏线性图标（手机端），纯 Compose Canvas 自绘，不依赖 material-icons / XML 资源。
 * 24dp 网格坐标系，[tint] 为线条 / 填充色。
 */
@Composable
fun NavBarIcon(dest: Dest, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.width / 24f
        val stroke = 1.7f * u
        val cap = StrokeCap.Round
        when (dest) {
            Dest.Home -> {
                val roof = Path().apply {
                    moveTo(3.5f * u, 12.5f * u)
                    lineTo(12f * u, 4.5f * u)
                    lineTo(20.5f * u, 12.5f * u)
                    close()
                }
                drawPath(roof, tint, style = Stroke(stroke))
                drawRect(
                    color = tint,
                    topLeft = Offset(6.5f * u, 12f * u),
                    size = Size(11f * u, 8.5f * u),
                    style = Stroke(stroke)
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(10.5f * u, 15.5f * u),
                    size = Size(3f * u, 5f * u)
                )
            }
            Dest.Film -> {
                drawRect(
                    color = tint,
                    topLeft = Offset(3f * u, 6f * u),
                    size = Size(18f * u, 12f * u),
                    style = Stroke(stroke)
                )
                drawLine(tint, Offset(12f * u, 18f * u), Offset(12f * u, 21f * u), stroke, cap)
                drawLine(tint, Offset(9f * u, 21f * u), Offset(15f * u, 21f * u), stroke, cap)
            }
            Dest.Live -> {
                drawRect(
                    color = tint,
                    topLeft = Offset(3f * u, 5f * u),
                    size = Size(18f * u, 14f * u),
                    style = Stroke(stroke)
                )
                val triangle = Path().apply {
                    moveTo(9.5f * u, 9f * u)
                    lineTo(9.5f * u, 15f * u)
                    lineTo(14.5f * u, 12f * u)
                    close()
                }
                drawPath(triangle, tint)
            }
            Dest.Rank -> {
                drawRect(
                    color = tint,
                    topLeft = Offset(5f * u, 13f * u),
                    size = Size(4f * u, 6f * u),
                    style = Stroke(stroke)
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(10f * u, 9f * u),
                    size = Size(4f * u, 10f * u),
                    style = Stroke(stroke)
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(15f * u, 5f * u),
                    size = Size(4f * u, 14f * u),
                    style = Stroke(stroke)
                )
                drawLine(tint, Offset(3.5f * u, 20f * u), Offset(20.5f * u, 20f * u), stroke, cap)
            }
            Dest.Parse -> {
                val bolt = Path().apply {
                    moveTo(13.5f * u, 3f * u)
                    lineTo(8.5f * u, 13f * u)
                    lineTo(12f * u, 13f * u)
                    lineTo(10.5f * u, 21f * u)
                    lineTo(15.5f * u, 11f * u)
                    lineTo(12f * u, 11f * u)
                    close()
                }
                drawPath(bolt, tint)
            }
            Dest.Cloud -> {
                drawCircle(tint, radius = 3.6f * u, center = Offset(9f * u, 11.5f * u), style = Stroke(stroke))
                drawCircle(tint, radius = 4.6f * u, center = Offset(15f * u, 10.5f * u), style = Stroke(stroke))
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4f * u, 12f * u),
                    size = Size(16f * u, 6f * u),
                    cornerRadius = CornerRadius(3f * u, 3f * u),
                    style = Stroke(stroke)
                )
            }
            Dest.Moment -> {
                drawCircle(color = tint, radius = 8f * u, center = Offset(12f * u, 12f * u), style = Stroke(stroke))
                drawLine(tint, Offset(12f * u, 12f * u), Offset(12f * u, 8f * u), stroke, cap)
                drawLine(tint, Offset(12f * u, 12f * u), Offset(15f * u, 13.5f * u), stroke, cap)
            }
            Dest.Setting -> {
                val cx = 12f * u
                val cy = 12f * u
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val r1 = 8.0f * u
                    val r2 = 10.2f * u
                    drawLine(
                        tint,
                        Offset(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat()),
                        Offset(cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat()),
                        stroke, cap
                    )
                }
                drawCircle(color = tint, radius = 6.6f * u, center = Offset(cx, cy), style = Stroke(stroke))
                drawCircle(color = tint, radius = 2.3f * u, center = Offset(cx, cy))
            }
            Dest.Lab -> {
                drawRect(
                    color = tint,
                    topLeft = Offset(8f * u, 3f * u),
                    size = Size(8f * u, 18f * u),
                    style = Stroke(stroke)
                )
            }
        }
    }
}

/** 放大镜搜索图标（手机端）：纯 Compose 自绘，24dp 网格 */
@Composable
fun SearchIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.width / 24f
        val stroke = 1.7f * u
        val cap = StrokeCap.Round
        drawCircle(
            color = tint,
            radius = 6.2f * u,
            center = Offset(10.5f * u, 10.5f * u),
            style = Stroke(stroke)
        )
        drawLine(tint, Offset(15.2f * u, 15.2f * u), Offset(19.5f * u, 19.5f * u), stroke, cap)
    }
}

/** 关闭叉号图标（手机端）：纯 Compose 自绘，24dp 网格 */
@Composable
fun CloseIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.width / 24f
        val stroke = 1.7f * u
        val cap = StrokeCap.Round
        drawLine(tint, Offset(7f * u, 7f * u), Offset(17f * u, 17f * u), stroke, cap)
        drawLine(tint, Offset(17f * u, 7f * u), Offset(7f * u, 17f * u), stroke, cap)
    }
}
