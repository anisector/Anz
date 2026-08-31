package com.kerimmkirac

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random

object AniziumApi {
    private val mapper = jacksonObjectMapper()

    const val WEB = "https://anizium.co"
    const val API = "https://api.anizium.co"
    const val ONLINE = "https://api.anizium.online"
    const val LEGACY = "https://x.anizium.co"

    private const val CF_TOKEN_KEY = "16ghkdz5qnwinkyebwopbd94b49xhs"

    private val jsonHeaders = mapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Content-Type" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36",
        "Origin" to WEB,
        "Referer" to "$WEB/",
        "device" to "browser",
        "device_type" to "browser",
        "user-profile" to "null",
        "user-session" to "null",
        "language" to "tr",
        "site" to "main",
    )

    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    fun cfControl(nowMillis: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.of("Europe/Istanbul")
        val day = Instant.ofEpochMilli(nowMillis).atZone(zone).dayOfWeek
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            .lowercase(Locale.ROOT)
        val combinedKey = "${CF_TOKEN_KEY}_$day"
        val randomKey = buildString(6) {
            repeat(6) {
                append("abcdefghijklmnopqrstuvwxyz0123456789"[Random.nextInt(36)])
            }
        }
        val json = mapper.writeValueAsString(mapOf(randomKey to nowMillis))
        return xorHex(json, combinedKey)
    }

    private fun xorHex(text: String, key: String): String {
        val tb = text.toByteArray(Charsets.UTF_8)
        val kb = key.toByteArray(Charsets.UTF_8)
        return tb.indices.joinToString("") { i ->
            "%02x".format((tb[i].toInt() xor kb[i % kb.size].toInt()) and 0xff)
        }
    }

    private fun headers(cf: Boolean = true, extra: Map<String, String> = emptyMap()): Map<String, String> =
        buildMap {
            putAll(jsonHeaders)
            if (cf) put("Cf-Control", cfControl())
            putAll(extra)
        }

    suspend fun getJson(path: String): JsonNode? {
        val bases = listOf(API, ONLINE, LEGACY, WEB)
        for (base in bases) {
            val url = if (path.startsWith("http")) path else "$base/${path.trimStart('/')}"

            try {
                val response = app.get(url, headers = headers(cf = true))
                if (response.isSuccessful) {
                    runCatching { return response.parsed<JsonNode>() }
                }
            } catch (_: Throwable) {
            }

            try {
                val response = app.get(url, headers = headers(cf = false))
                if (response.isSuccessful) {
                    runCatching { return response.parsed<JsonNode>() }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    fun unwrap(node: JsonNode): JsonNode {
        var cur = node
        repeat(4) {
            val data = cur.get("data")
            val result = cur.get("result")
            val payload = cur.get("payload")
            cur = when {
                data?.isObject == true || data?.isArray == true -> data
                result?.isObject == true || result?.isArray == true -> result
                payload?.isObject == true || payload?.isArray == true -> payload
                else -> return cur
            }
        }
        return cur
    }

    fun text(node: JsonNode?, vararg names: String): String? {
        if (node == null) return null
        for (name in names) {
            val v = node.get(name) ?: continue
            if (v.isTextual && v.asText().isNotBlank()) return v.asText()
            if (v.isNumber) return v.asText()
        }
        return null
    }

    fun int(node: JsonNode?, vararg names: String): Int? =
        text(node, *names)?.let { Regex("-?\\d+").find(it)?.value?.toIntOrNull() }

    fun array(node: JsonNode?, vararg names: String): List<JsonNode> {
        if (node == null) return emptyList()
        for (name in names) {
            val v = node.get(name)
            if (v?.isArray == true) return v.toList()
        }
        return emptyList()
    }

    fun findFirstArray(node: JsonNode): List<JsonNode> {
        if (node.isArray) return node.toList()
        if (!node.isContainerNode) return emptyList()
        val it = node.elements()
        while (it.hasNext()) {
            val found = findFirstArray(it.next())
            if (found.isNotEmpty() && found.any { it.isObject }) return found
        }
        return emptyList()
    }

    fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
