package com.hoopoe.zyfuntv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hoopoe.zyfuntv.core.debug.DebugLog
import com.hoopoe.zyfuntv.core.model.ProxyType
import com.hoopoe.zyfuntv.core.network.applyTls
import com.hoopoe.zyfuntv.core.player.PlayerIntents
import com.hoopoe.zyfuntv.data.remote.model.MediaKind
import com.hoopoe.zyfuntv.data.remote.model.MediaKindClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/** 播放状态，对齐 UI 层展示 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val error: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val resizeMode: Int = 0,
    val sidecarLabels: List<String> = emptyList()
)

/** 可选轨道（音轨 / 字幕） */
data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean
)

/**
 * ExoPlayer 统一封装。HLS/DASH/MP4 由 ExoPlayer 原生解；
 * FLV/MPEGTS 兜底内核预留（见 docs/03 §2）。
 * 播放流量经 OkHttp 数据源，跟随设置中的代理 / 超时 / UA / 自定义 headers。
 */
class PlayerController(context: Context) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    @Volatile private var timeoutMs: Long = 15000L
    @Volatile private var proxyType: String = ProxyType.DIRECT
    @Volatile private var customProxy: String = ""
    @Volatile private var ignoreSsl: Boolean = false
    @Volatile private var defaultHeaders: Map<String, String> = emptyMap()
    @Volatile private var currentUrl: String = ""

    /** 外挂侧载字幕配置；随媒体源一起挂到 MediaItem */
    private val sidecars = mutableListOf<MediaItem.SubtitleConfiguration>()

    /** 弹幕开关（数据源与覆盖层二期接入，此处仅保留状态钩子） */
    @Volatile var danmuEnabled: Boolean = false

    /** 画面比例档位，对齐 PlayerView.RESIZE_MODE_* */
    val resizeModes: List<Int> = listOf(0, 2, 3, 4)
    val resizeModeLabels: List<String> = listOf("适应", "高度", "填充", "裁剪")

    private val headerInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        defaultHeaders.forEach { (k, v) -> builder.header(k, v) }
        chain.proceed(builder.build())
    }

    @Volatile private var httpClient: OkHttpClient = buildClient()

    /** 包装最新 client：代理切换后无需重建 Player，下次创建数据源即生效 */
    private val dataSourceFactory = DataSource.Factory {
        OkHttpDataSource.Factory(httpClient).createDataSource()
    }

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(
                playbackState = playbackState,
                durationMs = player.duration.coerceAtLeast(0L)
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            val kind = MediaKindClassifier.classify(currentUrl)
            val notice = PlayerIntents.fallbackNotice(kind)
            val msg = notice ?: (error.message ?: "播放错误")
            DebugLog.e("ZyPlay", "播放失败 code=${error.errorCodeName} 原因=${msg} url=${currentUrl.take(160)}")
            _state.value = _state.value.copy(error = msg)
        }
    }

    init {
        player.addListener(listener)
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .applyTls(ignoreSsl)
            .addInterceptor(headerInterceptor)
        when (proxyType) {
            ProxyType.SYSTEM -> builder.proxySelector(ProxySelector.getDefault())
            ProxyType.CUSTOM -> parseProxy(customProxy)?.let { builder.proxy(it) }
            // DIRECT：默认直连
        }
        return builder.build()
    }

    private fun parseProxy(raw: String): Proxy? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val hostPort = trimmed.replace("http://", "").replace("https://", "")
            val idx = hostPort.lastIndexOf(':')
            if (idx > 0) {
                Proxy(Proxy.Type.HTTP, InetSocketAddress(hostPort.substring(0, idx), hostPort.substring(idx + 1).toInt()))
            } else null
        }.getOrNull()
    }

    /** 按设置更新超时、代理、忽略证书；UA 走 headers 拦截器注入 */
    fun configure(timeoutMs: Long, ua: String, proxyType: String, customProxy: String, ignoreSsl: Boolean = false) {
        this.timeoutMs = timeoutMs.coerceIn(1000L, 120000L)
        this.proxyType = proxyType
        this.customProxy = customProxy
        this.ignoreSsl = ignoreSsl
        val base = defaultHeaders.toMutableMap()
        if (ua.isNotEmpty()) base["User-Agent"] = ua
        defaultHeaders = base
        httpClient = buildClient()
    }

    /** 加载并播放地址；headers 注入 UA / Referer / 自定义请求头 */
    fun setSource(url: String, headers: Map<String, String> = emptyMap()) {
        if (url.isBlank()) {
            DebugLog.w("ZyPlay", "起播地址为空")
            _state.value = _state.value.copy(error = "播放地址为空")
            return
        }
        DebugLog.d("ZyPlay", "起播 url=${url.take(160)} 请求头=${headers.keys.joinToString(",")}")
        val kind = MediaKindClassifier.classify(url)
        if (kind == MediaKind.UNKNOWN) {
            val looksPage = url.contains("/share/", true) || url.contains(".html", true) ||
                url.contains("/player/", true) || url.contains("?url=", true)
            if (looksPage) {
                DebugLog.w("ZyPlay", "地址疑似网页/分享页而非媒体直链，不应直喂播放器：${url.take(120)}")
                _state.value = _state.value.copy(error = "该地址为网页/分享页，需经解析源提取直链")
                return
            }
            DebugLog.w("ZyPlay", "地址无标准媒体后缀，按裸流尝试播放：${url.take(120)}")
        }
        _state.value = _state.value.copy(error = null)
        currentUrl = url
        defaultHeaders = headers
        httpClient = buildClient()
        val builder = MediaItem.Builder().setUri(url)
        if (sidecars.isNotEmpty()) builder.setSubtitleConfigurations(sidecars.toList())
        player.setMediaItem(builder.build())
        player.prepare()
        player.playWhenReady = true
    }

    /** 外挂字幕侧载：按本地/在线字幕文件 URI 挂载，支持 srt / vtt / ass */
    fun addSidecarSubtitle(uri: String, mimeType: String, label: String, language: String = "zh") {
        if (uri.isBlank()) return
        val cfg = MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mimeType)
            .setLabel(label)
            .setLanguage(language)
            .build()
        sidecars.add(cfg)
        if (currentUrl.isNotEmpty()) setSource(currentUrl, defaultHeaders)
        _state.value = _state.value.copy(sidecarLabels = sidecars.map { it.label ?: "字幕" })
    }

    /** 清除全部外挂字幕并重新加载当前源 */
    fun clearSidecarSubtitles() {
        sidecars.clear()
        if (currentUrl.isNotEmpty()) setSource(currentUrl, defaultHeaders)
        _state.value = _state.value.copy(sidecarLabels = emptyList())
    }

    /** 循环切换画面比例：适应 → 高度 → 填充 → 裁剪 */
    fun cycleResizeMode() {
        val cur = _state.value.resizeMode
        val idx = resizeModes.indexOf(cur)
        val next = resizeModes[(idx + 1).coerceAtMost(resizeModes.size - 1)]
        _state.value = _state.value.copy(resizeMode = next)
    }

    /**
     * 抓取当前帧截图。Media3 未提供稳定的帧位图 API，
     * 待接入 ImageReader / VideoCapture 后实现；当前返回 null。
     */
    fun captureFrame(): android.graphics.Bitmap? = null

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    /** 倍速档位；循环切换 */
    val speedRates: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun cycleSpeed() {
        val cur = _state.value.speed
        val idx = speedRates.indexOfFirst { it == cur }
        val next = speedRates[(idx + 1).coerceAtMost(speedRates.size - 1)]
        setSpeed(next)
    }

    // ---- 音轨 / 字幕轨道 ----
    fun audioTracks(): List<TrackOption> = tracksOf(C.TRACK_TYPE_AUDIO)
    fun subtitleTracks(): List<TrackOption> = tracksOf(C.TRACK_TYPE_TEXT)

    /** 清晰度（视频轨道），按分辨率高度标注档位 */
    fun videoTracks(): List<TrackOption> {
        val result = mutableListOf<TrackOption>()
        player.currentTracks.groups.forEachIndexed { gi, group ->
            if (group.mediaTrackGroup.type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                val label = format.height.takeIf { it > 0 }?.let { "${it}P" }
                    ?: format.label?.ifEmpty { null }
                    ?: "视频 $gi-$ti"
                result.add(TrackOption(gi, ti, label, group.isTrackSelected(ti)))
            }
        }
        return result
    }

    fun selectAudio(groupIndex: Int, trackIndex: Int) =
        selectTrack(C.TRACK_TYPE_AUDIO, groupIndex, trackIndex)

    fun selectSubtitle(groupIndex: Int, trackIndex: Int) =
        selectTrack(C.TRACK_TYPE_TEXT, groupIndex, trackIndex)

    fun selectVideo(groupIndex: Int, trackIndex: Int) =
        selectTrack(C.TRACK_TYPE_VIDEO, groupIndex, trackIndex)

    /** 关闭字幕轨道 */
    fun disableSubtitle() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    private fun tracksOf(type: Int): List<TrackOption> {
        val result = mutableListOf<TrackOption>()
        player.currentTracks.groups.forEachIndexed { gi, group ->
            if (group.mediaTrackGroup.type != type) return@forEachIndexed
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                val label = format.label?.ifEmpty { null }
                    ?: format.language ?: "$type $gi-$ti"
                result.add(TrackOption(gi, ti, label, group.isTrackSelected(ti)))
            }
        }
        return result
    }

    private fun selectTrack(type: Int, groupIndex: Int, trackIndex: Int) {
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return
        if (group.mediaTrackGroup.type != type) return
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type)
            .addOverride(override)
            .build()
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}
