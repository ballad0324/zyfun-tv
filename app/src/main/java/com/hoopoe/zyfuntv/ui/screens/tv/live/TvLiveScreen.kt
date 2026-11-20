package com.hoopoe.zyfuntv.ui.screens.tv.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hoopoe.zyfuntv.data.local.ChannelEntity
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.remote.model.EpgItem
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.player.ZyfunPlayerView
import com.hoopoe.zyfuntv.player.rememberPlayerController
import com.hoopoe.zyfuntv.ui.component.tv.EmptyHint
import com.hoopoe.zyfuntv.ui.component.tv.FocusRow
import com.hoopoe.zyfuntv.ui.component.tv.PlayerPlaceholder
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize

private const val UNGROUPED = "未分组"

/**
 * TV 直播大屏：左侧折叠分组 + 频道列表，右侧播放器 + 下方节目单（EPG）。
 * 分组点击展开 / 收起，展开后列出当前分组频道；选中频道在右侧播放器起播。
 */
@Composable
fun TvLiveScreen(
    settings: AppSettings,
    repo: AppRepository,
    modifier: Modifier = Modifier
) {
    val channels by repo.channels.collectAsState(emptyList())
    val iptvs by repo.iptvs.collectAsState(emptyList())
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var expandedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var channelQuery by remember { mutableStateOf("") }
    val controller = rememberPlayerController()

    val groups = channels
        .map { it.groupName?.ifEmpty { UNGROUPED } ?: UNGROUPED }
        .distinct()
        .sorted()

    // 频道异步到达后，默认展开首个分组
    LaunchedEffect(groups) {
        if (expandedGroups.isEmpty() && groups.isNotEmpty()) {
            expandedGroups = setOf(groups.first())
            selectedGroup = groups.first()
        }
    }

    LaunchedEffect(controller) {
        val s = repo.currentSettings()
        controller.configure(s.timeoutMs, s.ua, s.proxyType, s.customProxy, s.ignoreSsl)
    }

    // 本地频道为空时，按默认启用的直播源主动拉取一次频道表
    LaunchedEffect(iptvs) {
        if (channels.isEmpty() && iptvs.isNotEmpty()) {
            val defId = settings.defaultIptv
            val src = iptvs.firstOrNull { it.id == defId && it.isActive }
                ?: iptvs.firstOrNull { it.isActive }
                ?: iptvs.first()
            runCatching { repo.refreshChannels(src) }
        }
    }

    LaunchedEffect(selectedChannel?.id) {
        val ch = selectedChannel
        if (ch != null && ch.api.isNotEmpty()) {
            val headers = repo.parseHeaders(ch.headers)
            controller.setSource(ch.api, headers)
        }
    }

    val query = channelQuery.trim()
    val groupMap = channels
        .groupBy { it.groupName?.ifEmpty { UNGROUPED } ?: UNGROUPED }
        .mapValues { entry ->
            entry.value
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .sortedBy { it.name }
        }
        .toSortedMap()

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(TvSize.containerPadding),
        horizontalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
    ) {
        // 左栏：折叠分组 + 频道（收窄，给右侧播放器让出空间）
        Column(
            modifier = Modifier.weight(0.72f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
        ) {
            OutlinedTextField(
                value = channelQuery,
                onValueChange = { channelQuery = it },
                singleLine = true,
                placeholder = { Text("搜索频道", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )

            if (groupMap.isEmpty()) {
                EmptyHint("暂无频道 · 请在「我的」导入直播源", Modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
                ) {
                    groupMap.forEach { (g, chs) ->
                        val expanded = g in expandedGroups
                        val groupActive = chs.any { it.id == selectedChannel?.id }
                        item(key = "group-$g") {
                            FocusRow(
                                title = "${if (expanded) "▼" else "▶"} $g",
                                subtitle = "${chs.size} 个频道",
                                active = groupActive,
                                onClick = {
                                    expandedGroups = if (expanded) expandedGroups - g else expandedGroups + g
                                    selectedGroup = g
                                }
                            )
                        }
                        if (expanded) {
                            items(chs, key = { it.id }) { ch ->
                                FocusRow(
                                    title = ch.name,
                                    subtitle = null,
                                    active = ch.id == selectedChannel?.id,
                                    onClick = { selectedChannel = ch }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 右栏：播放器占主要空间，节目单压缩为底部窄条
        Column(
            modifier = Modifier.weight(1.45f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(percent = 5)),
                contentAlignment = Alignment.Center
            ) {
                val ch = selectedChannel
                if (ch != null) {
                    ZyfunPlayerView(controller = controller, modifier = Modifier.fillMaxSize())
                } else {
                    PlayerPlaceholder("暂无频道 · 选择后播放")
                }
            }
            val current = selectedChannel
            Box(modifier = Modifier.height(168.dp).fillMaxWidth()) {
                if (current != null) {
                    TvChannelInfoPanel(current, settings, repo)
                } else {
                    EmptyHint("暂无节目单")
                }
            }
        }
    }
}

@Composable
private fun TvChannelInfoPanel(ch: ChannelEntity, settings: AppSettings, repo: AppRepository) {
    var epgList by remember { mutableStateOf<List<EpgItem>>(emptyList()) }
    var epgLoading by remember { mutableStateOf(false) }

    val logoUrl = ch.logo?.takeIf { it.isNotEmpty() }
        ?: repo.fillTemplate(settings.logo, ch.name)
    val epgUrl = repo.fillTemplate(settings.epg, ch.name)

    LaunchedEffect(ch.id) {
        epgLoading = true
        epgList = repo.fetchEpg(settings.epg, ch.name)
        epgLoading = false
    }

    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSize.smallMargin)) {
        if (logoUrl.isNotEmpty()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = ch.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp)
            )
        }
        Column {
            Text(ch.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("EPG：$epgUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }

    val nowMinute = currentMinute()
    val current = epgList.firstOrNull { ep ->
        val s = toMinute(ep.start); val e = toMinute(ep.end)
        s != null && e != null && nowMinute >= s && nowMinute < e
    }
    val progress = current?.let {
        val s = toMinute(it.start) ?: 0; val e = toMinute(it.end) ?: s + 1
        ((nowMinute - s).toFloat() / (e - s).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    Box(Modifier.weight(1f)) {
    when {
        epgLoading -> Text("节目单加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        epgList.isEmpty() -> Text("暂无节目单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            current?.let { cur ->
                item(key = "current-${cur.title}") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("● 直播中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text("${cur.start}-${cur.end}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text(cur.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                        }
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            items(epgList.filter { it != current }.take(8), key = { it.title + it.start }) { ep ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${ep.start}-${ep.end}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(ep.title, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    }
    }
}

/** "HH:mm" 转当天分钟数；无法解析返回 null */
private fun toMinute(t: String): Int? {
    val parts = t.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun currentMinute(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}
