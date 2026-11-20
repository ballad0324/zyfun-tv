package com.hoopoe.zyfuntv.ui.screens.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.R
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.ui.component.tv.AppLogo
import com.hoopoe.zyfuntv.ui.component.tv.NavBarIcon
import com.hoopoe.zyfuntv.ui.nav.Dest
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize

/**
 * TV 端左侧竖排导航栏：品牌区 + 竖排图标文字导航。
 * 条目按内容定高、顶部排列，选中项主色浅底 + 左侧主色竖条，聚焦描边高亮，遥控器 / 键盘可达。
 * 端切换与设置入口放在顶部全局快捷行，不在侧栏重复展示。
 */
@Composable
fun TvSideBar(
    items: List<Dest>,
    current: Dest,
    onSelect: (Dest) -> Unit,
    sites: List<SiteEntity> = emptyList(),
    selectedSite: SiteEntity? = null,
    onSiteChange: (SiteEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var siteMenu by remember { mutableStateOf(false) }
    var logoFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(TvSize.sideBarWidth)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = TvSize.miniMargin, vertical = TvSize.containerPadding),
        verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
    ) {
        // 品牌区：点击 Logo 弹出视频源切换列表（不显源名文字）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = TvSize.miniMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
        ) {
            Box {
                AppLogo(
                    size = 36.dp,
                    modifier = Modifier
                        .focusable()
                        .onFocusChanged { logoFocused = it.isFocused }
                        .border(
                            width = if (logoFocused) 1.5.dp else 0.dp,
                            color = if (logoFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(TvSize.middleRadius)
                        )
                        .clickable { siteMenu = true }
                )
                DropdownMenu(
                    expanded = siteMenu,
                    onDismissRequest = { siteMenu = false }
                ) {
                    if (sites.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂无视频源") },
                            onClick = { siteMenu = false }
                        )
                    } else {
                        sites.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                leadingIcon = {
                                    if (s.id == selectedSite?.id) {
                                        Text("●", color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    onSiteChange(s)
                                    siteMenu = false
                                }
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 导航条目：按内容定高、顶部排列，避免横屏高度紧张时被 weight 均分压扁、文字被裁
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TvSize.sideBarItemSpacing)
        ) {
            items.forEach { dest ->
                SideBarItem(
                    dest = dest,
                    active = dest == current,
                    onClick = { onSelect(dest) }
                )
            }
        }
    }
}

@Composable
private fun SideBarItem(
    dest: Dest,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = when {
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(TvSize.middleRadius))
            .border(
                width = if (focused || active) 1.5.dp else 0.dp,
                color = if (focused || active) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(TvSize.middleRadius)
            )
            .clickable { onClick() }
            .padding(horizontal = TvSize.miniMargin, vertical = TvSize.sideBarItemVPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
    ) {
        Box(
            Modifier
                .width(TvSize.lineWidth)
                .height(TvSize.sideBarIcon)
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
        NavBarIcon(dest = dest, tint = fg, modifier = Modifier.size(TvSize.sideBarIcon))
        Text(
            text = stringResource(dest.titleRes),
            color = fg,
            fontSize = TvSize.navLabelFontSize,
            lineHeight = TvSize.navLabelLineHeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
