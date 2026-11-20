package com.hoopoe.zyfuntv.ui.screens.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.core.model.SiteType
import com.hoopoe.zyfuntv.core.model.ThemeMode
import com.hoopoe.zyfuntv.core.model.UiMode
import com.hoopoe.zyfuntv.data.local.SiteEntity
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.component.tv.TvTopActionRow
import com.hoopoe.zyfuntv.ui.nav.Dest
import com.hoopoe.zyfuntv.ui.screens.mobile.CloudScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.PlaceholderScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.RankScreen
import com.hoopoe.zyfuntv.ui.screens.tv.components.TvSideBar
import com.hoopoe.zyfuntv.ui.screens.tv.home.TvHomeScreen
import com.hoopoe.zyfuntv.ui.screens.tv.home.TvIndexScreen
import com.hoopoe.zyfuntv.ui.screens.tv.live.TvLiveScreen
import com.hoopoe.zyfuntv.ui.screens.tv.moment.TvMomentScreen
import com.hoopoe.zyfuntv.ui.screens.tv.parse.TvParseScreen
import com.hoopoe.zyfuntv.ui.screens.tv.setting.TvSettingScreen
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize

/**
 * TV 端独立根壳：左侧竖排导航栏 + 右侧大屏内容路由。
 * 与手机端布局并行，互不影响；数据复用同一套 repo / model / 播放器。
 */
@Composable
fun TvRootScreen(
    settings: AppSettings,
    repo: AppRepository,
    onSetUiMode: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    darkTheme: Boolean = false,
    initialVod: PendingVod? = null,
    onConsumeVod: () -> Unit = {},
    onOpenVod: (PendingVod) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf(Dest.Home) }
    var pendingVod by remember { mutableStateOf<PendingVod?>(null) }

    val sites by repo.sites.collectAsState(emptyList())
    // 影视源排除 Alist 网盘，网盘仅在网盘专区/网盘配置中展示
    val filmSites = sites.filter { it.type != SiteType.T3_ALIST }
    var selectedSite by remember { mutableStateOf<SiteEntity?>(null) }

    LaunchedEffect(filmSites) {
        if (selectedSite == null && filmSites.isNotEmpty()) {
            val defId = settings.defaultSite
            selectedSite = filmSites.firstOrNull { it.id == defId && it.isActive }
                ?: filmSites.firstOrNull { it.isActive }
                ?: filmSites.first()
        }
    }

    // 遥控器 / 系统返回键：非主页先回到首页，主页由系统处理退出
    BackHandler(enabled = current != Dest.Home) { current = Dest.Home }

    val openVod: (PendingVod) -> Unit = {
        pendingVod = it
        current = Dest.Film
        onOpenVod(it)
    }

    val toggleTheme: () -> Unit = {
        onSetThemeMode(if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 侧栏：首页 + 主导航（设置 / 端切换收进顶部快捷行，不在侧栏重复）
        TvSideBar(
            items = listOf(Dest.Home) + Dest.navItems.filter { it != Dest.Setting },
            current = current,
            onSelect = { current = it },
            sites = filmSites,
            selectedSite = selectedSite,
            onSiteChange = { selectedSite = it }
        )

        Column(Modifier.weight(1f).fillMaxHeight()) {
            // 顶部全局快捷操作：设置 / 手机端切换 / 明暗主题，右上一排
            TvTopActionRow(
                onOpenSetting = { current = Dest.Setting },
                onSwitchToPhone = { onSetUiMode(UiMode.PHONE) },
                darkTheme = darkTheme,
                onToggleTheme = toggleTheme,
                modifier = Modifier.padding(horizontal = TvSize.containerPadding)
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                Dest.Home -> TvIndexScreen(modifier = Modifier.fillMaxSize())
                Dest.Film -> TvHomeScreen(
                    repo = repo,
                    sites = filmSites,
                    selectedSite = selectedSite,
                    onSiteChange = { selectedSite = it },
                    initialVod = pendingVod,
                    onConsumeVod = { pendingVod = null; onConsumeVod() },
                    modifier = Modifier.fillMaxSize()
                )
                Dest.Live -> TvLiveScreen(
                    settings = settings,
                    repo = repo,
                    modifier = Modifier.fillMaxSize()
                )
                Dest.Rank -> RankScreen(
                    repo = repo,
                    onOpenVod = openVod,
                    isPhone = false,
                    modifier = Modifier.fillMaxSize()
                )
                Dest.Parse -> TvParseScreen(
                    settings = settings,
                    repo = repo,
                    modifier = Modifier.fillMaxSize()
                )
                Dest.Cloud -> CloudScreen(
                    repo = repo,
                    settings = settings,
                    onOpenVod = openVod,
                    modifier = Modifier.fillMaxSize()
                )
                Dest.Moment -> TvMomentScreen(
                    repo = repo,
                    onOpenVod = openVod,
                    modifier = Modifier.fillMaxSize()
                )
                Dest.Setting -> TvSettingScreen(
                    settings = settings,
                    repo = repo,
                    onSetUiMode = onSetUiMode,
                    onSetThemeMode = onSetThemeMode,
                    onSetLanguage = onSetLanguage,
                    modifier = Modifier.fillMaxSize()
                )
                Dest.Lab -> PlaceholderScreen(Dest.Lab, Modifier.fillMaxSize())
            }
        }
    }
    }
}
