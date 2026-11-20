package com.hoopoe.zyfuntv.ui.screens.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hoopoe.zyfuntv.BuildConfig
import com.hoopoe.zyfuntv.R
import com.hoopoe.zyfuntv.core.model.AnalyzeType
import com.hoopoe.zyfuntv.core.sync.SyncClient
import com.hoopoe.zyfuntv.core.sync.SyncServer
import com.hoopoe.zyfuntv.core.model.IptvType
import com.hoopoe.zyfuntv.core.model.ProxyType
import com.hoopoe.zyfuntv.core.model.SiteType
import com.hoopoe.zyfuntv.core.model.ThemeMode
import com.hoopoe.zyfuntv.core.model.UiMode
import com.hoopoe.zyfuntv.data.local.AnalyzeEntity
import com.hoopoe.zyfuntv.data.local.Converters
import com.hoopoe.zyfuntv.data.local.IptvEntity
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.component.mobile.EmptyHint
import com.hoopoe.zyfuntv.ui.screens.mobile.lab.LabPanel
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileSize


private val modeOptions = listOf(
    UiMode.AUTO to "自动",
    UiMode.TV to "电视端",
    UiMode.PHONE to "手机端"
)

private val themeOptions = listOf(
    ThemeMode.LIGHT to "明亮",
    ThemeMode.DARK to "黑暗",
    ThemeMode.SYSTEM to "跟随系统"
)

private val timeoutOptions = listOf(
    5000L to "5s",
    10000L to "10s",
    15000L to "15s",
    30000L to "30s"
)

private val proxyOptions = listOf(
    ProxyType.DIRECT to "直连",
    ProxyType.SYSTEM to "系统代理",
    ProxyType.CUSTOM to "自定义代理"
)

private val dnsOptions = listOf(
    "system" to "系统 DNS",
    "custom" to "自定义 DNS"
)

private val siteTypeOptions = listOf(
    SiteType.T1_JSON to "T1 JSON",
    SiteType.T0_XML to "T0 XML",
    SiteType.T4_CATVOD to "CatVod",
    SiteType.T3_XBPQ to "XBPQ",
    SiteType.T3_APPYSV2 to "AppYsV2",
    SiteType.T3_ALIST to "Alist"
)

/** 对齐 Windows 端的五大配置分组；手机端顶部 Tab 与 TV 端左栏共用同一套过滤映射 */
private val settingCategories = listOf(
    "基础配置", "影视配置", "电视配置", "解析配置", "网盘配置"
)

private data class ConfirmAction(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)

@Composable
fun SettingScreen(
    settings: AppSettings,
    repo: AppRepository,
    onSetUiMode: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
    category: String? = null,
    compact: Boolean = false
) {
    val sites by repo.sites.collectAsState(emptyList())
    val iptvs by repo.iptvs.collectAsState(emptyList())
    val channels by repo.channels.collectAsState(emptyList())
    val analyzes by repo.analyzes.collectAsState(emptyList())
    val history by repo.history.collectAsState(emptyList())
    val stars by repo.stars.collectAsState(emptyList())

    // 手机端（category 为 null）内部维护当前分组；TV 端由外部 TvSettingScreen 传入 category
    var phoneCat by remember { mutableStateOf(settingCategories.first()) }
    val effectiveCat = category ?: phoneCat

    // 构建期强制端（phone/tv 变体）下隐藏界面模式切换，端由变体锁定
    val forcedMode = BuildConfig.FORCED_UI_MODE.trim().lowercase()
    val uiModeLocked = forcedMode != UiMode.AUTO

    fun showBlock(key: String): Boolean = when (effectiveCat) {
        "基础配置" -> key in listOf("ui", "net", "epg", "data", "import", "advanced", "lab")
        "影视配置" -> key == "site"
        "电视配置" -> key == "iptv"
        "解析配置" -> key == "analyze"
        "网盘配置" -> key == "disk"
        else -> true
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var fileMsg by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }

    // 导出：SAF 创建文档后写入全量备份 JSON（偏好/源/历史/收藏）
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = repo.exportBackup()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("openOutputStream returned null")
                }.onSuccess {
                    fileMsg = context.getString(R.string.export_done, uri.lastPathSegment ?: "")
                    com.hoopoe.zyfuntv.core.notify.Notify.notify(
                        context, "备份导出完成", fileMsg
                    )
                }.onFailure {
                    fileMsg = context.getString(R.string.export_failed, it.message ?: "unknown")
                }
            }
        }
    }

    // 本地文件选择器：备份 JSON / TVBox / Catvod 文本，或 zip/gz/tar.gz 配置压缩包
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null) {
                    fileMsg = context.getString(R.string.import_failed)
                    return@launch
                }
                val name = queryDisplayName(context, uri) ?: ""
                val lower = name.lowercase()
                val isArchive = lower.endsWith(".zip") || lower.endsWith(".gz") ||
                    lower.endsWith(".tgz") || lower.endsWith(".tar.gz") || lower.endsWith(".7z")
                fileMsg = if (isArchive) {
                    val (sum, err) = repo.importArchiveBytes(name, bytes)
                    when {
                        err != null -> err
                        sum != null -> context.getString(
                            R.string.import_done,
                            sum.sites, sum.parses, sum.iptv, sum.channels
                        )
                        else -> context.getString(R.string.import_failed)
                    }
                } else {
                    val text = bytes.toString(Charsets.UTF_8)
                    val sum = runCatching { repo.importBackup(text) }.getOrNull()
                    when {
                        sum != null -> {
                            val base = context.getString(
                                R.string.import_backup_done,
                                sum.sites, sum.parses, sum.iptv, sum.history, sum.stars
                            )
                            if (sum.settingsApplied) "$base\n${context.getString(R.string.import_settings_hint)}" else base
                        }
                        else -> {
                            val cfg = runCatching { repo.importText(text) }.getOrNull()
                            if (cfg != null) {
                                context.getString(
                                    R.string.import_done, cfg.sites, cfg.parses, cfg.iptv, cfg.channels
                                )
                            } else context.getString(R.string.import_failed)
                        }
                    }
                }
            }
        }
    }

    var uaInput by remember(settings.ua) { mutableStateOf(settings.ua) }
    var epgInput by remember(settings.epg) { mutableStateOf(settings.epg) }
    var logoInput by remember(settings.logo) { mutableStateOf(settings.logo) }
    var proxyInput by remember(settings.customProxy) { mutableStateOf(settings.customProxy) }
    var dnsInput by remember(settings.dnsServer) { mutableStateOf(settings.dnsServer) }
    var pkgInput by remember(settings.externalPlayerPkg) { mutableStateOf(settings.externalPlayerPkg) }
    var davUrlInput by remember(settings.davUrl) { mutableStateOf(settings.davUrl) }
    var davUserInput by remember(settings.davUser) { mutableStateOf(settings.davUser) }
    var davPassInput by remember(settings.davPass) { mutableStateOf(settings.davPass) }
    var davPathInput by remember(settings.davPath) { mutableStateOf(settings.davPath) }
    var davMsg by remember { mutableStateOf<String?>(null) }
    var showLab by remember { mutableStateOf(false) }

    // 影视站点表单
    var showSiteForm by remember { mutableStateOf(false) }
    var siteEditId by remember { mutableStateOf<String?>(null) }
    var siteName by remember { mutableStateOf("") }
    var siteApi by remember { mutableStateOf("") }
    var siteType by remember { mutableIntStateOf(SiteType.T1_JSON) }
    var siteGroup by remember { mutableStateOf("") }
    var siteSearch by remember { mutableStateOf(true) }
    var sitePlayUrl by remember { mutableStateOf("") }
    var siteSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var siteQuery by remember { mutableStateOf("") }
    var siteMsg by remember { mutableStateOf<String?>(null) }

    // 直播源表单
    var showIptvForm by remember { mutableStateOf(false) }
    var iptvEditId by remember { mutableStateOf<String?>(null) }
    var iptvName by remember { mutableStateOf("") }
    var iptvApi by remember { mutableStateOf("") }
    var iptvType by remember { mutableIntStateOf(IptvType.REMOTE) }
    var iptvEpg by remember { mutableStateOf("") }
    var iptvLogo by remember { mutableStateOf("") }
    var iptvSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var iptvQuery by remember { mutableStateOf("") }

    // 解析源表单（设置页内）
    var showAnalyzeForm by remember { mutableStateOf(false) }
    var anEditId by remember { mutableStateOf<String?>(null) }
    var anName by remember { mutableStateOf("") }
    var anApi by remember { mutableStateOf("") }
    var anType by remember { mutableIntStateOf(AnalyzeType.JSON) }
    var anFlag by remember { mutableStateOf("") }
    var anSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var anQuery by remember { mutableStateOf("") }

    val compactTypography = MaterialTheme.typography.copy(
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
        titleSmall = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp, lineHeight = 15.sp),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
        bodySmall = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
        labelLarge = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, lineHeight = 15.sp),
        labelMedium = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 14.sp),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        displaySmall = MaterialTheme.typography.displaySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    )

    MaterialTheme(
        typography = if (compact) compactTypography else MaterialTheme.typography
    ) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = MobileSize.containerPadding,
                end = MobileSize.containerPadding,
                top = MobileSize.containerPadding,
                bottom = MobileSize.containerPadding + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)
    ) {
        // 手机端顶部分组 Tab；TV 端由外部左栏切换，不重复渲染
        if (category == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)
            ) {
                settingCategories.forEach { cat ->
                    ModeChip(
                        label = cat,
                        active = phoneCat == cat,
                        onClick = { phoneCat = cat }
                    )
                }
            }
        }

        if (showBlock("ui")) {
        SectionTitle("外观与界面")

        if (!uiModeLocked) {
            Text(
                text = "界面模式",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            ChipRow(
                options = modeOptions,
                active = settings.uiMode,
                onSelect = onSetUiMode
            )
            Text(
                text = when (settings.uiMode) {
                    UiMode.PHONE -> "当前：手机端布局（预留，后续细化）"
                    UiMode.TV -> "当前：电视端布局"
                    else -> "当前：自动判定（电视设备走电视端）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            text = "主题外观",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        ChipRow(
            options = themeOptions,
            active = settings.themeMode,
            onSelect = onSetThemeMode
        )

        Text(
            text = stringResource(R.string.setting_language),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)) {
            ModeChip(
                label = stringResource(R.string.lang_zh),
                active = settings.language != "en",
                onClick = { onSetLanguage("zh") }
            )
            ModeChip(
                label = stringResource(R.string.lang_en),
                active = settings.language == "en",
                onClick = { onSetLanguage("en") }
            )
        }

        }

        if (showBlock("site")) {
        fun resetSiteForm() {
            siteEditId = null
            siteName = ""; siteApi = ""; siteGroup = ""; sitePlayUrl = ""
            siteSearch = true; siteType = SiteType.T1_JSON
        }
        fun fillSiteForm(item: SiteEntity) {
            siteEditId = item.id
            siteName = item.name
            siteApi = item.api ?: ""
            siteType = item.type
            siteGroup = item.groupName ?: ""
            siteSearch = item.search
            sitePlayUrl = item.playUrl ?: ""
            showSiteForm = true
        }
        SectionTitle("影视配置（${sites.size}）")
        SourceManageTable(
            rows = sites.map { s ->
                TblRow(
                    id = s.id,
                    name = s.name,
                    typeLabel = siteTypeLabel(s.type),
                    groupLabel = s.groupName,
                    isActive = s.isActive,
                    isDefault = settings.defaultSite == s.id
                )
            },
            showGroup = true,
            showDetect = true,
            selected = siteSelected,
            onSelectChange = { siteSelected = it },
            query = siteQuery,
            onQueryChange = { siteQuery = it },
            onAdd = { if (!showSiteForm) resetSiteForm(); showSiteForm = true },
            onEnable = {
                if (siteSelected.isEmpty()) siteMsg = "请先勾选要启用的站点"
                else scope.launch {
                    siteSelected.forEach { id -> repo.setSiteActive(id, true) }
                    siteSelected = emptySet()
                }
            },
            onDisable = {
                if (siteSelected.isEmpty()) siteMsg = "请先勾选要停用的站点"
                else scope.launch {
                    siteSelected.forEach { id -> repo.setSiteActive(id, false) }
                    siteSelected = emptySet()
                }
            },
            onDelete = {
                if (siteSelected.isEmpty()) siteMsg = "请先勾选要删除的站点"
                else confirm = ConfirmAction("删除站点", "确定删除选中的 ${siteSelected.size} 个站点？") {
                    scope.launch {
                        siteSelected.forEach { id ->
                            sites.firstOrNull { it.id == id }?.let { repo.deleteSite(it) }
                        }
                        siteSelected = emptySet()
                    }
                }
            },
            onDetect = {
                scope.launch {
                    val targets = if (siteSelected.isEmpty()) sites else sites.filter { it.id in siteSelected }
                    var ok = 0
                    targets.forEach { runCatching { repo.fetchCategories(it) }.onSuccess { ok++ } }
                    siteMsg = "检测完成：$ok/${targets.size} 个站点可正常访问"
                }
            },
            onToggleActive = { id, active -> scope.launch { repo.setSiteActive(id, active) } },
            onSetDefault = { scope.launch { repo.setDefaultSite(it) } },
            onEdit = { id -> sites.firstOrNull { it.id == id }?.let { fillSiteForm(it) } },
            onDeleteOne = { id ->
                sites.firstOrNull { it.id == id }?.let { item ->
                    confirm = ConfirmAction("删除站点", "确定删除「${item.name}」？") {
                        scope.launch { repo.deleteSite(item) }
                    }
                }
            }
        )
        siteMsg?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        ModeChip(
            label = if (showSiteForm) "收起站点表单" else "▼ 站点表单",
            active = false,
            onClick = {
                if (!showSiteForm) resetSiteForm()
                showSiteForm = !showSiteForm
            }
        )
        if (showSiteForm) {
            OutlinedTextField(
                value = siteName,
                onValueChange = { siteName = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = siteApi,
                onValueChange = { siteApi = it },
                label = { Text("接口地址（api.php/provide/vod/）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("站点类型", style = MaterialTheme.typography.bodyMedium)
            ChipRow(
                options = siteTypeOptions,
                active = siteType,
                onSelect = { siteType = it }
            )
            OutlinedTextField(
                value = siteGroup,
                onValueChange = { siteGroup = it },
                label = { Text("分组（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sitePlayUrl,
                onValueChange = { sitePlayUrl = it },
                label = { Text("备用播放地址 playUrl（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("允许搜索", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ModeChip(
                    label = if (siteSearch) "开" else "关",
                    active = siteSearch,
                    onClick = { siteSearch = !siteSearch }
                )
            }
            ModeChip(
                label = if (siteEditId != null) "保存修改" else "保存站点",
                active = false,
                onClick = {
                    if (siteApi.isNotBlank()) {
                        scope.launch {
                            val edit = siteEditId?.let { id -> sites.firstOrNull { it.id == id } }
                            if (edit != null) {
                                repo.updateSite(edit, siteName, siteApi, siteType, siteGroup, siteSearch, sitePlayUrl)
                            } else {
                                repo.addSite(siteName, siteApi, siteType, siteGroup, siteSearch, sitePlayUrl)
                            }
                            resetSiteForm()
                            showSiteForm = false
                        }
                    }
                }
            )
        }

        }

        if (showBlock("iptv")) {
        fun resetIptvForm() {
            iptvEditId = null
            iptvName = ""; iptvApi = ""; iptvEpg = ""; iptvLogo = ""
            iptvType = IptvType.REMOTE
        }
        fun fillIptvForm(item: IptvEntity) {
            iptvEditId = item.id
            iptvName = item.name
            iptvApi = item.api
            iptvType = item.type
            iptvEpg = item.epg ?: ""
            iptvLogo = item.logo ?: ""
            showIptvForm = true
        }
        SectionTitle("直播源配置（${iptvs.size} · 频道 ${channels.size}）")
        SourceManageTable(
            rows = iptvs.map { i ->
                TblRow(
                    id = i.id,
                    name = i.name,
                    typeLabel = when (i.type) {
                        IptvType.REMOTE -> "远程"
                        IptvType.LOCAL -> "本地"
                        IptvType.MANUAL -> "手动"
                        else -> "远程"
                    },
                    groupLabel = null,
                    isActive = i.isActive,
                    isDefault = settings.defaultIptv == i.id
                )
            },
            showGroup = false,
            showDetect = false,
            selected = iptvSelected,
            onSelectChange = { iptvSelected = it },
            query = iptvQuery,
            onQueryChange = { iptvQuery = it },
            onAdd = { if (!showIptvForm) resetIptvForm(); showIptvForm = true },
            onEnable = {
                if (iptvSelected.isEmpty()) {
                } else scope.launch {
                    iptvSelected.forEach { id -> repo.setIptvActive(id, true) }
                    iptvSelected = emptySet()
                }
            },
            onDisable = {
                if (iptvSelected.isNotEmpty()) scope.launch {
                    iptvSelected.forEach { id -> repo.setIptvActive(id, false) }
                    iptvSelected = emptySet()
                }
            },
            onDelete = {
                if (iptvSelected.isEmpty()) {
                } else confirm = ConfirmAction("删除直播源", "确定删除选中的 ${iptvSelected.size} 个直播源？") {
                    scope.launch {
                        iptvSelected.forEach { id ->
                            iptvs.firstOrNull { it.id == id }?.let { repo.deleteIptv(it) }
                        }
                        iptvSelected = emptySet()
                    }
                }
            },
            onToggleActive = { id, active -> scope.launch { repo.setIptvActive(id, active) } },
            onSetDefault = { scope.launch { repo.setDefaultIptv(it) } },
            onEdit = { id -> iptvs.firstOrNull { it.id == id }?.let { fillIptvForm(it) } },
            onDeleteOne = { id ->
                iptvs.firstOrNull { it.id == id }?.let { item ->
                    confirm = ConfirmAction("删除直播源", "确定删除「${item.name}」？") {
                        scope.launch { repo.deleteIptv(item) }
                    }
                }
            }
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "频道 IP 标记",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.liveIpMark,
                onCheckedChange = { scope.launch { repo.setLiveIpMark(it) } }
            )
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "低延迟模式",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.liveDelay,
                onCheckedChange = { scope.launch { repo.setLiveDelay(it) } }
            )
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "频道缩略图",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.liveThumbnail,
                onCheckedChange = { scope.launch { repo.setLiveThumbnail(it) } }
            )
        }
        ModeChip(
            label = if (showIptvForm) "收起直播源表单" else "＋ 添加直播源",
            active = false,
            onClick = {
                if (!showIptvForm) resetIptvForm()
                showIptvForm = !showIptvForm
            }
        )
        if (showIptvForm) {
            OutlinedTextField(
                value = iptvName,
                onValueChange = { iptvName = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)) {
                ModeChip(
                    label = "远程链接",
                    active = iptvType == IptvType.REMOTE,
                    onClick = { iptvType = IptvType.REMOTE }
                )
                ModeChip(
                    label = "手动粘贴",
                    active = iptvType == IptvType.MANUAL,
                    onClick = { iptvType = IptvType.MANUAL }
                )
            }
            if (iptvType == IptvType.MANUAL) {
                OutlinedTextField(
                    value = iptvApi,
                    onValueChange = { iptvApi = it },
                    label = { Text("M3U / TXT 频道文本（#EXTM3U 或 频道名,地址）") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = iptvApi,
                    onValueChange = { iptvApi = it },
                    label = { Text("直播源链接（m3u/m3u8/txt）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = iptvEpg,
                onValueChange = { iptvEpg = it },
                label = { Text("EPG 模板（可选，{name} {date}）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = iptvLogo,
                onValueChange = { iptvLogo = it },
                label = { Text("台标模板（可选，{name}）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ModeChip(
                label = if (iptvEditId != null) "保存修改" else "保存直播源",
                active = false,
                onClick = {
                    if (iptvApi.isNotBlank()) {
                        scope.launch {
                            val edit = iptvEditId?.let { id -> iptvs.firstOrNull { it.id == id } }
                            if (edit != null) {
                                repo.updateIptv(edit, iptvName, iptvApi, iptvType, iptvEpg, iptvLogo)
                            } else {
                                repo.addIptv(iptvName, iptvApi, iptvType, iptvEpg, iptvLogo)
                            }
                            resetIptvForm()
                            showIptvForm = false
                        }
                    }
                }
            )
        }

        }

        if (showBlock("analyze")) {
        fun resetAnalyzeForm() {
            anEditId = null
            anName = ""; anApi = ""; anFlag = ""
            anType = AnalyzeType.JSON
        }
        fun fillAnalyzeForm(item: AnalyzeEntity) {
            anEditId = item.id
            anName = item.name
            anApi = item.api
            anType = item.type
            anFlag = item.flag ?: ""
            showAnalyzeForm = true
        }
        SectionTitle("解析配置（${analyzes.size}）")
        SourceManageTable(
            rows = analyzes.map { a ->
                TblRow(
                    id = a.id,
                    name = a.name,
                    typeLabel = if (a.type == AnalyzeType.JSON) "JSON 接口" else "网页嗅探",
                    groupLabel = a.flag,
                    isActive = a.isActive,
                    isDefault = settings.defaultAnalyze == a.id
                )
            },
            showGroup = false,
            showDetect = false,
            selected = anSelected,
            onSelectChange = { anSelected = it },
            query = anQuery,
            onQueryChange = { anQuery = it },
            onAdd = { if (!showAnalyzeForm) resetAnalyzeForm(); showAnalyzeForm = true },
            onEnable = {
                if (anSelected.isNotEmpty()) scope.launch {
                    anSelected.forEach { id -> repo.setAnalyzeActive(id, true) }
                    anSelected = emptySet()
                }
            },
            onDisable = {
                if (anSelected.isNotEmpty()) scope.launch {
                    anSelected.forEach { id -> repo.setAnalyzeActive(id, false) }
                    anSelected = emptySet()
                }
            },
            onDelete = {
                if (anSelected.isEmpty()) {
                } else confirm = ConfirmAction("删除解析源", "确定删除选中的 ${anSelected.size} 个解析源？") {
                    scope.launch {
                        anSelected.forEach { id ->
                            analyzes.firstOrNull { it.id == id }?.let { repo.deleteAnalyze(it) }
                        }
                        anSelected = emptySet()
                    }
                }
            },
            onToggleActive = { id, active -> scope.launch { repo.setAnalyzeActive(id, active) } },
            onSetDefault = { scope.launch { repo.setDefaultAnalyze(it) } },
            onEdit = { id -> analyzes.firstOrNull { it.id == id }?.let { fillAnalyzeForm(it) } },
            onDeleteOne = { id ->
                analyzes.firstOrNull { it.id == id }?.let { item ->
                    confirm = ConfirmAction("删除解析源", "确定删除「${item.name}」？") {
                        scope.launch { repo.deleteAnalyze(item) }
                    }
                }
            }
        )
        ModeChip(
            label = if (showAnalyzeForm) "收起解析源表单" else "＋ 添加解析源",
            active = false,
            onClick = {
                if (!showAnalyzeForm) resetAnalyzeForm()
                showAnalyzeForm = !showAnalyzeForm
            }
        )
        if (showAnalyzeForm) {
            OutlinedTextField(
                value = anName,
                onValueChange = { anName = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = anApi,
                onValueChange = { anApi = it },
                label = { Text("解析接口地址（前缀，调用时拼接播放地址）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)) {
                ModeChip(
                    label = "JSON 接口",
                    active = anType == AnalyzeType.JSON,
                    onClick = { anType = AnalyzeType.JSON }
                )
                ModeChip(
                    label = "网页嗅探",
                    active = anType == AnalyzeType.WEB,
                    onClick = { anType = AnalyzeType.WEB }
                )
            }
            OutlinedTextField(
                value = anFlag,
                onValueChange = { anFlag = it },
                label = { Text("适配线路名（可选，逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ModeChip(
                label = if (anEditId != null) "保存修改" else "保存解析源",
                active = false,
                onClick = {
                    if (anApi.isNotBlank()) {
                        scope.launch {
                            val edit = anEditId?.let { id -> analyzes.firstOrNull { it.id == id } }
                            if (edit != null) {
                                repo.updateAnalyze(edit, anName, anApi, anType, anFlag.trim().ifBlank { null })
                            } else {
                                repo.addAnalyze(anName, anApi, anType, anFlag.trim().ifBlank { null })
                            }
                            resetAnalyzeForm()
                            showAnalyzeForm = false
                        }
                    }
                }
            )
        }

        }

        if (showBlock("net")) {
        SectionTitle("网络与播放")

        Text("User-Agent", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uaInput,
                onValueChange = { uaInput = it },
                label = { Text("留空使用默认 UA") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            ModeChip("保存", active = false) {
                scope.launch { repo.setUa(uaInput.trim()) }
            }
        }

        Text("超时时间", style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            options = timeoutOptions,
            active = settings.timeoutMs,
            onSelect = { scope.launch { repo.setTimeoutMs(it) } }
        )

        Text("代理", style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            options = proxyOptions,
            active = settings.proxyType,
            onSelect = { scope.launch { repo.setProxyType(it) } }
        )
        if (settings.proxyType == ProxyType.CUSTOM) {
            OutlinedTextField(
                value = proxyInput,
                onValueChange = { proxyInput = it },
                label = { Text("自定义代理地址（host:port）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ModeChip("保存代理", active = false) {
                scope.launch { repo.setCustomProxy(proxyInput.trim()) }
            }
        }

        Text("DNS", style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            options = dnsOptions,
            active = settings.dnsMode,
            onSelect = { scope.launch { repo.setDnsMode(it) } }
        )
        if (settings.dnsMode == "custom") {
            OutlinedTextField(
                value = dnsInput,
                onValueChange = { dnsInput = it },
                label = { Text("自定义 DNS（IP 或 DoH 地址，本期仅保存）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ModeChip("保存 DNS", active = false) {
                scope.launch { repo.setDnsServer(dnsInput.trim()) }
            }
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("忽略 HTTPS 证书校验", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "兼容证书链不全或自签的第三方解析/采集源；开启后跳过证书校验，存在安全风险",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = settings.ignoreSsl, onCheckedChange = { scope.launch { repo.setIgnoreSsl(it) } })
        }

        Text("播放器内核", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("内置 / 外部系统播放器", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            ModeChip(
                label = "内置",
                active = !settings.useExternalPlayer,
                onClick = { scope.launch { repo.setUseExternalPlayer(false) } }
            )
            ModeChip(
                label = "外部",
                active = settings.useExternalPlayer,
                onClick = { scope.launch { repo.setUseExternalPlayer(true) } }
            )
        }
        if (settings.useExternalPlayer) {
            ExternalPlayerPicker(
                currentPkg = pkgInput,
                onPick = { pkgInput = it }
            )
            OutlinedTextField(
                value = pkgInput,
                onValueChange = { pkgInput = it },
                label = { Text("外部播放器包名（留空由系统选择器；如 org.videolan.vlc / com.mxtech.videoplayer.ad）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ModeChip(
                label = "保存包名",
                active = false,
                onClick = { scope.launch { repo.setExternalPlayerPkg(pkgInput) } }
            )
        }
        Text("播放行为", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("自动连播下一集", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = settings.autoPlayNext, onCheckedChange = { scope.launch { repo.setAutoPlayNext(it) } })
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("后台保持播放", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = settings.keepBackground, onCheckedChange = { scope.launch { repo.setKeepBackground(it) } })
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("弹幕（数据源二期接入）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = settings.danmuEnabled, onCheckedChange = { scope.launch { repo.setDanmuEnabled(it) } })
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("调试日志", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.debug,
                onCheckedChange = { scope.launch { repo.setDebug(it) } }
            )
        }

        }

        if (showBlock("epg")) {
        SectionTitle("直播源 EPG 与图标")
        OutlinedTextField(
            value = epgInput,
            onValueChange = { epgInput = it },
            label = { Text("EPG 模板（{name} {date}）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ModeChip("保存 EPG", active = false) {
            scope.launch { repo.setEpg(epgInput.trim()) }
        }
        OutlinedTextField(
            value = logoInput,
            onValueChange = { logoInput = it },
            label = { Text("台标模板（{name}）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ModeChip("保存台标", active = false) {
            scope.launch { repo.setLogo(logoInput.trim()) }
        }

        }

        if (showBlock("data")) {
        SectionTitle("数据")
        SettingRowWithAction(
            label = "历史记录",
            value = "${history.size} 条",
            actionLabel = if (history.isEmpty()) null else "清空",
            onAction = {
                confirm = ConfirmAction("清空历史记录", "确定清空全部历史记录吗？此操作不可恢复。") {
                    scope.launch { repo.clearHistory() }
                }
            }
        )
        SettingRowWithAction(
            label = "收藏夹",
            value = "${stars.size} 条",
            actionLabel = if (stars.isEmpty()) null else "清空",
            onAction = {
                confirm = ConfirmAction("清空收藏夹", "确定清空全部收藏吗？此操作不可恢复。") {
                    scope.launch { repo.clearStars() }
                }
            }
        )
        SettingRowWithAction(
            label = "影视站点",
            value = "${sites.size} 个",
            actionLabel = if (sites.isEmpty()) null else "清空",
            onAction = {
                confirm = ConfirmAction("清空影视站点", "确定清空全部影视站点配置吗？此操作不可恢复。") {
                    scope.launch { repo.clearSites() }
                }
            }
        )
        SettingRowWithAction(
            label = "直播源",
            value = "${iptvs.size} 个",
            actionLabel = if (iptvs.isEmpty()) null else "清空",
            onAction = {
                confirm = ConfirmAction("清空直播源", "确定清空全部直播源与频道吗？此操作不可恢复。") {
                    scope.launch { repo.clearIptvs() }
                }
            }
        )
        SettingRowWithAction(
            label = "解析源",
            value = "${analyzes.size} 个",
            actionLabel = if (analyzes.isEmpty()) null else "清空",
            onAction = {
                confirm = ConfirmAction("清空解析源", "确定清空全部解析源吗？此操作不可恢复。") {
                    scope.launch { repo.clearAnalyzes() }
                }
            }
        )
        SettingRowWithAction(
            label = "恢复出厂",
            value = "清空全部源 / 历史 / 收藏并还原默认设置",
            actionLabel = "重置",
            onAction = {
                confirm = ConfirmAction(
                    "恢复出厂",
                    "将清空全部站点、直播源、解析源、历史与收藏，并还原默认设置。确定继续吗？"
                ) { scope.launch { repo.resetAll() } }
            }
        )

        }

        if (showBlock("import")) {
        SectionTitle("配置导入")
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("TVBox / Catvod 配置链接") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ModeChip(
            label = if (importing) "导入中…" else "开始导入",
            active = false,
            onClick = {
                val url = urlInput.trim()
                if (url.isNotEmpty() && !importing) {
                    scope.launch {
                        importing = true
                        importMsg = null
                        val sum = repo.importConfigUrl(url)
                        importMsg = if (sum != null) {
                            "导入成功：站点 ${sum.sites} · 解析 ${sum.parses} · 直播源 ${sum.iptv} · 频道 ${sum.channels}"
                        } else {
                            "导入失败：无法获取或解析该配置链接"
                        }
                        importing = false
                    }
                }
            }
        )
        ModeChip(
            label = stringResource(R.string.setting_import_local),
            active = false,
            onClick = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )
        importMsg?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        ModeChip(
            label = stringResource(R.string.setting_export),
            active = false,
            onClick = { exportLauncher.launch("zyfuntv_backup.json") }
        )
        fileMsg?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (category != null) {
            TvSyncReceivePanel(repo = repo, modifier = Modifier.fillMaxWidth())
        } else {
            PhoneSyncPushPanel(repo = repo, scope = scope, modifier = Modifier.fillMaxWidth())
        }

        }

        if (showBlock("advanced")) {
        SectionTitle("高级")
        Text(stringResource(R.string.setting_webdav), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = davUrlInput,
            onValueChange = { davUrlInput = it },
            label = { Text("WebDAV 服务器地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
            ModeChip(
                label = "保存配置",
                active = false,
                onClick = {
                    scope.launch {
                        repo.setDavConfig(davUrlInput, davUserInput, davPassInput, davPathInput)
                        davMsg = "WebDAV 配置已保存"
                    }
                }
            )
            ModeChip(
                label = "备份到 WebDAV",
                active = false,
                onClick = {
                    scope.launch {
                        val ok = repo.backupToWebDav()
                        davMsg = if (ok) "已备份到 WebDAV" else "备份失败：未配置或网络错误"
                    }
                }
            )
            ModeChip(
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
        SettingRowWithAction(
            label = stringResource(R.string.setting_check_update),
            value = stringResource(R.string.hint_coming_soon),
            actionLabel = null,
            onAction = {}
        )

        }

        if (showBlock("lab")) {
        SectionTitle("实验室")
        if (showLab) {
            LabPanel(repo = repo, onBack = { showLab = false })
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showLab = true }
                    .padding(horizontal = MobileSize.containerPadding, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("实验室", color = MaterialTheme.colorScheme.onSurface)
            }
        }
        }

        if (showBlock("disk")) {
            DiskPanel(repo = repo, settings = settings, modifier = Modifier.fillMaxWidth())
        }
    }
    }

    // 危险操作确认弹窗
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.message) },
            confirmButton = {
                TextButton(onClick = { c.onConfirm(); confirm = null }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("取消") }
            }
        )
    }
}

/** 选项 chip 组：竖屏宽度不足时自动换行，避免横向溢出 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    active: T,
    onSelect: (T) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin),
        verticalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)
    ) {
        options.forEach { (value, label) ->
            ModeChip(
                label = label,
                active = active == value,
                onClick = { onSelect(value) }
            )
        }
    }
}

@Composable
private fun ModeChip(
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

@Composable
private fun SettingRowWithAction(
    label: String,
    value: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = MobileSize.containerPadding, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** TV 端：开启局域网同步服务，展示地址与配对码，接收手机推送并落库 */
@Composable
private fun TvSyncReceivePanel(repo: AppRepository, modifier: Modifier = Modifier) {
    var lastResult by remember { mutableStateOf<String?>(null) }
    val server = remember {
        SyncServer(
            deviceName = android.os.Build.MODEL,
            onImport = { text ->
                val sum = repo.importBackup(text)
                lastResult = "已同步：站点 ${sum.sites} · 解析 ${sum.parses} · 直播源 ${sum.iptv} · 频道 ${sum.channels} · 历史 ${sum.history} · 收藏 ${sum.stars}"
                sum
            },
            onExport = { repo.exportBackup() }
        )
    }
    var ip by remember { mutableStateOf<String?>(null) }
    var port by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val started = server.start()
        ip = started
        port = server.port
        code = server.code
        onDispose { server.stop() }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)
    ) {
        SectionTitle("局域网同步 · 接收")
        if (ip == null) {
            Text(
                "未获取到局域网地址，请确认电视已连接网络",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Text(
                "设备：${android.os.Build.MODEL}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "地址：http://$ip:$port",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(MobileSize.middleRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = MobileSize.containerPadding, vertical = 12.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)) {
                ModeChip(
                    label = "刷新配对码",
                    active = false,
                    onClick = {
                        server.rotateCode()
                        code = server.code
                    }
                )
            }
            Text(
                "在手机端「设置 → 配置导入」输入上述地址与配对码，可推送本机配置到电视，或从电视拉取配置覆盖本机",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            lastResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 手机端：输入电视地址与配对码，握手后把本机全量备份推送到电视 */
@Composable
private fun PhoneSyncPushPanel(
    repo: AppRepository,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var endpointInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<SyncClient.DiscoveredDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MobileSize.smallMargin)
    ) {
        SectionTitle("与电视同步")
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                val selected = devices.firstOrNull { "${it.host}:${it.port}" == endpointInput }
                TextButton(onClick = { menuExpanded = true }) {
                    Text(
                        selected?.let { "${it.device}（${it.host}:${it.port}）" }
                            ?: "选择已发现的电视"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (devices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂未发现设备，请先扫描") },
                            onClick = { menuExpanded = false }
                        )
                    } else {
                        devices.forEach { d ->
                            DropdownMenuItem(
                                text = { Text("${d.device}（${d.host}:${d.port}）") },
                                onClick = {
                                    endpointInput = "${d.host}:${d.port}"
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            ModeChip(
                label = if (scanning) "扫描中…" else "扫描局域网",
                active = scanning,
                onClick = {
                    if (scanning) return@ModeChip
                    scope.launch {
                        scanning = true
                        result = null
                        val found = withContext(Dispatchers.IO) { SyncClient.discover() }
                        devices = found
                        scanning = false
                        result = if (found.isEmpty()) {
                            "未发现同局域网电视，可在下方手动输入地址"
                        } else {
                            "发现 ${found.size} 台设备，请在下拉中选择"
                        }
                    }
                }
            )
        }
        OutlinedTextField(
            value = endpointInput,
            onValueChange = { endpointInput = it },
            label = { Text("电视地址（IP 或 IP:端口，可手填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text("电视端配对码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MobileSize.miniMargin)) {
            ModeChip(
                label = "连接测试",
                active = false,
                onClick = {
                    val ep = SyncClient.parseEndpoint(endpointInput)
                    if (ep == null) {
                        result = "地址格式不正确"
                    } else {
                        scope.launch {
                            busy = true
                            val probe = withContext(Dispatchers.IO) { SyncClient.probe(ep, codeInput) }
                            result = probe.message
                            busy = false
                        }
                    }
                }
            )
            ModeChip(
                label = if (busy) "请稍候…" else "一键推送",
                active = busy,
                onClick = {
                    val ep = SyncClient.parseEndpoint(endpointInput)
                    if (ep == null) {
                        result = "地址格式不正确"
                    } else if (!busy) {
                        scope.launch {
                        busy = true
                        result = null
                        val probe = withContext(Dispatchers.IO) { SyncClient.probe(ep, codeInput) }
                        if (!probe.ok) {
                            result = probe.message
                            busy = false
                            return@launch
                        }
                        val backup = repo.exportBackup()
                        val res = withContext(Dispatchers.IO) { SyncClient.push(ep, codeInput, backup) }
                        result = if (res.ok) buildString {
                            append(res.message)
                            res.summary?.let { s ->
                                append("\n站点 ${s.sites} · 解析 ${s.parses} · 直播源 ${s.iptv} · 频道 ${s.channels} · 历史 ${s.history} · 收藏 ${s.stars}")
                                if (s.settingsApplied) append("\n设置项已一并还原")
                            }
                        } else res.message
                        busy = false
                    }
                    }
                }
            )
            ModeChip(
                label = if (busy) "请稍候…" else "从电视拉取",
                active = busy,
                onClick = {
                    val ep = SyncClient.parseEndpoint(endpointInput)
                    if (ep == null) {
                        result = "地址格式不正确"
                    } else if (!busy) {
                        scope.launch {
                            busy = true
                            result = null
                            val probe = withContext(Dispatchers.IO) { SyncClient.probe(ep, codeInput) }
                            if (!probe.ok) {
                                result = probe.message
                                busy = false
                                return@launch
                            }
                            val pull = withContext(Dispatchers.IO) { SyncClient.pull(ep, codeInput) }
                            if (!pull.ok || pull.backup == null) {
                                result = pull.message
                                busy = false
                                return@launch
                            }
                            val sum = repo.importBackup(pull.backup)
                            result = buildString {
                                append("已从电视拉取并覆盖本机")
                                append("\n站点 ${sum.sites} · 解析 ${sum.parses} · 直播源 ${sum.iptv} · 频道 ${sum.channels} · 历史 ${sum.history} · 收藏 ${sum.stars}")
                                if (sum.settingsApplied) append("\n设置项已一并还原")
                            }
                            busy = false
                        }
                    }
                }
            )
        }
        Text(
            "推送：用本机配置覆盖电视；拉取：用电视配置覆盖本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        result?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

/** 查询 SAF 选中文件的显示名（用于判别压缩包扩展名） */
private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
}

/** 列出本机可播放视频的已安装应用，供外部播放器内核选择；查不到时仍可手填包名兜底 */
@Composable
private fun ExternalPlayerPicker(currentPkg: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val players = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://"), "video/*")
        }
        pm.queryIntentActivities(intent, 0)
            .map { ri -> ri.activityInfo.packageName to ri.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
    val currentLabel = players.firstOrNull { it.first == currentPkg }?.second
        ?: currentPkg.ifBlank { "系统默认选择器" }
    Box {
        ModeChip(
            label = "选择已安装播放器：$currentLabel",
            active = currentPkg.isNotBlank(),
            onClick = { menuExpanded = true }
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("系统默认选择器（不指定包名）") },
                onClick = { onPick(""); menuExpanded = false }
            )
            players.forEach { (pkg, label) ->
                DropdownMenuItem(
                    text = { Text("$label（$pkg）") },
                    onClick = { onPick(pkg); menuExpanded = false }
                )
            }
        }
    }
}
