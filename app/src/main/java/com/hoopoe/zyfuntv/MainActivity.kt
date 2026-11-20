package com.hoopoe.zyfuntv

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.hoopoe.zyfuntv.core.model.ThemeMode
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.prefs.SettingsRepository
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.ZyfunApp
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** 启动前按持久化语言应用 locale（同步读 SP 缓存，避免等待异步 DataStore） */
    override fun attachBaseContext(newBase: Context) {
        val sp = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val lang = sp.getString("language", "zh") ?: "zh"
        val locale = Locale(if (lang == "en") "en" else "zh")
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.hoopoe.zyfuntv.core.notify.Notify.ensureChannels(applicationContext)
        val incomingLink = intent?.data?.toString().takeIf { !it.isNullOrBlank() }
        setContent {
            val settingsRepo = remember { SettingsRepository(applicationContext) }
            val repo = remember { AppRepository(applicationContext, settingsRepo) }
            val scope = rememberCoroutineScope()
            val settings by settingsRepo.settings.collectAsState(initial = AppSettings())

            // 启动深链（zy://subscribe 或 http(s) 订阅链接）一次性导入
            if (incomingLink != null) {
                LaunchedEffect(Unit) {
                    runCatching { repo.importDeepLink(incomingLink) }
                }
            }
            val darkTheme = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                else -> false
            }
            ZyfunApp(
                settings = settings,
                repo = repo,
                onSetUiMode = { mode -> scope.launch { settingsRepo.setUiMode(mode) } },
                onSetThemeMode = { mode -> scope.launch { settingsRepo.setThemeMode(mode) } },
                onSetLanguage = { lang ->
                    scope.launch {
                        settingsRepo.setLanguage(lang)
                        recreate()
                    }
                },
                darkTheme = darkTheme
            )
        }
    }
}
