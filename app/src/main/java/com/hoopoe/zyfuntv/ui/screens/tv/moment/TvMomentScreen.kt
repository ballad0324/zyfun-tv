package com.hoopoe.zyfuntv.ui.screens.tv.moment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.core.model.RecordType
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.component.tv.EmptyHint
import com.hoopoe.zyfuntv.ui.component.tv.SegmentedPill
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.ui.screens.tv.components.TvPosterGrid
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize

/** TV 我的大屏：历史 / 收藏双 Tab，海报网格，点击跳转续播 */
@Composable
fun TvMomentScreen(
    repo: AppRepository,
    onOpenVod: (PendingVod) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val history by repo.history.collectAsState(emptyList())
    val stars by repo.stars.collectAsState(emptyList())
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TvSize.containerPadding),
        verticalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            SegmentedPill(
                items = listOf(
                    "历史 ${history.size}" to (tab == 0),
                    "收藏 ${stars.size}" to (tab == 1)
                ),
                onSelect = { tab = it }
            )
        }

        if (tab == 0) {
            if (history.isEmpty()) {
                EmptyHint("暂无播放历史")
            } else {
                val vods = history.map { h ->
                    CmsVod(
                        id = h.videoId ?: h.id,
                        name = h.videoName ?: "未命名",
                        pic = h.videoImage.orEmpty(),
                        remarks = buildString {
                            append(recordTypeLabel(h.type))
                            if (h.duration > 0) {
                                val pct = (h.watchTime / h.duration * 100).toInt().coerceIn(0, 100)
                                append(" · 已看 $pct%")
                            }
                        },
                        blurb = h.videoIndex?.substringAfter("##", h.videoIndex ?: "").orEmpty()
                    )
                }
                TvPosterGrid(
                    vods = vods,
                    onVodClick = { vod ->
                        val h = history.firstOrNull { (it.videoId ?: it.id) == vod.id } ?: return@TvPosterGrid
                        val vid = h.videoId ?: return@TvPosterGrid
                        onOpenVod(PendingVod(h.siteSource ?: "", vid, h.videoName ?: "", h.videoImage))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            if (stars.isEmpty()) {
                EmptyHint("暂无收藏")
            } else {
                val vods = stars.map { s ->
                    CmsVod(
                        id = s.videoId ?: s.id,
                        name = s.videoName ?: "未命名",
                        pic = s.videoImage.orEmpty(),
                        remarks = recordTypeLabel(s.type),
                        blurb = listOfNotNull(s.videoType, s.videoRemarks).filter { it.isNotEmpty() }.joinToString(" · ")
                    )
                }
                TvPosterGrid(
                    vods = vods,
                    onVodClick = { vod ->
                        val s = stars.firstOrNull { (it.videoId ?: it.id) == vod.id } ?: return@TvPosterGrid
                        val vid = s.videoId ?: return@TvPosterGrid
                        onOpenVod(PendingVod(s.relateId ?: "", vid, s.videoName ?: "", s.videoImage))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun recordTypeLabel(type: Int): String = when (type) {
    RecordType.LIVE -> "直播"
    else -> "影视"
}
