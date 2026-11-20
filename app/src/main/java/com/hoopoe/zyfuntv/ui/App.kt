package com.hoopoe.zyfuntv.ui

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hoopoe.zyfuntv.BuildConfig
import com.hoopoe.zyfuntv.core.model.UiMode
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.component.mobile.PhoneNavBar
import com.hoopoe.zyfuntv.ui.nav.Dest
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileAppTheme
import com.hoopoe.zyfuntv.ui.theme.tv.TvAppTheme
import com.hoopoe.zyfuntv.ui.screens.tv.TvRootScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.CloudScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.FilmScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.LiveScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.MomentScreen
import com.hoopoe.zyfuntv.core.model.PendingVod
import com.hoopoe.zyfuntv.ui.screens.mobile.ParseScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.PlaceholderScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.RankScreen
import com.hoopoe.zyfuntv.ui.screens.mobile.SettingScreen

@Composable
fun ZyfunApp(
    settings: AppSettings,
    repo: AppRepository,
    onSetUiMode: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    darkTheme: Boolean
) {
    val context = LocalContext.current
    val isTvDevice = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    // 变体强制端：phone / tv 变体在构建期锁定，不读用户设置；universal(auto) 走设置 + 设备判定。
    val forcedMode = BuildConfig.FORCED_UI_MODE.trim().lowercase()
    val normalizedMode = settings.uiMode.trim().lowercase()
    val effectiveMode = when (forcedMode) {
        UiMode.PHONE -> UiMode.PHONE
        UiMode.TV -> UiMode.TV
        else -> when (normalizedMode) {
            UiMode.PHONE -> UiMode.PHONE
            UiMode.TV -> UiMode.TV
            // 仅「自动」模式按设备能力判定：带 LEANBACK 的电视设备走 TV，其余走手机端。
            else -> if (isTvDevice) UiMode.TV else UiMode.PHONE
        }
    }
    val isPhone = effectiveMode == UiMode.PHONE
    var current by remember { mutableStateOf(Dest.Film) }
    var pendingVod by remember { mutableStateOf<PendingVod?>(null) }
    val items = Dest.navItems

    // 遥控器 / 系统返回键：非主页先回到影视主页，主页由系统处理退出
    BackHandler(enabled = current != Dest.Film) { current = Dest.Film }

    // 手机端不锁死方向（横屏沿用手机布局），电视端保持横屏
    LaunchedEffect(isPhone) {
        val activity = context as? ComponentActivity
        activity?.requestedOrientation = if (isPhone) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    val openVod: (PendingVod) -> Unit = {
        pendingVod = it
        current = Dest.Film
    }

    when (effectiveMode) {
        UiMode.PHONE -> MobileAppTheme(darkTheme = darkTheme) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AppContent(
                    dest = current,
                    settings = settings,
                    repo = repo,
                    onSetUiMode = onSetUiMode,
                    onSetThemeMode = onSetThemeMode,
                    onSetLanguage = onSetLanguage,
                    initialVod = pendingVod,
                    onConsumeVod = { pendingVod = null },
                    onOpenVod = openVod,
                    isPhone = isPhone,
                    onNavigate = { current = it },
                    modifier = Modifier.weight(1f)
                )
                PhoneNavBar(items, current, onSelect = { current = it })
            }
        }
        else -> TvAppTheme(darkTheme = darkTheme) {
            TvRootScreen(
                settings = settings,
                repo = repo,
                onSetUiMode = onSetUiMode,
                onSetThemeMode = onSetThemeMode,
                onSetLanguage = onSetLanguage,
                darkTheme = darkTheme,
                initialVod = pendingVod,
                onConsumeVod = { pendingVod = null },
                onOpenVod = openVod,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AppContent(
    dest: Dest,
    settings: AppSettings,
    repo: AppRepository,
    onSetUiMode: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    initialVod: PendingVod?,
    onConsumeVod: () -> Unit,
    onOpenVod: (PendingVod) -> Unit,
    isPhone: Boolean,
    onNavigate: (Dest) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (dest) {
            Dest.Home -> PlaceholderScreen(dest, Modifier.fillMaxSize())
            Dest.Film -> FilmScreen(repo, initialVod, onConsumeVod, isPhone, Modifier.fillMaxSize(), onSwitchToTv = { onSetUiMode(UiMode.TV) })
            Dest.Live -> LiveScreen(settings, repo, isPhone, Modifier.fillMaxSize())
            Dest.Rank -> RankScreen(repo, onOpenVod, isPhone, Modifier.fillMaxSize())
            Dest.Parse -> ParseScreen(settings, repo, isPhone, Modifier.fillMaxSize())
            Dest.Cloud -> CloudScreen(repo, settings, onOpenVod, Modifier.fillMaxSize())
            Dest.Moment -> MomentScreen(repo, onOpenVod, isPhone, Modifier.fillMaxSize())
            Dest.Lab -> PlaceholderScreen(dest, Modifier.fillMaxSize())
            Dest.Setting -> SettingScreen(settings, repo, onSetUiMode, onSetThemeMode, onSetLanguage, Modifier.fillMaxSize())
        }
    }
}
