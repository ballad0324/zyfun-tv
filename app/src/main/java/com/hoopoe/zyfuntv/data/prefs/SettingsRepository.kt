package com.hoopoe.zyfuntv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hoopoe.zyfuntv.core.model.ProxyType
import com.hoopoe.zyfuntv.core.model.ThemeMode
import com.hoopoe.zyfuntv.core.model.UiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val defaultSite: String = "",
    val defaultIptv: String = "",
    val defaultAnalyze: String = "",
    val ua: String = "",
    val epg: String = "https://epg.112114.eu.org/?ch={name}&date={date}",
    val logo: String = "https://epg.112114.eu.org/logo/{name}.png",
    val proxyType: String = ProxyType.DIRECT,
    val customProxy: String = "",
    val timeoutMs: Long = 15000L,
    val debug: Boolean = false,
    val uiMode: String = UiMode.PHONE,
    val themeMode: String = ThemeMode.SYSTEM,
    val liveIpMark: Boolean = true,
    val liveDelay: Boolean = false,
    val liveThumbnail: Boolean = false,
    val language: String = "zh",
    val davUrl: String = "",
    val davUser: String = "",
    val davPass: String = "",
    val davPath: String = "/zyfuntv",
    val dnsMode: String = "system",
    val dnsServer: String = "",
    val danmuEnabled: Boolean = false,
    val autoPlayNext: Boolean = true,
    val useExternalPlayer: Boolean = false,
    /** 外部播放器包名；空串表示由系统选择器弹出，非空则定向拉起该应用 */
    val externalPlayerPkg: String = "",
    val keepBackground: Boolean = false,
    /** 忽略 HTTPS 证书校验（仅用于兼容证书链不全/自签的第三方源，默认关闭） */
    val ignoreSsl: Boolean = false
)

class SettingsRepository(private val context: Context) {

    /** 轻量同步缓存：供 attachBaseContext 在异步 DataStore 读取前应用 locale */
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private object Keys {
        val DEFAULT_SITE = stringPreferencesKey("default_site")
        val DEFAULT_IPTV = stringPreferencesKey("default_iptv")
        val DEFAULT_ANALYZE = stringPreferencesKey("default_analyze")
        val UA = stringPreferencesKey("ua")
        val EPG = stringPreferencesKey("epg")
        val LOGO = stringPreferencesKey("logo")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val CUSTOM_PROXY = stringPreferencesKey("custom_proxy")
        val TIMEOUT_MS = longPreferencesKey("timeout_ms")
        val DEBUG = booleanPreferencesKey("debug")
        val UI_MODE = stringPreferencesKey("ui_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIVE_IP_MARK = booleanPreferencesKey("live_ip_mark")
        val LIVE_DELAY = booleanPreferencesKey("live_delay")
        val LIVE_THUMBNAIL = booleanPreferencesKey("live_thumbnail")
        val LANGUAGE = stringPreferencesKey("language")
        val DAV_URL = stringPreferencesKey("dav_url")
        val DAV_USER = stringPreferencesKey("dav_user")
        val DAV_PASS = stringPreferencesKey("dav_pass")
        val DAV_PATH = stringPreferencesKey("dav_path")
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val DNS_SERVER = stringPreferencesKey("dns_server")
        val DANMU_ENABLED = booleanPreferencesKey("danmu_enabled")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val USE_EXTERNAL_PLAYER = booleanPreferencesKey("use_external_player")
        val EXTERNAL_PKG = stringPreferencesKey("external_player_pkg")
        val KEEP_BACKGROUND = booleanPreferencesKey("keep_background")
        val IGNORE_SSL = booleanPreferencesKey("ignore_ssl")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            defaultSite = p[Keys.DEFAULT_SITE] ?: "",
            defaultIptv = p[Keys.DEFAULT_IPTV] ?: "",
            defaultAnalyze = p[Keys.DEFAULT_ANALYZE] ?: "",
            ua = p[Keys.UA] ?: "",
            epg = p[Keys.EPG] ?: AppSettings().epg,
            logo = p[Keys.LOGO] ?: AppSettings().logo,
            proxyType = p[Keys.PROXY_TYPE] ?: ProxyType.DIRECT,
            customProxy = p[Keys.CUSTOM_PROXY] ?: "",
            timeoutMs = p[Keys.TIMEOUT_MS] ?: 15000L,
            debug = p[Keys.DEBUG] ?: false,
            uiMode = p[Keys.UI_MODE] ?: UiMode.PHONE,
            themeMode = p[Keys.THEME_MODE] ?: ThemeMode.SYSTEM,
            liveIpMark = p[Keys.LIVE_IP_MARK] ?: true,
            liveDelay = p[Keys.LIVE_DELAY] ?: false,
            liveThumbnail = p[Keys.LIVE_THUMBNAIL] ?: false,
            language = p[Keys.LANGUAGE] ?: "zh",
            davUrl = p[Keys.DAV_URL] ?: "",
            davUser = p[Keys.DAV_USER] ?: "",
            davPass = p[Keys.DAV_PASS] ?: "",
            davPath = p[Keys.DAV_PATH] ?: "/zyfuntv",
            dnsMode = p[Keys.DNS_MODE] ?: "system",
            dnsServer = p[Keys.DNS_SERVER] ?: "",
            danmuEnabled = p[Keys.DANMU_ENABLED] ?: false,
            autoPlayNext = p[Keys.AUTO_PLAY_NEXT] ?: true,
            useExternalPlayer = p[Keys.USE_EXTERNAL_PLAYER] ?: false,
            externalPlayerPkg = p[Keys.EXTERNAL_PKG] ?: "",
            keepBackground = p[Keys.KEEP_BACKGROUND] ?: false,
            ignoreSsl = p[Keys.IGNORE_SSL] ?: false
        )
    }

    suspend fun setDefaultSite(id: String) = context.dataStore.edit { it[Keys.DEFAULT_SITE] = id }
    suspend fun setDefaultIptv(id: String) = context.dataStore.edit { it[Keys.DEFAULT_IPTV] = id }
    suspend fun setDefaultAnalyze(id: String) = context.dataStore.edit { it[Keys.DEFAULT_ANALYZE] = id }
    suspend fun setUa(ua: String) = context.dataStore.edit { it[Keys.UA] = ua }
    suspend fun setEpg(url: String) = context.dataStore.edit { it[Keys.EPG] = url }
    suspend fun setLogo(url: String) = context.dataStore.edit { it[Keys.LOGO] = url }
    suspend fun setProxyType(type: String) = context.dataStore.edit { it[Keys.PROXY_TYPE] = type }
    suspend fun setCustomProxy(host: String) = context.dataStore.edit { it[Keys.CUSTOM_PROXY] = host }
    suspend fun setTimeoutMs(ms: Long) = context.dataStore.edit { it[Keys.TIMEOUT_MS] = ms }
    suspend fun setDebug(debug: Boolean) = context.dataStore.edit { it[Keys.DEBUG] = debug }
    suspend fun setUiMode(mode: String) = context.dataStore.edit { it[Keys.UI_MODE] = mode }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setLiveIpMark(v: Boolean) = context.dataStore.edit { it[Keys.LIVE_IP_MARK] = v }
    suspend fun setLiveDelay(v: Boolean) = context.dataStore.edit { it[Keys.LIVE_DELAY] = v }
    suspend fun setLiveThumbnail(v: Boolean) = context.dataStore.edit { it[Keys.LIVE_THUMBNAIL] = v }
    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
        sp.edit().putString("language", lang).apply()
    }

    /** 全量写回一组偏好（备份还原用）；同步语言 SP 缓存供 attachBaseContext 即时读取 */
    suspend fun applySettings(s: AppSettings) = context.dataStore.edit { p ->
        p[Keys.DEFAULT_SITE] = s.defaultSite
        p[Keys.DEFAULT_IPTV] = s.defaultIptv
        p[Keys.DEFAULT_ANALYZE] = s.defaultAnalyze
        p[Keys.UA] = s.ua
        p[Keys.EPG] = s.epg
        p[Keys.LOGO] = s.logo
        p[Keys.PROXY_TYPE] = s.proxyType
        p[Keys.CUSTOM_PROXY] = s.customProxy
        p[Keys.TIMEOUT_MS] = s.timeoutMs
        p[Keys.DEBUG] = s.debug
        p[Keys.UI_MODE] = s.uiMode
        p[Keys.THEME_MODE] = s.themeMode
        p[Keys.LIVE_IP_MARK] = s.liveIpMark
        p[Keys.LIVE_DELAY] = s.liveDelay
        p[Keys.LIVE_THUMBNAIL] = s.liveThumbnail
        p[Keys.LANGUAGE] = s.language
        p[Keys.DAV_URL] = s.davUrl
        p[Keys.DAV_USER] = s.davUser
        p[Keys.DAV_PASS] = s.davPass
        p[Keys.DAV_PATH] = s.davPath
        p[Keys.DNS_MODE] = s.dnsMode
        p[Keys.DNS_SERVER] = s.dnsServer
        p[Keys.DANMU_ENABLED] = s.danmuEnabled
        p[Keys.AUTO_PLAY_NEXT] = s.autoPlayNext
        p[Keys.USE_EXTERNAL_PLAYER] = s.useExternalPlayer
        p[Keys.EXTERNAL_PKG] = s.externalPlayerPkg
        p[Keys.KEEP_BACKGROUND] = s.keepBackground
        p[Keys.IGNORE_SSL] = s.ignoreSsl
        sp.edit().putString("language", s.language).apply()
    }

    suspend fun setDnsMode(mode: String) = context.dataStore.edit { it[Keys.DNS_MODE] = mode }
    suspend fun setDnsServer(server: String) = context.dataStore.edit { it[Keys.DNS_SERVER] = server.trim() }
    suspend fun setDanmuEnabled(v: Boolean) = context.dataStore.edit { it[Keys.DANMU_ENABLED] = v }
    suspend fun setAutoPlayNext(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = v }
    suspend fun setUseExternalPlayer(v: Boolean) = context.dataStore.edit { it[Keys.USE_EXTERNAL_PLAYER] = v }
    suspend fun setExternalPlayerPkg(pkg: String) = context.dataStore.edit { it[Keys.EXTERNAL_PKG] = pkg.trim() }
    suspend fun setKeepBackground(v: Boolean) = context.dataStore.edit { it[Keys.KEEP_BACKGROUND] = v }
    suspend fun setIgnoreSsl(v: Boolean) = context.dataStore.edit { it[Keys.IGNORE_SSL] = v }

    /** 批量保存 WebDAV 连接配置 */
    suspend fun setDavConfig(url: String, user: String, pass: String, path: String) {
        context.dataStore.edit {
            it[Keys.DAV_URL] = url.trim()
            it[Keys.DAV_USER] = user.trim()
            it[Keys.DAV_PASS] = pass
            it[Keys.DAV_PATH] = path.ifBlank { "/zyfuntv" }
        }
    }

    /** 清空全部偏好，回落到 AppSettings 默认值 */
    suspend fun reset() = context.dataStore.edit { it.clear() }
}
