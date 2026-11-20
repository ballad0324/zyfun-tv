package com.hoopoe.zyfuntv.ui.screens.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.component.tv.CategoryChip
import com.hoopoe.zyfuntv.ui.component.tv.EmptyHint
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.ui.screens.tv.components.TvPosterGrid
import com.hoopoe.zyfuntv.ui.screens.tv.detail.TvDetailScreen
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize
import kotlinx.coroutines.launch

/**
 * TV 影视主页：站点由根壳顶部栏切换，本屏负责分类横排条 + 海报网格（无 Banner）；
 * 点海报进 TV 详情大屏。
 */
@Composable
fun TvHomeScreen(
    repo: AppRepository,
    sites: List<SiteEntity>,
    selectedSite: SiteEntity?,
    onSiteChange: (SiteEntity) -> Unit,
    initialVod: PendingVod? = null,
    onConsumeVod: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedVod by remember { mutableStateOf<CmsVod?>(null) }

    LaunchedEffect(initialVod, sites) {
        val pending = initialVod ?: return@LaunchedEffect
        val site = sites.firstOrNull { it.api == pending.siteSource || it.id == pending.siteSource }
            ?: return@LaunchedEffect
        onSiteChange(site)
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
    BackHandler(enabled = currentSite != null && currentVod != null) { selectedVod = null }

    when {
        currentSite != null && currentVod != null -> TvDetailScreen(
            site = currentSite,
            vod = currentVod,
            repo = repo,
            onBack = { selectedVod = null },
            modifier = modifier
        )
        else -> TvHomeGrid(
            site = currentSite,
            repo = repo,
            modifier = modifier,
            onVodClick = { selectedVod = it }
        )
    }
}

@Composable
private fun TvHomeGrid(
    site: SiteEntity?,
    repo: AppRepository,
    modifier: Modifier = Modifier,
    onVodClick: (CmsVod) -> Unit
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CmsCategory>>(emptyList()) }
    var selectedTid by remember { mutableStateOf("") }
    var vods by remember { mutableStateOf<List<CmsVod>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var pageCount by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun loadCategory(tid: String, p: Int, append: Boolean) {
        val current = site ?: return
        scope.launch {
            if (append) loadingMore = true else loading = true
            val result = repo.fetchVodList(current, tid, p)
            vods = if (append) vods + result.list else result.list
            page = result.page.coerceAtLeast(1)
            pageCount = result.pageCount.coerceAtLeast(1)
            message = if (!append && result.list.isEmpty()) "暂无影片" else ""
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(site?.id) {
        val current = site ?: return@LaunchedEffect
        loading = true
        message = "分类加载中…"
        categories = repo.fetchCategories(current)
        if (categories.isNotEmpty()) {
            selectedTid = categories.first().typeId
            loadCategory(selectedTid, 1, false)
        } else {
            message = "该站点暂不支持或无分类"
            loading = false
        }
    }

    if (site == null) {
        EmptyHint("请选择站点", modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TvSize.containerPadding),
        verticalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
    ) {
        // 分类横排条
        LazyRow(horizontalArrangement = Arrangement.spacedBy(TvSize.miniMargin)) {
            items(categories, key = { it.typeId }) { cat ->
                CategoryChip(
                    label = cat.typeName,
                    active = cat.typeId == selectedTid,
                    onClick = {
                        selectedTid = cat.typeId
                        loadCategory(cat.typeId, 1, false)
                    }
                )
            }
        }

        // 内容区：网格占满剩余高度，滚动到底自动追加下一页（无 Banner、无分页条）
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> EmptyHint(message.ifEmpty { "加载中…" })
                message.isNotEmpty() -> EmptyHint(message)
                vods.isEmpty() -> EmptyHint("暂无影片")
                else -> TvPosterGrid(
                    vods = vods,
                    onVodClick = onVodClick,
                    onLoadMore = {
                        if (!loading && !loadingMore && page < pageCount) {
                            loadCategory(selectedTid, page + 1, true)
                        }
                    },
                    loadingMore = loadingMore,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
