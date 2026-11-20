package com.hoopoe.zyfuntv.core.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一条屏内调试日志 */
data class DebugEntry(
    val time: String,
    val level: String,
    val tag: String,
    val message: String
)

/**
 * 全局环形调试日志：在无 logcat 环境（真机 / 模拟器）下，把网络请求、分类 / 列表 / 详情、
 * 解析与播放链路记录到内存缓冲，供实验室「调试日志」页查看、复制与清空。
 * 仅保留最近 [CAP] 条；不持久化，不记录完整响应体（只记状态码 / 长度 / 条数 / 摘要）。
 */
object DebugLog {
    private const val CAP = 500
    private val lock = Any()
    private val entries = mutableListOf<DebugEntry>()
    private val _flow = MutableStateFlow<List<DebugEntry>>(emptyList())

    val flow: StateFlow<List<DebugEntry>> = _flow

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(level: String, tag: String, message: String) {
        val safe = if (message.length > 500) message.take(500) + "…" else message
        entries.add(DebugEntry(timeFmt.format(Date()), level, tag, safe))
        if (entries.size > CAP) entries.removeAt(0)
        _flow.value = entries.toList()
    }

    fun d(tag: String, message: String) = log("D", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)

    @Synchronized
    fun clear() {
        entries.clear()
        _flow.value = emptyList()
    }

    @Synchronized
    fun export(): String = entries.joinToString("\n") {
        "${it.time} [${it.level}] ${it.tag}: ${it.message}"
    }
}
