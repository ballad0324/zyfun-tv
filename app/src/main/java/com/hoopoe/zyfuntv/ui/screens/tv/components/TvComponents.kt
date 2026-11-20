package com.hoopoe.zyfuntv.ui.screens.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.ui.component.tv.VodPosterCard
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize
import androidx.compose.runtime.snapshotFlow

/**
 * TV 大屏通用可聚焦条目：选中走主色浅底，聚焦描边高亮，D-pad / 键盘可达。
 * 用于分类侧栏、线路 Tab、设置分类、浮层按钮等。
 */
@Composable
fun TvFocusItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    fontSize: TextUnit? = null
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .background(bg, RoundedCornerShape(TvSize.middleRadius))
            .border(
                width = if (focused || active) 1.5.dp else 1.dp,
                color = if (focused || active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(TvSize.middleRadius)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leading != null) leading()
        Text(
            text = label,
            color = fg,
            style = if (fontSize != null) {
                MaterialTheme.typography.titleMedium.copy(fontSize = fontSize)
            } else {
                MaterialTheme.typography.titleMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * TV 海报网格：自适应列数，复用 [VodPosterCard]，焦点卡片自带描边高亮。
 * [onLoadMore] 在滚动接近底部时触发追加加载，[loadingMore] 显示底部加载态。
 */
@Composable
fun TvPosterGrid(
    vods: List<CmsVod>,
    onVodClick: (CmsVod) -> Unit,
    modifier: Modifier = Modifier,
    minCardWidth: androidx.compose.ui.unit.Dp = 150.dp,
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        if (onLoadMore == null) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = gridState.layoutInfo.totalItemsCount
                if (total > 0 && last != null && last >= total - 3) onLoadMore()
            }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minCardWidth),
        horizontalArrangement = Arrangement.spacedBy(TvSize.smallMargin),
        verticalArrangement = Arrangement.spacedBy(TvSize.smallMargin),
        modifier = modifier.fillMaxSize()
    ) {
        items(vods, key = { it.id }) { vod ->
            VodPosterCard(vod = vod, onClick = { onVodClick(vod) })
        }
        if (loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(TvSize.smallMargin), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
