package com.hoopoe.zyfuntv.ui.screens.mobile.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.core.crypto.Crypto
import com.hoopoe.zyfuntv.core.crypto.Crypto.toHex
import com.hoopoe.zyfuntv.core.debug.DebugLog
import com.hoopoe.zyfuntv.core.network.WebSniffer
import com.hoopoe.zyfuntv.data.repository.AppRepository
import kotlinx.coroutines.launch

/** 实验室入口：加解密工具 / 网页嗅探调试 / 筛选·对比·编辑器·扩展占位 */
@Composable
fun LabPanel(repo: AppRepository, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("crypto") }
    // 注意：本面板被嵌入设置页右侧的垂直 ScrollView 内，顶层不得再叠加 verticalScroll，
    // 否则可滚动组件拿到无限最大高度约束，会抛 IllegalStateException 崩溃。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("← 返回") }
            listOf("crypto" to "加解密", "sniffer" to "嗅探", "log" to "日志", "more" to "更多工具").forEach { (key, label) ->
                TextButton(onClick = { tab = key }) {
                    Text(if (tab == key) "● $label" else label)
                }
            }
        }

        when (tab) {
            "crypto" -> CryptoTool()
            "sniffer" -> SnifferPanel(repo)
            "log" -> LogPanel()
            else -> MoreTools()
        }
    }
}

@Composable
private fun CryptoTool() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("加解密 / 摘要 / 编码", style = MaterialTheme.typography.titleMedium)
        BasicTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            decorationBox = { inner ->
                if (input.isEmpty()) Text("输入待处理文本…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = {
                output = buildList {
                    add("MD5      : ${Crypto.md5Hex(input)}")
                    add("MD5-16   : ${Crypto.md5_16(input)}")
                    add("SHA1     : ${Crypto.sha1Hex(input)}")
                    add("SHA256   : ${Crypto.sha256Hex(input)}")
                }.joinToString("\n")
            }) { Text("摘要") }
            TextButton(onClick = { output = "Base64 编码:\n${Crypto.b64EncodeText(input)}" }) { Text("Base64 编") }
            TextButton(onClick = { output = "Base64 解码:\n${runCatching { Crypto.b64DecodeText(input) }.getOrDefault("解码失败")}" }) { Text("Base64 解") }
            TextButton(onClick = { output = "Hex 编码:\n${input.toByteArray().toHex()}" }) { Text("Hex 编") }
            TextButton(onClick = {
                output = "Hex 解码:\n${runCatching { String(Crypto.hexDecode(input), Charsets.UTF_8) }.getOrDefault("解码失败")}"
            }) { Text("Hex 解") }
            TextButton(onClick = { output = "URL 编码:\n${Crypto.urlEncode(input)}" }) { Text("URL 编") }
            TextButton(onClick = { output = "URL 解码:\n${runCatching { Crypto.urlDecode(input) }.getOrDefault("解码失败")}" }) { Text("URL 解") }
            TextButton(onClick = { output = "UUID v4:\n${Crypto.uuidV4()}" }) { Text("UUID") }
        }
        if (output.isNotEmpty()) {
            Text(
                output,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SnifferPanel(repo: AppRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("网页嗅探调试", style = MaterialTheme.typography.titleMedium)
        BasicTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            decorationBox = { inner ->
                if (url.isEmpty()) Text("输入待嗅探页面地址…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                if (url.isBlank()) return@TextButton
                loading = true
                result = "嗅探中…"
                scope.launch {
                    val settings = repo.currentSettings()
                    val sniffed = WebSniffer.sniff(context, url, emptyMap(), settings.timeoutMs)
                    result = if (sniffed != null) {
                        buildString {
                            appendLine("命中媒体：${sniffed.url}")
                            if (sniffed.headers.isNotEmpty()) {
                                appendLine("请求头：")
                                sniffed.headers.forEach { (k, v) -> appendLine("  $k: $v") }
                            }
                        }
                    } else {
                        // 兜底：静态正则提取页面 HTML
                        val html = repo.fetchRawText(url)
                        val static = WebSniffer.staticExtract(html, url)
                        if (static != null) "静态提取：$static" else "未命中可播媒体地址"
                    }
                    loading = false
                }
            }) { Text(if (loading) "嗅探中…" else "开始嗅探") }
        }
        if (result.isNotEmpty()) {
            Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun LogPanel() {
    val logs by DebugLog.flow.collectAsState()
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("调试日志（最近 ${logs.size} 条）", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(DebugLog.export()))
                }) { Text("复制全部") }
                TextButton(onClick = { DebugLog.clear() }) { Text("清空") }
            }
        }
        Text(
            "先回到影视页切换站点 / 搜索 / 起播，再回本页查看网络、分类、列表、解析与播放链路。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        if (logs.isEmpty()) {
            Text("暂无日志", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            logs.reversed().forEach { entry ->
                val color = when (entry.level) {
                    "E" -> Color(0xFFD32F2F)
                    "W" -> Color(0xFFE65100)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    "${entry.time} [${entry.level}] ${entry.tag}: ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun MoreTools() {
    val items = listOf(
        "源筛选 / 去重（sift）" to "按分组、备注、有效性筛选与去重源列表（二期）",
        "配置对比（diff）" to "两份备份 / 源配置逐项差异对比（二期）",
        "规则编辑器" to "在线编辑蜘蛛规则 / 模板注入与日志查看（二期）",
        "扩展管理" to "插件安装、详情与环境变量管理（二期，依赖 JS 引擎）"
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("更多工具", style = MaterialTheme.typography.titleMedium)
        items.forEach { (title, desc) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
