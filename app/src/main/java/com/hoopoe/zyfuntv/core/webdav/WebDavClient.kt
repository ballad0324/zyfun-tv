package com.hoopoe.zyfuntv.core.webdav

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 轻量 WebDAV 客户端：直连 OkHttp + Basic Auth。
 * 用于把配置备份 JSON 上传到 WebDAV 目录，或从该目录拉回备份还原。
 *
 * @param baseUrl WebDAV 根地址，如 https://cloud.example.com/remote.php/dav/files/yourname
 * @param user    用户名（公开匿名盘可留空）
 * @param pass    密码
 * @param remoteDir 相对 baseUrl 的远程目录，如 /zyfuntv
 */
class WebDavClient(
    private val baseUrl: String,
    private val user: String,
    private val pass: String,
    private val remoteDir: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15000, TimeUnit.MILLISECONDS)
        .readTimeout(20000, TimeUnit.MILLISECONDS)
        .writeTimeout(20000, TimeUnit.MILLISECONDS)
        .build()

    private fun authHeader(): String? {
        if (user.isBlank()) return null
        val raw = "$user:$pass"
        return "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun encodeSeg(seg: String): String =
        URLEncoder.encode(seg, "UTF-8").replace("+", "%20")

    private fun encodedDir(): String {
        val dir = remoteDir.trim().trim('/')
        if (dir.isEmpty()) return ""
        return dir.split('/').filter { it.isNotEmpty() }.joinToString("/") { encodeSeg(it) }
    }

    private fun fullUrl(fileName: String): String {
        val base = baseUrl.trimEnd('/')
        val dirPart = encodedDir()
        val file = encodeSeg(fileName)
        return if (dirPart.isEmpty()) "$base/$file" else "$base/$dirPart/$file"
    }

    private fun Request.Builder.withAuth(): Request.Builder = apply {
        authHeader()?.let { header("Authorization", it) }
    }

    /** 逐级创建远程目录；目录已存在视为成功。 */
    fun ensureDir(): Boolean {
        val dir = remoteDir.trim().trim('/')
        if (dir.isEmpty()) return true
        var acc = baseUrl.trimEnd('/')
        for (seg in dir.split('/').filter { it.isNotEmpty() }) {
            acc = "$acc/${encodeSeg(seg)}"
            val req = Request.Builder().url(acc).method("MKCOL", null).withAuth().build()
            try {
                client.newCall(req).execute().use { r ->
                    // 405 资源已存在；301/409 表示父级或重定向就绪，不阻断
                    if (!r.isSuccessful && r.code != 405 && r.code != 301 && r.code != 409) return false
                }
            } catch (e: Exception) {
                Log.w("WebDav", "MKCOL $acc FAIL ${e.javaClass.simpleName}: ${e.message}")
                return false
            }
        }
        return true
    }

    /** 上传文本内容到远程文件；成功返回 true。 */
    fun uploadText(fileName: String, content: String): Boolean {
        if (baseUrl.isBlank()) return false
        if (!ensureDir()) return false
        val body = content.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(fullUrl(fileName)).put(body).withAuth().build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w("WebDav", "PUT FAIL ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** 下载远程文本文件；失败 / 不存在返回 null。 */
    fun downloadText(fileName: String): String? {
        if (baseUrl.isBlank()) return null
        val req = Request.Builder().url(fullUrl(fileName)).get().withAuth().build()
        return try {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        } catch (e: Exception) {
            Log.w("WebDav", "GET FAIL ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
