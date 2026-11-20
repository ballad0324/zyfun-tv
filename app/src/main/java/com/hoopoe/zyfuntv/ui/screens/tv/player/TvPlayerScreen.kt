package com.hoopoe.zyfuntv.ui.screens.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.hoopoe.zyfuntv.core.player.PlayerIntents
import com.hoopoe.zyfuntv.data.remote.model.MediaKindClassifier
import com.hoopoe.zyfuntv.player.PlayerController
import com.hoopoe.zyfuntv.player.ZyfunPlayerView

/**
 * TV 全屏播放器：视频铺满，顶部返回/标题，底部播放、倍速、清晰度、音轨、字幕。
 * 手势：单击显隐控制条，双击播放/暂停，水平拖动快进快退。
 */
@Composable
fun TvPlayerScreen(
    controller: PlayerController,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playUrl: String = "",
    headers: Map<String, String> = emptyMap(),
    externalPkg: String? = null,
    onPrevEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf("") } // "" / "video" / "audio" / "subtitle"
    var dragStartPos by remember { mutableStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }

    val subtitleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = queryDisplayName(context, uri) ?: "外挂字幕"
        controller.addSidecarSubtitle(uri.toString(), subtitleMime(name), name)
    }

    LaunchedEffect(state.error) {
        notice = state.error
    }

    fun launchExternal() {
        val kind = MediaKindClassifier.classify(playUrl)
        val pkg = externalPkg?.takeIf { it.isNotBlank() }
        val intent = PlayerIntents.buildExternalPlayer(playUrl, headers, kind, pkg)
        when {
            intent == null -> notice = "当前地址无法用外部播放器打开"
            PlayerIntents.canResolve(context, intent) -> context.startActivity(intent)
            pkg != null -> {
                val fallback = PlayerIntents.buildExternalPlayer(playUrl, headers, kind, null)
                if (fallback != null && PlayerIntents.canResolve(context, fallback)) context.startActivity(fallback)
                else notice = "指定播放器未安装，且未找到其他可处理该媒体的外部播放器"
            }
            else -> notice = "未找到可处理该媒体的外部播放器"
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val viewWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        ZyfunPlayerView(
            controller = controller,
            showDefaultControls = false,
            modifier = Modifier.fillMaxSize()
        )

        // 手势层：单击显隐、双击暂停/播放、横拖 seek
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (!locked) controlsVisible = !controlsVisible },
                        onDoubleTap = {
                            if (!locked && state.isPlaying) controller.pause() else controller.play()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragStartPos = state.positionMs },
                        onHorizontalDrag = { change, dragAmount ->
                            val duration = state.durationMs
                            if (duration > 0 && viewWidthPx > 0f) {
                                val ratio = dragAmount / viewWidthPx
                                val target = (dragStartPos + (duration * ratio).toLong())
                                    .coerceIn(0L, duration)
                                controller.seekTo(target)
                            }
                            change.consume()
                        }
                    )
                }
        )

        if (locked) {
            TextButton(
                onClick = { locked = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) { Text("解锁") }
        }

        if (controlsVisible && !locked) {
            // 顶部浮层：返回 + 标题
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 底部浮层：播放控制 + 倍速 + 清晰度 + 音轨 + 字幕
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = { if (state.isPlaying) controller.pause() else controller.play() }) {
                    Text(if (state.isPlaying) "暂停" else "播放")
                }
                TextButton(onClick = { controller.cycleSpeed() }) {
                    Text("倍速 ${state.speed}x")
                }

                TrackMenu(
                    label = "清晰度",
                    expanded = menu == "video",
                    tracks = controller.videoTracks(),
                    onToggle = { menu = if (menu == "video") "" else "video" },
                    onDismiss = { menu = "" },
                    onSelect = { gi, ti -> controller.selectVideo(gi, ti); menu = "" }
                )
                TrackMenu(
                    label = "音轨",
                    expanded = menu == "audio",
                    tracks = controller.audioTracks(),
                    onToggle = { menu = if (menu == "audio") "" else "audio" },
                    onDismiss = { menu = "" },
                    onSelect = { gi, ti -> controller.selectAudio(gi, ti); menu = "" }
                )
                TrackMenu(
                    label = "字幕",
                    expanded = menu == "subtitle",
                    tracks = controller.subtitleTracks(),
                    onToggle = { menu = if (menu == "subtitle") "" else "subtitle" },
                    onDismiss = { menu = "" },
                    onSelect = { gi, ti -> controller.selectSubtitle(gi, ti); menu = "" },
                    allowDisable = true,
                    onDisable = { controller.disableSubtitle(); menu = "" }
                )

                TextButton(onClick = { controller.cycleResizeMode() }) {
                    val idx = controller.resizeModes.indexOf(state.resizeMode).coerceAtLeast(0)
                    Text(controller.resizeModeLabels[idx])
                }

                TextButton(onClick = {
                    subtitleLauncher.launch(arrayOf("text/plain", "text/vtt", "application/x-subrip", "*/*"))
                }) { Text("外挂字幕") }

                TextButton(onClick = { launchExternal() }) { Text("外播") }

                TextButton(onClick = { locked = true }) { Text("锁定") }

                if (onPrevEpisode != null) {
                    TextButton(onClick = { onPrevEpisode?.invoke() }) { Text("上一集") }
                }
                if (onNextEpisode != null) {
                    TextButton(onClick = { onNextEpisode?.invoke() }) { Text("下一集") }
                }
            }

            notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp)
                )
            }
        }
    }
}

/** 按文件名推断外挂字幕 MIME */
private fun subtitleMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ass"
        "srt" -> "text/x-subrip"
        else -> "text/plain"
    }
}

/** 读取 SAF 选中文件的显示名 */
private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}

@Composable
private fun TrackMenu(
    label: String,
    expanded: Boolean,
    tracks: List<com.hoopoe.zyfuntv.player.TrackOption>,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (groupIndex: Int, trackIndex: Int) -> Unit,
    allowDisable: Boolean = false,
    onDisable: (() -> Unit)? = null
) {
    Box {
        TextButton(onClick = onToggle) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            if (allowDisable && onDisable != null) {
                DropdownMenuItem(text = { Text("关闭字幕") }, onClick = onDisable)
            }
            if (tracks.isEmpty()) {
                DropdownMenuItem(text = { Text("无可用$label") }, onClick = onDismiss)
            } else {
                tracks.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(if (t.selected) "● ${t.label}" else t.label) },
                        onClick = { onSelect(t.groupIndex, t.trackIndex) }
                    )
                }
            }
        }
    }
}
