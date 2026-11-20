package com.hoopoe.zyfuntv.ui.screens.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.core.model.RecordType
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.component.mobile.EmptyHint
import com.hoopoe.zyfuntv.ui.component.mobile.SegmentedPill
import com.hoopoe.zyfuntv.ui.component.mobile.VodPosterCard

@Composable
fun MomentScreen(
    repo: AppRepository,
    onOpenVod: (PendingVod) -> Unit = {},
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    val history by repo.history.collectAsState(emptyList())
    val stars by repo.stars.collectAsState(emptyList())
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier.then(Modifier.fillMaxSize().padding(16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyHint("暂无播放历史")
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isPhone) GridCells.Fixed(2) else GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(history, key = { it.id }) { h ->
                        val vod = CmsVod(
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
                        VodPosterCard(
                            vod = vod,
                            onClick = {
                                val vid = h.videoId ?: return@VodPosterCard
                                onOpenVod(PendingVod(h.siteSource ?: "", vid, h.videoName ?: "", h.videoImage))
                            }
                        )
                    }
                }
            }
        } else {
            if (stars.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyHint("暂无收藏")
                }
            } else {
                LazyVerticalGrid(
                    columns = if (isPhone) GridCells.Fixed(2) else GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stars, key = { it.id }) { s ->
                        val vod = CmsVod(
                            id = s.videoId ?: s.id,
                            name = s.videoName ?: "未命名",
                            pic = s.videoImage.orEmpty(),
                            remarks = recordTypeLabel(s.type),
                            blurb = listOfNotNull(s.videoType, s.videoRemarks).filter { it.isNotEmpty() }.joinToString(" · ")
                        )
                        VodPosterCard(
                            vod = vod,
                            onClick = {
                                val vid = s.videoId ?: return@VodPosterCard
                                onOpenVod(PendingVod(s.relateId ?: "", vid, s.videoName ?: "", s.videoImage))
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun recordTypeLabel(type: Int): String = when (type) {
    RecordType.LIVE -> "直播"
    else -> "影视"
}
