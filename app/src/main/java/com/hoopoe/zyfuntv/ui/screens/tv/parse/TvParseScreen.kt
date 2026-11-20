package com.hoopoe.zyfuntv.ui.screens.tv.parse

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hoopoe.zyfuntv.data.prefs.AppSettings
import com.hoopoe.zyfuntv.data.repository.AppRepository
import com.hoopoe.zyfuntv.ui.screens.mobile.ParseScreen

/** TV 解析源管理壳：横屏复用现有解析表单与源卡片列表 */
@Composable
fun TvParseScreen(
    settings: AppSettings,
    repo: AppRepository,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        ParseScreen(settings, repo)
    }
}
