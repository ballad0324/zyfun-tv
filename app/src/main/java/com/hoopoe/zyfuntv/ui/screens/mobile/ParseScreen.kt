package com.hoopoe.zyfuntv.ui.screens.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.data.local.AnalyzeEntity
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.player.ZyfunPlayerView
import com.hoopoe.zyfuntv.player.rememberPlayerController
import com.hoopoe.zyfuntv.ui.component.mobile.PlayerPlaceholder
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize
import kotlinx.coroutines.launch

/**
 * 解析页：
 * - 手机竖屏：播放器在上，下方链接 + 下拉选源 / 播放。
 * - TV 横屏：播放器居中在上；底部一行：解析源下拉（链接左侧）+ 链接输入 + 解析按钮，整体居中。
 * 解析源的增删改统一在设置页「解析配置」管理。
 */
@Composable
fun ParseScreen(
    settings: AppSettings,
    repo: AppRepository,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    val analyzes by repo.analyzes.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val controller = rememberPlayerController()

    var selected by remember { mutableStateOf<AnalyzeEntity?>(null) }
    var targetUrl by remember { mutableStateOf("") }
    var playMsg by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    var sourceMenu by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        val s = repo.currentSettings()
        controller.configure(s.timeoutMs, s.ua, s.proxyType, s.customProxy, s.ignoreSsl)
    }

    LaunchedEffect(analyzes) {
        if (selected == null) {
            selected = analyzes.firstOrNull { it.id == settings.defaultAnalyze }
                ?: analyzes.firstOrNull()
        }
    }

    fun play() {
        val a = selected ?: run {
            playMsg = "请先选择解析源"
            return
        }
        val url = targetUrl.trim()
        if (url.isEmpty()) {
            playMsg = "请输入待解析的视频链接"
            return
        }
        scope.launch {
            val result = repo.resolveWithAnalyze(a, url)
            playMsg = result.message
            if (result.url.isNotEmpty() && !result.needParse) {
                controller.setSource(result.url, result.headers)
                playingTitle = url
            }
        }
    }

    val playerBlock: @Composable (Boolean) -> Unit = { phone ->
        Box(
            modifier = Modifier
                .then(if (phone) Modifier.aspectRatio(16f / 9f) else Modifier.fillMaxSize())
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 5))
        ) {
            if (playingTitle.isNotEmpty()) {
                ZyfunPlayerView(controller = controller, modifier = Modifier.fillMaxSize())
            } else {
                PlayerPlaceholder("填入链接 · 选择解析源后解析播放", modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (isPhone) {
        // 手机竖屏：播放器在上铺满宽度，下方输入与操作
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            playerBlock(true)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("待解析视频链接") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        TextButton(onClick = { sourceMenu = true }) {
                            Text(selected?.name ?: "选择解析源")
                        }
                        DropdownMenu(
                            expanded = sourceMenu,
                            onDismissRequest = { sourceMenu = false }
                        ) {
                            if (analyzes.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无解析源") },
                                    onClick = { sourceMenu = false }
                                )
                            } else {
                                analyzes.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a.name) },
                                        onClick = {
                                            selected = a
                                            sourceMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { play() }) { Text("播放") }
                }
                playMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    } else {
        // TV 横屏：播放器居中在上；底部一行：源下拉（链接左边）+ 链接输入 + 解析按钮
        Column(
            modifier = modifier.fillMaxSize().padding(TvSize.containerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSize.smallMargin)
        ) {
            // 播放器居中显示
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                playerBlock(false)
            }

            // 底部一行：源下拉（链接左边）+ 链接输入 + 解析按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
            ) {
                Box {
                    TextButton(onClick = { sourceMenu = true }) {
                        Text(selected?.name ?: "选择解析源")
                    }
                    DropdownMenu(
                        expanded = sourceMenu,
                        onDismissRequest = { sourceMenu = false }
                    ) {
                        if (analyzes.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无解析源") },
                                onClick = { sourceMenu = false }
                            )
                        } else {
                            analyzes.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(a.name) },
                                    onClick = {
                                        selected = a
                                        sourceMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("待解析视频链接") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { play() })
                )
                TextButton(onClick = { play() }) { Text("解析") }
            }
            playMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
