package com.hoopoe.zyfuntv.ui.screens.tv.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize

/**
 * TV 首页占位屏：暂定页面，顶部仅放置一个全局搜索框，搜索结果 / 推荐内容后续接入。
 */
@Composable
fun TvIndexScreen(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TvSize.containerPadding),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            focused = focused,
            onFocusChange = { focused = it },
            modifier = Modifier.fillMaxWidth(0.6f)
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(TvSize.inputHeight)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(TvSize.middleRadius)
            )
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(TvSize.middleRadius)
            )
            .padding(horizontal = TvSize.smallMargin),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
        ) {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable()
                        .onFocusChanged { onFocusChange(it.isFocused) }
                )
                if (query.isEmpty()) {
                    Text(
                        text = "搜索影片",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SearchGlyph(
                tint = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(TvSize.sideBarIcon)
            )
        }
    }
}

/** 放大镜图标：圆环 + 手柄，纯 Compose 自绘 */
@Composable
private fun SearchGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.width / 24f
        val stroke = 1.7f * u
        drawCircle(
            color = tint,
            radius = 6.2f * u,
            center = Offset(10f * u, 10f * u),
            style = Stroke(stroke)
        )
        drawLine(
            tint,
            Offset(14.6f * u, 14.6f * u),
            Offset(19.5f * u, 19.5f * u),
            stroke,
            StrokeCap.Round
        )
    }
}
