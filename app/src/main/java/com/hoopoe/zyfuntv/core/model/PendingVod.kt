package com.hoopoe.zyfuntv.core.model

/** 从历史/收藏跳转影视详情的携带信息（手机端与 TV 端共用，不随任一屏迁移） */
data class PendingVod(
    val siteSource: String,
    val videoId: String,
    val name: String,
    val image: String?
)
