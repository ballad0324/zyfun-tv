package com.hoopoe.zyfuntv.ui.screens.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.core.model.SiteType
import com.hoopoe.zyfuntv.data.local.Converters
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.remote.model.CmsVod
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.component.mobile.EmptyHint
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 网盘专区前台（手机 / TV 复用）：只读展示已配置的 Alist 网盘卡片与启用状态。
 * 站点增删改、账号密码、WebDAV 配置与备份 / 还原等后台功能统一收在
 * 设置页「网盘配置」区块（[DiskPanel]），不在前台重复暴露。
 */
@Composable
fun CloudScreen(
    repo: AppRepository,
    settings: AppSettings,
    onOpenVod: (PendingVod) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sites by repo.sites.collectAsState(emptyList())
    val diskSites = sites.filter { it.type == SiteType.T3_ALIST }
    var activeDisk by remember { mutableStateOf<SiteEntity?>(null) }
    var pathStack by remember { mutableStateOf<List<String>>(emptyList()) }

    val disk = activeDisk
    if (disk == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MobileSize.containerPadding),
            verticalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)
        ) {
            SectionTitle("我的网盘（${diskSites.size}）")

            if (diskSites.isEmpty()) {
                EmptyHint("暂无网盘 · 请到「设置 · 网盘配置」添加 Alist 自建盘")
            } else {
                diskSites.forEach { d ->
                    CloudDiskCard(
                        name = d.name,
                        server = d.api ?: "",
                        onClick = {
                            activeDisk = d
                            pathStack = listOf(startPageOf(d))
                        }
                    )
                }
            }

            Text(
                text = "网盘的添加与编辑、账号密码、WebDAV 备份与还原，均在「设置 · 网盘配置」中管理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    } else {
        CloudBrowser(
            disk = disk,
            repo = repo,
            pathStack = pathStack,
            onBack = {
                if (pathStack.size > 1) pathStack = pathStack.dropLast(1)
                else {
                    activeDisk = null
                    pathStack = emptyList()
                }
            },
            onPush = { path -> pathStack = pathStack + path },
            onOpenVod = onOpenVod,
            modifier = modifier
        )
    }
}

private fun startPageOf(disk: SiteEntity): String =
    Converters.jsonToMap(disk.ext)["startPage"]?.takeIf { it.isNotEmpty() } ?: "/"

/** 网盘目录浏览器：文件夹进入子目录，视频点击走详情播放器点播 */
@Composable
private fun CloudBrowser(
    disk: SiteEntity,
    repo: AppRepository,
    pathStack: List<String>,
    onBack: () -> Unit,
    onPush: (String) -> Unit,
    onOpenVod: (PendingVod) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val currentPath = pathStack.lastOrNull() ?: "/"
    var items by remember { mutableStateOf<List<CmsVod>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(disk.id, currentPath) {
        loading = true
        msg = null
        val page = withContext(Dispatchers.IO) { repo.fetchVodList(disk, currentPath, 1) }
        items = page.list
        loading = false
        if (page.list.isEmpty()) msg = "此目录为空"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MobileSize.containerPadding),
        verticalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(if (pathStack.size > 1) "返回上级" else "返回网盘列表")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    disk.name.ifEmpty { "未命名网盘" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            loading -> EmptyHint("加载中…")
            msg != null -> EmptyHint(msg!!)
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { vod ->
                    val isFolder = vod.id.endsWith("/")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .clip(RoundedCornerShape(MobileSize.middleRadius))
                            .clickable {
                                if (isFolder) onPush(vod.id)
                                else onOpenVod(PendingVod(disk.id, vod.id, vod.name, vod.pic))
                            }
                            .padding(horizontal = MobileSize.containerPadding, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                vod.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (vod.remarks.isNotEmpty()) {
                                Text(
                                    vod.remarks,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(
                            if (isFolder) "进入" else "播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/** 前台网盘入口卡片：名称 + 地址，点击进入资源管理器式目录浏览；不展示启用/停用状态 */
@Composable
private fun CloudDiskCard(
    name: String,
    server: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = MobileSize.containerPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.ifEmpty { "未命名网盘" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (server.isNotEmpty()) {
                Text(
                    text = server,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "进入 ›",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun DiskPanel(
    repo: AppRepository,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val sites by repo.sites.collectAsState(emptyList())
    val diskSites = sites.filter { it.type == SiteType.T3_ALIST }

    var msg by remember { mutableStateOf<String?>(null) }

    // Alist 表单
    var showDiskForm by remember { mutableStateOf(false) }
    var diskEditId by remember { mutableStateOf<String?>(null) }
    var diskName by remember { mutableStateOf("") }
    var diskServer by remember { mutableStateOf("") }
    var diskStartPage by remember { mutableStateOf("/") }
    var diskShowAll by remember { mutableStateOf(false) }
    var diskUser by remember { mutableStateOf("") }
    var diskPwd by remember { mutableStateOf("") }
    var diskSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var diskQuery by remember { mutableStateOf("") }

    // WebDAV 表单
    var davUrlInput by remember(settings.davUrl) { mutableStateOf(settings.davUrl) }
    var davUserInput by remember(settings.davUser) { mutableStateOf(settings.davUser) }
    var davPassInput by remember(settings.davPass) { mutableStateOf(settings.davPass) }
    var davPathInput by remember(settings.davPath) { mutableStateOf(settings.davPath) }
    var davMsg by remember { mutableStateOf<String?>(null) }

    fun resetDiskForm() {
        diskEditId = null
        diskName = ""; diskServer = ""; diskStartPage = "/"
        diskShowAll = false; diskUser = ""; diskPwd = ""
    }

    fun fillDiskForm(item: SiteEntity) {
        val m = Converters.jsonToMap(item.ext)
        diskEditId = item.id
        diskName = item.name
        diskServer = item.api ?: ""
        diskStartPage = m["startPage"] ?: "/"
        diskShowAll = m["showAll"].let { it == "true" || it == "1" }
        diskUser = m["username"] ?: ""
        diskPwd = m["password"] ?: ""
        showDiskForm = true
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)
    ) {
        SectionTitle("Alist 网盘（${diskSites.size}）")

        if (diskSites.isNotEmpty()) {
            val backupSite = diskSites.first()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelChip(
                    label = "备份到网盘",
                    active = false,
                    onClick = {
                        scope.launch {
                            val ok = repo.backupToAlist(backupSite, "zyfun-backup.json")
                            msg = if (ok) "已备份到网盘：${backupSite.name}" else "备份到网盘失败"
                        }
                    }
                )
                PanelChip(
                    label = "从网盘恢复",
                    active = false,
                    onClick = {
                        scope.launch {
                            val sum = repo.restoreFromAlist(backupSite, "zyfun-backup.json")
                            msg = if (sum != null) {
                                "已从网盘恢复：站点${sum.sites} 解析${sum.parses} 直播${sum.iptv} 频道${sum.channels} 历史${sum.history} 收藏${sum.stars}"
                            } else "从网盘恢复失败"
                        }
                    }
                )
            }
        }

        SourceManageTable(
            rows = diskSites.map { d ->
                TblRow(
                    id = d.id,
                    name = d.name,
                    typeLabel = "Alist",
                    groupLabel = d.api,
                    isActive = d.isActive,
                    isDefault = false
                )
            },
            showGroup = true,
            showDetect = false,
            selected = diskSelected,
            onSelectChange = { diskSelected = it },
            query = diskQuery,
            onQueryChange = { diskQuery = it },
            onAdd = { if (!showDiskForm) resetDiskForm(); showDiskForm = true },
            onEnable = {
                if (diskSelected.isNotEmpty()) scope.launch {
                    diskSelected.forEach { repo.setSiteActive(it, true) }
                    diskSelected = emptySet()
                }
            },
            onDisable = {
                if (diskSelected.isNotEmpty()) scope.launch {
                    diskSelected.forEach { repo.setSiteActive(it, false) }
                    diskSelected = emptySet()
                }
            },
            onDelete = {
                if (diskSelected.isNotEmpty()) scope.launch {
                    diskSelected.forEach { id ->
                        diskSites.firstOrNull { it.id == id }?.let { repo.deleteSite(it) }
                    }
                    diskSelected = emptySet()
                }
            },
            onToggleActive = { id, active -> scope.launch { repo.setSiteActive(id, active) } },
            onSetDefault = {},
            onEdit = { id -> diskSites.firstOrNull { it.id == id }?.let { fillDiskForm(it) } },
            onDeleteOne = { id ->
                diskSites.firstOrNull { it.id == id }?.let { scope.launch { repo.deleteSite(it) } }
            }
        )

        PanelChip(
            label = if (showDiskForm) "收起网盘表单" else "＋ 添加网盘",
            active = false,
            onClick = {
                if (!showDiskForm) resetDiskForm()
                showDiskForm = !showDiskForm
            }
        )
        if (showDiskForm) {
            OutlinedTextField(
                value = diskName,
                onValueChange = { diskName = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = diskServer,
                onValueChange = { diskServer = it },
                label = { Text("Alist 服务器地址（https://盘域名或IP:端口）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = diskStartPage,
                onValueChange = { diskStartPage = it },
                label = { Text("起始目录（默认 /）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "显示全部文件（含非视频）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                PanelChip(
                    label = if (diskShowAll) "开" else "关",
                    active = diskShowAll,
                    onClick = { diskShowAll = !diskShowAll }
                )
            }
            OutlinedTextField(
                value = diskUser,
                onValueChange = { diskUser = it },
                label = { Text("用户名（公开盘留空）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = diskPwd,
                onValueChange = { diskPwd = it },
                label = { Text("密码（私有盘填写，公开盘留空）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            PanelChip(
                label = "保存网盘",
                active = false,
                onClick = {
                    if (diskServer.isNotBlank()) {
                        scope.launch {
                            repo.upsertAlistSite(
                                diskEditId, diskName, diskServer, diskStartPage,
                                diskShowAll, diskUser, diskPwd
                            )
                            resetDiskForm()
                            showDiskForm = false
                        }
                    }
                }
            )
        }

        msg?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        SectionTitle("WebDAV 备份")
        OutlinedTextField(
            value = davUrlInput,
            onValueChange = { davUrlInput = it },
            label = { Text("WebDAV 服务器地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = davUserInput,
                onValueChange = { davUserInput = it },
                label = { Text("用户名（匿名留空）") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = davPassInput,
                onValueChange = { davPassInput = it },
                label = { Text("密码") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = davPathInput,
            onValueChange = { davPathInput = it },
            label = { Text("远程目录（默认 /zyfuntv）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelChip(
                label = "保存配置",
                active = false,
                onClick = {
                    scope.launch {
                        repo.setDavConfig(davUrlInput, davUserInput, davPassInput, davPathInput)
                        davMsg = "WebDAV 配置已保存"
                    }
                }
            )
            PanelChip(
                label = "备份到 WebDAV",
                active = false,
                onClick = {
                    scope.launch {
                        val ok = repo.backupToWebDav()
                        davMsg = if (ok) "已备份到 WebDAV" else "备份失败：未配置或网络错误"
                    }
                }
            )
            PanelChip(
                label = "从 WebDAV 恢复",
                active = false,
                onClick = {
                    scope.launch {
                        val sum = repo.restoreFromWebDav()
                        davMsg = if (sum != null) {
                            "已从 WebDAV 恢复：站点${sum.sites} 解析${sum.parses} 直播${sum.iptv} 频道${sum.channels} 历史${sum.history} 收藏${sum.stars}"
                        } else "恢复失败：未配置或备份不存在"
                    }
                }
            )
        }
        davMsg?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun PanelChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(MobileSize.middleRadius))
            .background(bg)
            .border(
                width = MobileSize.borderWidth,
                color = if (active || focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(MobileSize.middleRadius)
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
