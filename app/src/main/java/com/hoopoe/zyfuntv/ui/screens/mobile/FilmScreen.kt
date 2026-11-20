package com.hoopoe.zyfuntv.ui.screens.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.hoopoe.zyfuntv.BuildConfig
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.core.model.SiteType
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsEpisode
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.player.ZyfunPlayerView
import com.hoopoe.zyfuntv.player.rememberPlayerController
import com.hoopoe.zyfuntv.ui.component.mobile.AppLogo
import com.hoopoe.zyfuntv.ui.component.mobile.CategoryChip
import com.hoopoe.zyfuntv.ui.component.mobile.EmptyHint
import com.hoopoe.zyfuntv.ui.component.mobile.EpisodeChip
import com.hoopoe.zyfuntv.ui.component.mobile.NavBarIcon
import com.hoopoe.zyfuntv.ui.component.mobile.RatingBar
import com.hoopoe.zyfuntv.ui.component.mobile.CloseIcon
import com.hoopoe.zyfuntv.ui.component.mobile.SearchIcon
import com.hoopoe.zyfuntv.ui.component.mobile.VodPosterCard
import com.hoopoe.zyfuntv.ui.nav.Dest
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun FilmScreen(
    repo: AppRepository,
    initialVod: PendingVod? = null,
    onConsumeVod: () -> Unit = {},
    isPhone: Boolean = false,
    modifier: Modifier = Modifier,
    onSwitchToTv: () -> Unit = {}
) {
    val sites by repo.sites.collectAsState(emptyList())
    // 影视源下拉与选源排除 Alist 网盘，网盘仅在网盘专区/网盘配置中展示
    val filmSites = sites.filter { it.type != SiteType.T3_ALIST }
    var selectedSite by remember { mutableStateOf<SiteEntity?>(null) }
    var selectedVod by remember { mutableStateOf<CmsVod?>(null) }

    LaunchedEffect(initialVod, sites) {
        val pending = initialVod ?: return@LaunchedEffect
        val site = sites.firstOrNull {
            it.api == pending.siteSource || it.id == pending.siteSource
        } ?: return@LaunchedEffect
        selectedSite = site
        selectedVod = CmsVod(
            id = pending.videoId,
            name = pending.name,
            pic = pending.image.orEmpty(),
            remarks = "",
            blurb = ""
        )
        onConsumeVod()
    }

    val currentSite = selectedSite
    val currentVod = selectedVod

    // 返回键：详情页先退回影片列表
    BackHandler(enabled = currentVod != null) { selectedVod = null }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            currentSite != null && currentVod != null -> VodDetailScreen(
                site = currentSite,
                vod = currentVod,
                repo = repo,
                isPhone = isPhone,
                modifier = Modifier.fillMaxSize(),
                onBack = { selectedVod = null }
            )
            else -> HomeGridScreen(
                sites = filmSites,
                selectedSite = currentSite,
                onSiteChange = { selectedSite = it },
                repo = repo,
                isPhone = isPhone,
                modifier = Modifier.fillMaxSize(),
                onSwitchToTv = onSwitchToTv,
                onVodClick = { selectedVod = it }
            )
        }
    }
}

@Composable
private fun HomeGridScreen(
    sites: List<SiteEntity>,
    selectedSite: SiteEntity?,
    onSiteChange: (SiteEntity) -> Unit,
    repo: AppRepository,
    isPhone: Boolean,
    modifier: Modifier = Modifier,
    onSwitchToTv: () -> Unit = {},
    onVodClick: (CmsVod) -> Unit
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CmsCategory>>(emptyList()) }
    var selectedTid by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var aggregateMode by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var vods by remember { mutableStateOf<List<CmsVod>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var pageCount by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(sites) {
        if (selectedSite == null && sites.isNotEmpty()) {
            val defId = repo.currentSettings().defaultSite
            onSiteChange(
                sites.firstOrNull { it.id == defId && it.isActive }
                    ?: sites.firstOrNull { it.isActive }
                    ?: sites.first()
            )
        }
    }

    val site = selectedSite

    fun loadCategory(tid: String, p: Int) {
        val current = site ?: return
        scope.launch {
            loading = true
            message = "加载中…"
            val result = repo.fetchVodList(current, tid, p)
            vods = result.list
            page = result.page.coerceAtLeast(1)
            pageCount = result.pageCount.coerceAtLeast(1)
            total = result.total
            message = if (result.list.isEmpty()) "暂无影片" else ""
            loading = false
        }
    }

    fun doSearch(wd: String, p: Int) {
        if (wd.isBlank()) return
        scope.launch {
            loading = true
            message = "全站搜索中…"
            aggregateMode = true
            val agg = repo.aggregateSearch(wd)
            vods = agg.map { item ->
                item.vod.copy(remarks = listOf(item.vod.remarks, item.siteName)
                    .filter { it.isNotEmpty() }.joinToString(" · "))
            }
            page = 1
            pageCount = 1
            total = agg.size
            message = if (agg.isEmpty()) "未找到相关影片" else ""
            loading = false
        }
    }

    fun refreshSuggest(wd: String) {
        scope.launch {
            suggestions = if (wd.isBlank()) emptyList() else repo.searchSuggest(wd)
        }
    }

    fun doSearchNow() {
        if (query.isBlank()) return
        searching = true
        doSearch(query, 1)
    }

    fun resetCategory() {
        searching = false
        aggregateMode = false
        query = ""
        suggestions = emptyList()
        loadCategory(selectedTid, 1)
    }

    LaunchedEffect(site?.id) {
        val current = site ?: return@LaunchedEffect
        loading = true
        message = "分类加载中…"
        categories = repo.fetchCategories(current)
        if (categories.isNotEmpty()) {
            selectedTid = categories.first().typeId
            loadCategory(selectedTid, 1)
        } else {
            message = "该站点暂不支持或无分类"
            loading = false
        }
    }

    if (sites.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint("暂无影视站点 · 请在「我的」导入站点配置")
        }
        return
    }
    if (site == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint("请选择站点")
        }
        return
    }

    BoxWithConstraints(modifier = modifier.then(Modifier.fillMaxSize())) {
        val maxW = maxWidth.value
        val phoneCols = (maxW / 130f).toInt().coerceIn(2, 4)
        val categoryRailW = (maxW * 0.18f).coerceIn(130f, 180f).dp
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var siteMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                AppLogo(
                    size = 30.dp,
                    modifier = Modifier.clickable { siteMenu = true }
                )
                DropdownMenu(
                    expanded = siteMenu,
                    onDismissRequest = { siteMenu = false }
                ) {
                    sites.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.name) },
                            onClick = {
                                onSiteChange(s)
                                siteMenu = false
                            }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; refreshSuggest(it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearchNow() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "搜索影片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.clickable { if (searching) resetCategory() else doSearchNow() }) {
                        if (searching) {
                            CloseIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        } else {
                            SearchIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                DropdownMenu(
                    expanded = suggestions.isNotEmpty() && !searching,
                    onDismissRequest = { suggestions = emptyList() }
                ) {
                    suggestions.forEach { kw ->
                        DropdownMenuItem(
                            text = { Text(kw) },
                            onClick = {
                                query = kw
                                suggestions = emptyList()
                                searching = true
                                doSearch(kw, 1)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("清空搜索历史") },
                        onClick = {
                            scope.launch { repo.clearSearchHistory() }
                            suggestions = emptyList()
                        }
                    )
                }
            }
            // 电视机图标：单击直接切换到电视端；phone 变体已强制锁定手机端，隐藏入口
            if (BuildConfig.FORCED_UI_MODE.trim().lowercase() != "phone") {
                Box(
                    modifier = Modifier.clickable(onClick = onSwitchToTv).padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NavBarIcon(Dest.Film, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (isPhone) {
            if (!searching && categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it.typeId }) { cat ->
                        CategoryChip(
                            label = cat.typeName,
                            active = cat.typeId == selectedTid,
                            onClick = { selectedTid = cat.typeId; loadCategory(cat.typeId, 1) }
                        )
                    }
                }
            }
            GridVods(
                vods = vods, loading = loading, message = message,
                page = page, pageCount = pageCount, total = total,
                columns = phoneCols, isPhone = true,
                modifier = Modifier.weight(1f),
                onPrev = { if (searching) doSearch(query, page - 1) else loadCategory(selectedTid, page - 1) },
                onNext = { if (searching) doSearch(query, page + 1) else loadCategory(selectedTid, page + 1) },
                onVodClick = onVodClick
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.width(categoryRailW).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it.typeId }) { cat ->
                            CategoryChip(
                                label = cat.typeName,
                                active = cat.typeId == selectedTid,
                                onClick = { selectedTid = cat.typeId; loadCategory(cat.typeId, 1) }
                            )
                        }
                    }
                    GridVods(
                        vods = vods, loading = loading, message = message,
                        page = page, pageCount = pageCount, total = total,
                        columns = 0, isPhone = false,
                        modifier = Modifier.weight(1f),
                        onPrev = { if (page > 1) { if (searching) doSearch(query, page - 1) else loadCategory(selectedTid, page - 1) } },
                        onNext = { if (page < pageCount) { if (searching) doSearch(query, page + 1) else loadCategory(selectedTid, page + 1) } },
                        onVodClick = onVodClick
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun GridVods(
    vods: List<CmsVod>,
    loading: Boolean,
    message: String,
    page: Int,
    pageCount: Int,
    total: Int,
    columns: Int,
    isPhone: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onVodClick: (CmsVod) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        loading -> EmptyHint(message.ifEmpty { "加载中…" })
        message.isNotEmpty() -> EmptyHint(message)
        vods.isEmpty() -> EmptyHint("暂无影片")
        else -> LazyVerticalGrid(
            columns = if (isPhone) GridCells.Fixed(columns) else GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            items(vods, key = { it.id }) { vod ->
                VodPosterCard(vod = vod, onClick = { onVodClick(vod) })
            }
            item(span = { GridItemSpan(if (isPhone) columns else maxLineSpan) }) {
                PageRow(page = page, pageCount = pageCount, total = total, onPrev = onPrev, onNext = onNext)
            }
        }
    }
}

@Composable
private fun VodDetailScreen(
    site: SiteEntity,
    vod: CmsVod,
    repo: AppRepository,
    isPhone: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val controller = rememberPlayerController()
    var detail by remember { mutableStateOf<CmsDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(0) }
    var playingTitle by remember { mutableStateOf("") }
    var playMsg by remember { mutableStateOf<String?>(null) }
    var starred by remember { mutableStateOf(false) }
    var historyIndex by remember { mutableStateOf<String?>(null) }
    var historyPosSec by remember { mutableStateOf(0.0) }

    val relKey = site.api ?: site.id

    LaunchedEffect(controller) {
        val s = repo.currentSettings()
        controller.configure(s.timeoutMs, s.ua, s.proxyType, s.customProxy, s.ignoreSsl)
    }

    fun playEpisode(ep: CmsEpisode, resumeSec: Double = 0.0) {
        scope.launch {
            val sourceName = detail?.sources?.getOrNull(selectedSource)?.name
            val result = repo.resolvePlay(site, ep.url, sourceName)
            playMsg = result.message
            if (result.url.isNotEmpty() && !result.needParse) {
                controller.setSource(result.url, result.headers)
                playingTitle = ep.title
                if (resumeSec > 1.0) controller.seekTo((resumeSec * 1000).toLong())
                val indexKey = sourceName?.let { "$it##${ep.title}" } ?: ep.title
                repo.saveHistory(relKey, vod.id, detail?.pic, vod.name, indexKey)
            }
        }
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
    }

    // 播放中周期回写进度（每 5 秒）
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

    val outerModifier = if (isPhone)
        modifier.then(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()))
    else
        modifier.then(Modifier.fillMaxSize().padding(16.dp))
    Column(
        modifier = outerModifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = vod.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
            TextButton(onClick = {
                scope.launch {
                    starred = repo.toggleStar(relKey, vod.id, detail?.pic, vod.name, vod.remarks)
                }
            }) {
                Text(if (starred) "★ 已收藏" else "☆ 收藏")
            }
        }

        Box(modifier = Modifier.fillMaxWidth().then(if (isPhone) Modifier.height(220.dp) else Modifier.weight(1f))) {
            if (playingTitle.isNotEmpty()) {
                ZyfunPlayerView(controller = controller, modifier = Modifier.fillMaxSize())
            } else {
                EmptyHint("选择选集开始播放")
            }
        }

        playMsg?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }

        when {
            loading -> EmptyHint(message.ifEmpty { "加载中…" })
            message.isNotEmpty() -> EmptyHint(message)
            else -> {
                val d = detail
                if (d != null) {
                    DetailInfoCard(d = d, isPhone = isPhone)
                    if (d.sources.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(d.sources.size) { idx ->
                                val active = idx == selectedSource
                                var focused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .focusable()
                                        .onFocusChanged { focused = it.isFocused }
                                        .background(
                                            if (active || focused) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .clickable { selectedSource = idx }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = d.sources[idx].name,
                                        color = if (active || focused) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        val episodes = d.sources.getOrNull(selectedSource)?.episodes.orEmpty()
                        if (isPhone) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                episodes.chunked(3).forEach { rowEp ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowEp.forEach { ep ->
                                            val resume = if (historyMatches(historyIndex, ep.title)) historyPosSec else 0.0
                                            EpisodeChip(
                                                title = ep.title + if (resume > 1.0) "\uFF08\u7EED\u64AD ${formatSec(resume)}\uFF09" else "",
                                                subtitle = if (playingTitle == ep.title) "\u64AD\u653E\u4E2D" else null,
                                                active = playingTitle == ep.title,
                                                onClick = { playEpisode(ep, resume) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowEp.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(130.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(episodes, key = { it.url }) { ep ->
                                    val resume = if (historyMatches(historyIndex, ep.title)) historyPosSec else 0.0
                                    EpisodeChip(
                                        title = ep.title + if (resume > 1.0) "\uFF08\u7EED\u64AD ${formatSec(resume)}\uFF09" else "",
                                        subtitle = if (playingTitle == ep.title) "\u64AD\u653E\u4E2D" else null,
                                        active = playingTitle == ep.title,
                                        onClick = { playEpisode(ep, resume) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        EmptyHint("暂无播放线路")
                    }
                }
            }
        }
    }
}

/** 详情信息卡：左海报 + 评分星级 / 元信息 / 导演主演 / 简介折叠，TV 与手机复用 */
@Composable
private fun DetailInfoCard(d: CmsDetail, isPhone: Boolean) {
    val score = d.score.toFloatOrNull() ?: 0f
    val meta = listOf(d.year, d.area, d.lang, d.klass, d.state)
        .filter { it.isNotEmpty() }.joinToString(" · ")
    val cleanContent = d.content.replace(Regex("<[^>]*>"), "").trim()
    var expanded by remember { mutableStateOf(false) }
    val summary = if (cleanContent.length > 120 && !expanded) cleanContent.take(120) else cleanContent

    Row(horizontalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(MobileSize.middleRadius))
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (d.pic.isNotEmpty()) {
                AsyncImage(
                    model = d.pic,
                    contentDescription = d.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无图", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)
        ) {
            if (score > 0f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)
                ) {
                    RatingBar(rating = score)
                    Text(
                        d.score,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (d.director.isNotEmpty()) {
                Text(
                    "导演：${d.director}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (d.actor.isNotEmpty()) {
                Text(
                    "主演：${d.actor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (cleanContent.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (cleanContent.length > 120) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开简介")
                    }
                }
            }
        }
    }
}

@Composable
private fun PageRow(
    page: Int,
    pageCount: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = page > 1) { Text("上一页") }
        Text(
            "第 $page / $pageCount 页" + if (total > 0) " · 共 $total" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        TextButton(onClick = onNext, enabled = page < pageCount) { Text("下一页") }
    }
}

private fun formatSec(sec: Double): String {
    val s = sec.toLong()
    return "%02d:%02d".format(s / 60, s % 60)
}

/** 续播匹配：优先复合键 `线路##标题`，退化为标题匹配 */
private fun historyMatches(saved: String?, title: String): Boolean {
    if (saved.isNullOrEmpty()) return false
    if (saved == title) return true
    return saved.substringAfter("##", saved) == title
}

fun siteTypeLabel(type: Int): String = when (type) {
    SiteType.T0_XML -> "T0 XML"
    SiteType.T1_JSON -> "T1 JSON"
    SiteType.T4_DRPYJS0 -> "DrpyJs0"
    SiteType.T4_DRPYS -> "DrpyS"
    SiteType.T3_DRPY -> "Drpy"
    SiteType.T4_CATVOD -> "CatVod"
    SiteType.T3_XBPQ -> "XBPQ"
    SiteType.T3_XYQ -> "XYQ"
    SiteType.T3_APPYSV2 -> "AppYsV2"
    SiteType.T3_PY -> "Py"
    SiteType.T3_ALIST -> "Alist"
    SiteType.T3_CATOPEN -> "CatOpen"
    SiteType.T3_APPGET -> "AppGet"
    SiteType.T3_APPQI -> "AppQi"
    SiteType.T3_APPFOX -> "AppFox"
    else -> type.toString()
}
