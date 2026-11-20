package com.hoopoe.zyfuntv.ui.component.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoopoe.zyfuntv.R
import com.hoopoe.zyfuntv.ui.theme.mobile.MobileSize

/** 应用标识（手机端）：复用启动器位图，顶栏、功能条、搜索框左侧统一使用。 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Image(
        painter = painterResource(R.mipmap.ic_launcher),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(MobileSize.middleRadius))
    )
}
