package com.hoopoe.zyfuntv.data.parser

import com.hoopoe.zyfuntv.core.network.HttpFetcher
import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsPage
import com.hoopoe.zyfuntv.data.remote.model.PlayResult

/**
 * XYQ 适配器（type=10，T3_XYQ）。
 *
 * 源项目 `t3Xyq` 基于 Jsoup/CSS 选择器从 HTML 页面抽取影片列表与详情。
 * 本期未引入 HTML 选择器解析；Repository 层回落到同构 JSON 容错，本类为选择器规则落点。
 */
class XyqAdapter(
    private val fetcher: HttpFetcher,
    private val api: String,
    private val ext: String? = null,
    private val referer: String? = null
) {
    fun categories(): List<CmsCategory> = emptyList()

    fun list(tid: String, wd: String, page: Int): CmsPage =
        CmsPage(page, 0, 0, emptyList())

    fun detail(vodId: String): CmsDetail? = null

    fun play(playUrl: String): PlayResult =
        PlayResult(playUrl, true, "XYQ 选择器解析待接入")
}
