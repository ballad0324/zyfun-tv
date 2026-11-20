package com.hoopoe.zyfuntv.data.parser

import com.hoopoe.zyfuntv.core.network.HttpFetcher
import com.hoopoe.zyfuntv.core.script.JsRunner
import com.hoopoe.zyfuntv.data.remote.model.CmsCategory
import com.hoopoe.zyfuntv.data.remote.model.CmsDetail
import com.hoopoe.zyfuntv.data.remote.model.CmsPage
import com.hoopoe.zyfuntv.data.remote.model.PlayResult

/**
 * XBPQ 规则适配器（type=9，T3_XBPQ）。
 *
 * 源项目 `t3Xbpq` 用一套自研规则 + methods 方法表解析 HTML/JSON 页面。
 * 本期规则引擎尚未落地：[JsRunner] 不可用时，Repository 层回落到 MacCMS 同构 JSON 容错；
 * 本类作为规则钩子落点，后续接入规则解析器后在此实现 categories/list/detail/play。
 */
class XbpqAdapter(
    private val fetcher: HttpFetcher,
    private val api: String,
    private val ext: String? = null,
    private val referer: String? = null
) {
    val engineAvailable: Boolean get() = JsRunner.current().available

    fun categories(): List<CmsCategory> = emptyList()

    fun list(tid: String, wd: String, page: Int): CmsPage =
        CmsPage(page, 0, 0, emptyList())

    fun detail(vodId: String): CmsDetail? = null

    fun play(playUrl: String): PlayResult =
        PlayResult(playUrl, true, "XBPQ 规则引擎待接入")
}
