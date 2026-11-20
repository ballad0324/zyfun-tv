package com.hoopoe.zyfuntv.ui.screens.tv.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsEpisode
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.player.ZyfunPlayerView
import com.hoopoe.zyfuntv.player.rememberPlayerController
import com.hoopoe.zyfuntv.ui.screens.tv.player.TvPlayerScreen
import com.hoopoe.zyfuntv.ui.component.tv.EmptyHint
import com.hoopoe.zyfuntv.ui.component.tv.EpisodeChip
import com.hoopoe.zyfuntv.ui.component.tv.RatingBar
import com.hoopoe.zyfuntv.ui.component.tv.VodPosterCard
import com.hoopoe.zyfuntv.ui.screens.tv.components.TvFocusItem
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** TV 详情大屏：左大海报 + 右信息/播放器，下方线路 Tab + 选集网格 */
@Composable
fun TvDetailScreen(
    site: SiteEntity,
    vod: CmsVod,
    repo: AppRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val controller = rememberPlayerController()
    var detail by remember { mutableStateOf<CmsDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(0) }
    var playingTitle by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    var playMsg by remember { mutableStateOf<String?>(null) }
    var starred by remember { mutableStateOf(false) }
    var historyIndex by remember { mutableStateOf<String?>(null) }
    var historyPosSec by remember { mutableStateOf(0.0) }
    var currentEpIndex by remember { mutableStateOf(-1) }
    var lastUrl by remember { mutableStateOf("") }
    var lastHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var recommends by remember { mutableStateOf<List<CmsVod>>(emptyList()) }
    var contentExpanded by remember { mutableStateOf(false) }

    val relKey = site.api ?: site.id

    fun currentEpisodes(): List<CmsEpisode> =
        detail?.sources?.getOrNull(selectedSource)?.episodes.orEmpty()

    fun playEpisode(ep: CmsEpisode, resumeSec: Double = 0.0) {
        scope.launch {
            val sourceName = detail?.sources?.getOrNull(selectedSource)?.name
            val result = repo.resolvePlay(site, ep.url, sourceName)
            playMsg = result.message
            if (result.url.isNotEmpty() && !result.needParse) {
                controller.setSource(result.url, result.headers)
                playingTitle = ep.title
                lastUrl = result.url
                lastHeaders = result.headers
                currentEpIndex = currentEpisodes().indexOfFirst { it.url == ep.url }
                if (resumeSec > 1.0) controller.seekTo((resumeSec * 1000).toLong())
                val indexKey = sourceName?.let { "$it##${ep.title}" } ?: ep.title
                repo.saveHistory(relKey, vod.id, detail?.pic, vod.name, indexKey)
            }
        }
    }

    fun moveEpisode(delta: Int) {
        val eps = currentEpisodes()
        if (eps.isEmpty()) return
        val target = (currentEpIndex + delta).coerceIn(0, eps.size - 1)
        if (target == currentEpIndex) return
        playEpisode(eps[target])
    }

    LaunchedEffect(controller) {
        val s = repo.currentSettings()
        controller.configure(s.timeoutMs, s.ua, s.proxyType, s.customProxy, s.ignoreSsl)
    }

    LaunchedEffect(vod.id) {
        loading = true
        message = "详情加载中…"
        detail = repo.fetchDetail(site, vod.id)
        starred = repo.isStarred(vod.id, relKey)
        val h = repo.findHistory(vod.id, relKey)
        historyIndex = h?.videoIndex
        historyPosSec = h?.watchTime ?: 0.0
        loading = false
        message = if (detail == null) "详情获取失败或该源无播放线路" else ""
        detail?.let { d ->
            recommends = repo.relatedRecommend(site, d).filter { it.id != vod.id }
        }
    }

    LaunchedEffect(playingTitle) {
        if (playingTitle.isEmpty()) return@LaunchedEffect
        while (isActive) {
            delay(5000)
            val dur = controller.player.duration.coerceAtLeast(0) / 1000.0
            if (dur > 0) {
                val pos = controller.player.currentPosition / 1000.0
                repo.updateProgress(vod.id, relKey, pos, dur)
            }
        }
    }

    if (fullscreen) {
        TvPlayerScreen(
            controller = controller,
            title = playingTitle.ifEmpty { vod.name },
            onBack = { fullscreen = false },
            modifier = modifier,
            playUrl = lastUrl,
            headers = lastHeaders,
            externalPkg = repo.currentSettings().externalPlayerPkg.ifBlank { null },
            onPrevEpisode = { moveEpisode(-1) },
            onNextEpisode = { moveEpisode(1) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TvSize.containerPadding),
        verticalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = vod.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = TvSize.miniMargin)
            )
            TextButton(onClick = {
                scope.launch {
                    starred = repo.toggleStar(relKey, vod.id, detail?.pic, vod.name, vod.remarks)
                }
            }) {
                Text(if (starred) "★ 已收藏" else "☆ 收藏")
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
        ) {
            // 大海报
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(TvSize.middleRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (vod.pic.isNotEmpty()) {
                    AsyncImage(
                        model = vod.pic,
                        contentDescription = vod.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无图", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
            ) {
                when {
                    loading -> EmptyHint(message.ifEmpty { "加载中…" })
                    message.isNotEmpty() -> EmptyHint(message)
                    else -> detail?.let { TvDetailInfo(it, contentExpanded) { contentExpanded = !contentExpanded } }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (playingTitle.isNotEmpty()) {
                        ZyfunPlayerView(controller = controller, modifier = Modifier.fillMaxSize())
                        TextButton(
                            onClick = { fullscreen = true },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) { Text("全屏") }
                    } else {
                        EmptyHint("选择选集开始播放")
                    }
                }

                playMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // 线路 + 选集
        when {
            loading -> EmptyHint(message.ifEmpty { "加载中…" })
            message.isNotEmpty() -> EmptyHint(message)
            else -> {
                val d = detail
                if (d != null) {
                    if (d.sources.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(TvSize.miniMargin)) {
                            items(d.sources.size) { idx ->
                                TvFocusItem(
                                    label = d.sources[idx].name,
                                    active = idx == selectedSource,
                                    onClick = { selectedSource = idx }
                                )
                            }
                        }
                        val episodes = d.sources.getOrNull(selectedSource)?.episodes.orEmpty()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(130.dp),
                            horizontalArrangement = Arrangement.spacedBy(TvSize.miniMargin),
                            verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin),
                            modifier = Modifier.height(190.dp)
                        ) {
                            items(episodes, key = { it.url }) { ep ->
                                val resume = if (historyMatches(historyIndex, ep.title)) historyPosSec else 0.0
                                EpisodeChip(
                                    title = ep.title + if (resume > 1.0) "（续播 ${formatSec(resume)}）" else "",
                                    subtitle = if (playingTitle == ep.title) "播放中" else null,
                                    active = playingTitle == ep.title,
                                    onClick = { playEpisode(ep, resume) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        EmptyHint("暂无播放线路")
                    }
                }
            }
        }

        if (recommends.isNotEmpty()) {
            Text(
                "相关推荐",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(TvSize.miniMargin)) {
                items(recommends.size) { idx ->
                    VodPosterCard(
                        vod = recommends[idx],
                        onClick = {},
                        modifier = Modifier.width(110.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDetailInfo(d: CmsDetail, expanded: Boolean = false, onToggleExpand: () -> Unit = {}) {
    val score = d.score.toFloatOrNull() ?: 0f
    val meta = listOf(d.year, d.area, d.lang, d.klass, d.state)
        .filter { it.isNotEmpty() }.joinToString(" · ")
    val cleanContent = d.content.replace(Regex("<[^>]*>"), "").trim()

    Column(verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)) {
        if (score > 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
            ) {
                RatingBar(rating = score)
                Text(d.score, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        if (d.director.isNotEmpty()) {
            Text("导演：${d.director}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        if (d.actor.isNotEmpty()) {
            Text(
                "主演：${d.actor}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (cleanContent.isNotEmpty()) {
            Text(
                if (expanded) cleanContent else cleanContent.take(200),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            if (cleanContent.length > 200) {
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) "收起" else "展开简介")
                }
            }
        }
    }
}

private fun formatSec(sec: Double): String {
    val s = sec.toLong()
    return "%02d:%02d".format(s / 60, s % 60)
}

private fun historyMatches(saved: String?, title: String): Boolean {
    if (saved.isNullOrEmpty()) return false
    if (saved == title) return true
    return saved.substringAfter("##", saved) == title
}
