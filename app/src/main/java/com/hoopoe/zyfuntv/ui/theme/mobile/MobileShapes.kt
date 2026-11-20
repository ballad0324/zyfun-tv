package com.hoopoe.zyfuntv.ui.theme.mobile

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 手机端 M3 形状令牌：小组件 / 卡片 / 大容器圆角，阶梯 10 / 14 / 20。初值与原共用 AppShapes 一致。 */
val AppMobileShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)
