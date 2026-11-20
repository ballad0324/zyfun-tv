package com.hoopoe.zyfuntv.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
fun rememberPlayerController(): PlayerController {
    val context = LocalContext.current
    val controller = remember { PlayerController(context) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}

@Composable
fun ZyfunPlayerView(
    controller: PlayerController,
    modifier: Modifier = Modifier,
    showDefaultControls: Boolean = true
) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    var menu by remember { mutableStateOf("") } // "" / "audio" / "subtitle"

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.player
                    useController = true
                    setShowSubtitleButton(true)
                }
            },
            update = {
                it.player = controller.player
                it.resizeMode = state.resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showDefaultControls) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { controller.cycleSpeed() }) {
                Text("%.1fx".format(state.speed), color = MaterialTheme.colorScheme.onPrimary)
            }

            TextButton(onClick = { controller.cycleResizeMode() }) {
                val idx = controller.resizeModes.indexOf(state.resizeMode).coerceAtLeast(0)
                Text(controller.resizeModeLabels[idx], color = MaterialTheme.colorScheme.onPrimary)
            }

            Box {
                TextButton(onClick = { menu = if (menu == "audio") "" else "audio" }) {
                    Text("音轨", color = MaterialTheme.colorScheme.onPrimary)
                }
                DropdownMenu(expanded = menu == "audio", onDismissRequest = { menu = "" }) {
                    val tracks = controller.audioTracks()
                    if (tracks.isEmpty()) {
                        DropdownMenuItem(text = { Text("无可用音轨") }, onClick = { menu = "" })
                    } else {
                        tracks.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(if (t.selected) "● ${t.label}" else t.label) },
                                onClick = {
                                    controller.selectAudio(t.groupIndex, t.trackIndex)
                                    menu = ""
                                }
                            )
                        }
                    }
                }
            }

            Box {
                TextButton(onClick = { menu = if (menu == "subtitle") "" else "subtitle" }) {
                    Text("字幕", color = MaterialTheme.colorScheme.onPrimary)
                }
                DropdownMenu(expanded = menu == "subtitle", onDismissRequest = { menu = "" }) {
                    DropdownMenuItem(
                        text = { Text("关闭字幕") },
                        onClick = { controller.disableSubtitle(); menu = "" }
                    )
                    controller.subtitleTracks().forEach { t ->
                        DropdownMenuItem(
                            text = { Text(if (t.selected) "● ${t.label}" else t.label) },
                            onClick = {
                                controller.selectSubtitle(t.groupIndex, t.trackIndex)
                                menu = ""
                            }
                        )
                    }
                }
            }

            TextButton(onClick = { enterPip(context) }) {
                Text("画中画", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        }
    }
}

private fun enterPip(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = context.findActivity() ?: return
    runCatching {
        activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
