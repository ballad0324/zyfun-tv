package com.hoopoe.zyfuntv.ui.screens.tv.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.screens.mobile.SettingScreen
import com.hoopoe.zyfuntv.ui.screens.tv.components.TvFocusItem
import com.hoopoe.zyfuntv.ui.theme.tv.TvSize

/**
 * TV 设置大屏：左分类栏对齐 Windows 端五大配置分组，右侧复用现有设置表单。
 * 基础配置 / 影视配置 / 电视配置 / 解析配置 / 网盘配置。
 */
private val SETTING_CATEGORIES = listOf(
    "基础配置", "影视配置", "电视配置", "解析配置", "网盘配置"
)

@Composable
fun TvSettingScreen(
    settings: AppSettings,
    repo: AppRepository,
    onSetUiMode: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf(SETTING_CATEGORIES.first()) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(TvSize.containerPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .width(168.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(TvSize.miniMargin)
        ) {
            SETTING_CATEGORIES.forEach { cat ->
                TvFocusItem(
                    label = cat,
                    active = cat == selected,
                    onClick = { selected = cat },
                    fontSize = 13.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SettingScreen(
                settings = settings,
                repo = repo,
                onSetUiMode = onSetUiMode,
                onSetThemeMode = onSetThemeMode,
                onSetLanguage = onSetLanguage,
                modifier = Modifier.fillMaxSize(),
                category = selected,
                compact = true
            )
        }
    }
}
