package com.hoopoe.zyfuntv.core.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** 压缩包内一条可识别的配置文件 */
data class ArchiveEntry(val name: String, val text: String)

/** 解压结果；[unsupported] 非空表示该格式本期不支持（如 7z） */
data class ArchiveContents(
    val entries: List<ArchiveEntry>,
    val unsupported: String? = null
)

/**
 * 本地配置压缩包解压导入。
 * 支持：zip（多条目）、gz 单文件、tar.gz / tgz（简易 tar 条目读取）。
 * 7z 需第三方解压库，本期显式返回 [unsupported]，不抛异常。
 */
object ArchiveImporter {

    private val configExt = setOf("json", "txt", "m3u", "m3u8", "xml", "conf", "cfg")

    fun parse(fileName: String, bytes: ByteArray): ArchiveContents {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".7z") -> ArchiveContents(emptyList(), "7z 压缩包本期暂不支持，请改用 zip / gz / tar.gz")
            lower.endsWith(".zip") -> fromZip(bytes)
            lower.endsWith(".tgz") || lower.endsWith(".tar.gz") -> fromTarGz(bytes)
            lower.endsWith(".gz") -> fromGzip(bytes)
            else -> ArchiveContents(emptyList(), "不支持的压缩格式：$fileName")
        }
    }

    private fun fromZip(bytes: ByteArray): ArchiveContents {
        val out = mutableListOf<ArchiveEntry>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val text = zis.readBytes().toString(Charsets.UTF_8)
                        if (isConfig(entry.name, text)) out.add(ArchiveEntry(entry.name, text))
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return ArchiveContents(out)
    }

    private fun fromGzip(bytes: ByteArray): ArchiveContents {
        val text = runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return ArchiveContents(emptyList(), "gz 解压失败")
        val name = "config.txt"
        return ArchiveContents(listOf(ArchiveEntry(name, text)))
    }

    private fun fromTarGz(bytes: ByteArray): ArchiveContents {
        val tar = runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        }.getOrNull() ?: return ArchiveContents(emptyList(), "tar.gz 解压失败")
        val out = mutableListOf<ArchiveEntry>()
        var offset = 0
        while (offset + 512 <= tar.size) {
            val header = tar.copyOfRange(offset, offset + 512)
            val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000', ' ')
            if (name.isEmpty()) break
            val sizeStr = String(header, 124, 12, Charsets.UTF_8).trimEnd('\u0000', ' ', '\u0020')
            val size = sizeStr.toIntOrNull(8) ?: 0
            val dataStart = offset + 512
            val dataEnd = (dataStart + size).coerceAtMost(tar.size)
            if (size > 0 && dataStart < tar.size) {
                val text = tar.copyOfRange(dataStart, dataEnd).toString(Charsets.UTF_8)
                if (isConfig(name, text)) out.add(ArchiveEntry(name, text))
            }
            offset = dataEnd + ((512 - size % 512) % 512)
        }
        return ArchiveContents(out)
    }

    private fun isConfig(name: String, text: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in configExt) return true
        // 无扩展名时，内容像 JSON / M3U 也纳入
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("#EXTM3U")
    }

    /** 把多个条目合并为一份配置文本，交由 ConfigImporter 统一解析 */
    fun merge(contents: ArchiveContents): String {
        if (contents.entries.isEmpty()) return ""
        val sb = StringBuilder()
        contents.entries.forEach { e ->
            sb.appendLine("# ${e.name}")
            sb.appendLine(e.text)
            sb.appendLine()
        }
        return sb.toString()
    }

    /** 预留：读取二进制到内存的统一入口（当前所有格式均走 ByteArray） */
    fun readAll(stream: java.io.InputStream): ByteArray =
        ByteArrayOutputStream().use { out ->
            stream.copyTo(out)
            out.toByteArray()
        }
}
