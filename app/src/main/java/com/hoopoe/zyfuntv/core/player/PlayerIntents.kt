package com.hoopoe.zyfuntv.core.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.hoopoe.zyfuntv.data.remote.model.MediaKind

/** 外部播放器跳转与媒体格式提示 */
object PlayerIntents {

    /** 常见可播放媒体 MIME 粗判 */
    private fun mimeOf(kind: MediaKind): String = when (kind) {
        MediaKind.HLS -> "application/x-mpegURL"
        MediaKind.DASH -> "application/dash+xml"
        MediaKind.MP4 -> "video/mp4"
        MediaKind.TS -> "video/mp2t"
        MediaKind.FLV -> "video/x-flv"
        MediaKind.MKV -> "video/x-matroska"
        MediaKind.UNKNOWN -> "video/*"
    }

    /**
     * 构建跳转到外部播放器的 Intent。
     * 头部以 EXTRA_HEADERS 透传（多数播放器仅识别 User-Agent / Referer）。
     * @return 可启动的 Intent；调用方先 resolveActivity 判断是否有安装的播放器。
     */
    fun buildExternalPlayer(
        url: String,
        headers: Map<String, String>,
        kind: MediaKind,
        pkg: String? = null
    ): Intent? {
        if (url.isBlank()) return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mimeOf(kind))
            if (!pkg.isNullOrBlank()) setPackage(pkg)
            val hBundle = Bundle().apply { headers.forEach { (k, v) -> putString(k, v) } }
            putExtra("android.media.intent.extra.HEADERS", hBundle)
            headers["User-Agent"]?.let { putExtra("User-Agent", it) }
            headers["Referer"]?.let { putExtra("Referer", it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 是否存在可处理该 Intent 的外部应用 */
    fun canResolve(context: Context, intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, 0) != null

    /** 兜底内核缺失时的友好提示文案 */
    fun fallbackNotice(kind: MediaKind): String? = when (kind) {
        MediaKind.FLV -> "该源为 FLV 格式，当前内置内核暂不支持，可尝试外部播放器"
        MediaKind.TS -> "该源为 MPEGTS 流，内置内核可能无法直接播放，可尝试外部播放器"
        MediaKind.MKV -> "该源为 MKV 封装，内置内核兼容性有限，可尝试外部播放器"
        else -> null
    }
}
