package com.hoopoe.zyfuntv.data.local

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** headers / 扩展字段等 Map 与 JSON 字符串的互转工具（实体列以字符串持久化） */
object Converters {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun mapToJson(map: Map<String, String>?): String? =
        map?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(mapSerializer, it) }

    fun jsonToMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, raw) }
            .getOrElse { parseKvString(raw) }
    }

    /** 兜底：解析 `k=v, k2=v2` 或 `{k=v}` 形式的旧存储 */
    private fun parseKvString(raw: String): Map<String, String> {
        val cleaned = raw.trim().removePrefix("{").removeSuffix("}")
        if (cleaned.isEmpty()) return emptyMap()
        return cleaned.split(",").mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx).trim() to it.substring(idx + 1).trim() else null
        }.toMap()
    }
}
