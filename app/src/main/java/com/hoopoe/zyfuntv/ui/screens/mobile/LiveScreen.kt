package com.hoopoe.zyfuntv.ui.screens.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.hoopoe.zyfuntv.ui.component.mobile.CategoryChip
import com.hoopoe.zyfuntv.ui.component.mobile.EmptyHint
import com.hoopoe.zyfuntv.ui.component.mobile.FocusRow
import com.hoopoe.zyfuntv.ui.component.mobile.PlayerPlaceholder

private const val UNGROUPED = "未分组"

@Composable
fun LiveScreen(settings: AppSettings, repo: AppRepository, isPhone: Boolean = false, modifier: Modifier = Modifier) {
    val channels by repo.channels.collectAsState(emptyList())
    val iptvs by repo.iptvs.collectAsState(emptyList())
    val groups = channels
        .map { it.groupName?.ifEmpty { UNGROUPED } ?: UNGROUPED }
        .distinct()
        .sorted()
    var selectedGroup by remember { mutableStateOf(groups.firstOrNull()) }
    var selectedChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    // 频道异步到达后，若当前选中分组为空或已不存在，回退选中首组，避免首屏误显「暂无频道」
    LaunchedEffect(groups) {
        if (selectedGroup == null || selectedGroup !in groups) {
            selectedGroup = groups.firstOrNull()
        }
    }
    val controller = rememberPlayerController()

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

    val groupChannels = channels.filter {
        (it.groupName?.ifEmpty { UNGROUPED } ?: UNGROUPED) == selectedGroup
    }

    if (isPhone) {
        Column(
            modifier = modifier.then(Modifier.fillMaxSize().padding(12.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(percent = 5))
            ) {
                val ch = selectedChannel
                if (ch != null) {
                    ZyfunPlayerView(controller = controller, modifier = Modifier.fillMaxSize())
                } else {
                    PlayerPlaceholder("暂无频道 · 选择后播放")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { g ->
                    CategoryChip(
                        label = g,
                        active = g == selectedGroup,
                        onClick = { selectedGroup = g; selectedChannel = null }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (groupChannels.isEmpty()) {
                    EmptyHint("暂无频道 · 请在「我的」导入直播源")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groupChannels, key = { it.id }) { ch ->
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

            val current = selectedChannel
            if (current != null) {
                ChannelInfoPanel(current, settings, repo)
            }
        }
    } else {
        Row(
            modifier = modifier.then(Modifier.fillMaxSize().padding(16.dp)),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.width(170.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("分组", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                if (groups.isEmpty()) {
                    EmptyHint("无分组")
                } else {
                    groups.forEach { g ->
                        FocusRow(
                            title = g,
                            subtitle = null,
                            active = g == selectedGroup,
                            onClick = { selectedGroup = g; selectedChannel = null }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("频道", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                if (groupChannels.isEmpty()) {
                    EmptyHint("暂无频道 · 请在「我的」导入直播源")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groupChannels, key = { it.id }) { ch ->
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

            Column(
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("节目信息", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(percent = 5))
                ) {
                    val ch = selectedChannel
                    if (ch != null) {
                        ZyfunPlayerView(controller = controller, modifier = Modifier.fillMaxSize())
                    } else {
                        PlayerPlaceholder("暂无频道 · 选择后播放")
                    }
                }
                val current = selectedChannel
                if (current != null) {
                    ChannelInfoPanel(current, settings, repo)
                } else {
                    EmptyHint("暂无节目信息")
                }
            }
        }
    }
}

@Composable
private fun ChannelInfoPanel(ch: ChannelEntity, settings: AppSettings, repo: AppRepository) {
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

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

    when {
        epgLoading -> Text("节目单加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        epgList.isEmpty() -> Text("暂无节目单（XMLTV 整单解析待接入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(epgList.take(8), key = { it.title + it.start }) { ep ->
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
