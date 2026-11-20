package com.hoopoe.zyfuntv.ui.screens.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.remote.model.RankItem
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.player.ZyfunPlayerView
import com.hoopoe.zyfuntv.player.rememberPlayerController
import com.hoopoe.zyfuntv.ui.component.mobile.CategoryChip
import com.hoopoe.zyfuntv.ui.component.mobile.EmptyHint
import com.hoopoe.zyfuntv.ui.component.mobile.PlayerPlaceholder
import com.hoopoe.zyfuntv.ui.component.mobile.VodPosterCard
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize
import kotlinx.coroutines.launch

/** 榜单分类标签：1 电影 2 电视剧 3 综艺 4 动漫（对齐上游 hot type） */
private val CATEGORY_LABELS = mapOf(1 to "电影", 2 to "电视剧", 3 to "综艺", 4 to "动漫")

/**
 * 热榜榜单屏（手机 / TV 复用）。
 * 顶部可选数据源（豆瓣 / 百度 / 云合 / 海信 / 欢网 / 酷云 / 未来电视 / 夸克）与分类。
 * 点击条目：先按片名跨采集源聚合搜索，命中进详情；未命中则用默认解析源兜底解析播放。
 */
@Composable
fun RankScreen(
    repo: AppRepository,
    onOpenVod: (PendingVod) -> Unit = {},
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val controller = rememberPlayerController()

    val sources = remember { AppRepository.rankSources }
    var sourceKey by remember { mutableStateOf(sources.first().key) }
    var category by remember { mutableStateOf(sources.first().categories.first()) }
    var list by remember { mutableStateOf<List<RankItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var playMsg by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    var sourceMenu by remember { mutableStateOf(false) }

    val currentSource = sources.firstOrNull { it.key == sourceKey } ?: sources.first()

    LaunchedEffect(controller) {
        val s = repo.currentSettings()
        controller.configure(s.timeoutMs, s.ua, s.proxyType, s.customProxy, s.ignoreSsl)
    }

    LaunchedEffect(sourceKey, category) {
        loading = true
        playMsg = null
        list = runCatching { repo.fetchRank(sourceKey, category) }.getOrElse { emptyList() }
        loading = false
    }

    fun selectSource(key: String) {
        val src = sources.firstOrNull { it.key == key } ?: return
        sourceKey = src.key
        if (category !in src.categories) category = src.categories.first()
    }

    fun openItem(item: RankItem) {
        scope.launch {
            runCatching {
                val hits = repo.aggregateSearch(item.name)
                if (hits.isNotEmpty()) {
                    val h = hits.first()
                    onOpenVod(PendingVod(h.siteId, h.vod.id, h.vod.name, h.vod.pic))
                    return@launch
                }
                playMsg = "采集源未命中，尝试解析源…"
                val analyze = repo.fallbackAnalyze()
                if (analyze == null) {
                    playMsg = "采集源未命中，且暂无可用解析源"
                    return@launch
                }
                val result = repo.resolveWithAnalyze(analyze, item.name)
                if (result.url.isNotEmpty() && !result.needParse) {
                    controller.setSource(result.url, result.headers)
                    playingTitle = item.name
                    playMsg = null
                } else {
                    playMsg = result.message ?: "未找到可播源"
                }
            }.onFailure { e ->
                playMsg = "播放失败：${e.message ?: "未知错误"}"
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 兜底解析播放器：有起播地址则展示，否则主题占位；16:9 等比放大居中
        val playerBlock: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 手机端 16:9 等比；TV 宽屏改用紧凑限高，避免播放器把海报网格挤出屏幕
                    .then(
                        if (isPhone) Modifier.aspectRatio(16f / 9f)
                        else Modifier.height(TvSize.playerHeight)
                    )
                    .clip(RoundedCornerShape(percent = 5))
            ) {
                if (playingTitle.isNotEmpty()) {
                    ZyfunPlayerView(controller = controller, modifier = Modifier.fillMaxSize())
                } else {
                    PlayerPlaceholder("榜单点击后按片名搜索，无源走解析兜底", modifier = Modifier.fillMaxSize())
                }
            }
        }

        // TV 端：数据源折叠为单个下拉标签（占一位），点击弹出全部源；右侧横排当前源分类
        val tvHeader: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    CategoryChip(
                        label = "${currentSource.label} ▾",
                        active = true,
                        onClick = { sourceMenu = true }
                    )
                    DropdownMenu(
                        expanded = sourceMenu,
                        onDismissRequest = { sourceMenu = false }
                    ) {
                        sources.forEach { src ->
                            DropdownMenuItem(
                                text = { Text(src.label) },
                                leadingIcon = {
                                    if (src.key == sourceKey) {
                                        Text("●", color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    selectSource(src.key)
                                    sourceMenu = false
                                }
                            )
                        }
                    }
                }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentSource.categories.forEach { cat ->
                        item {
                            CategoryChip(
                                label = CATEGORY_LABELS[cat] ?: cat.toString(),
                                active = cat == category,
                                onClick = { category = cat }
                            )
                        }
                    }
                }
            }
        }

        // 手机端播放器置顶；TV 端保持 源下拉+分类 → 播放器 顺序；两端源均折叠为单个下拉标签
        if (isPhone) {
            playerBlock()
            tvHeader()
        } else {
            tvHeader()
            playerBlock()
        }

        playMsg?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyHint("榜单加载失败或暂无数据，可稍后重试")
            }
            else -> LazyVerticalGrid(
                columns = if (isPhone) GridCells.Fixed(3) else GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxSize()
            ) {
                items(list, key = { "${it.id}#${it.rank}#${it.name}" }) { item ->
                    val vod = CmsVod(
                        id = item.id,
                        name = "#${item.rank} ${item.name}",
                        pic = item.pic,
                        remarks = item.hot,
                        blurb = item.remarks
                    )
                    VodPosterCard(vod = vod, onClick = { openItem(item) })
                }
            }
        }
    }
}
