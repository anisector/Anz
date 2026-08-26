package com.kerimmkirac

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.MainAPI
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object AniziumApi {
    private val mapper = jacksonObjectMapper()

    const val WEB = "https://anizium.co"
    const val API = "https://api.anizium.co"
    const val LEGACY = "https://x.anizium.co"

    private val jsonHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36",
        "Origin" to WEB,
        "Referer" to "$WEB/",
        "device" to "browser",
        "language" to "tr",
        "site" to "main",
    )

    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    fun cfControl(nowMillis: Long = System.currentTimeMillis()): String {
        val keyBase = "hlxjl1c2w281ax473rt1ofgrvhyjvi"
        val zone = ZoneId.of("Europe/Istanbul")
        val day = Instant.ofEpochMilli(nowMillis).atZone(zone).dayOfWeek
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT)
        val key = "${keyBase}_${day}"
        val rnd = randomToken(6, nowMillis)
        val json = "{\"$rnd\":${nowMillis}}"
        return xorHex(json, key)
    }

    private fun randomToken(length: Int, seed: Long): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        var x = seed xor -7046029254386353131L
        return buildString(length) {
            repeat(length) {
                x = x xor (x shl 13)
                x = x xor (x ushr 7)
                x = x xor (x shl 17)
                append(alphabet[(x ushr 1).toInt().and(Int.MAX_VALUE) % alphabet.length])
            }
        }
    }

    private fun xorHex(text: String, key: String): String {
        val tb = text.toByteArray(Charsets.UTF_8)
        val kb = key.toByteArray(Charsets.UTF_8)
        return tb.indices.joinToString("") { i -> "%02x".format((tb[i].toInt() xor kb[i % kb.size].toInt()) and 0xff) }
    }

    private fun headers(cf: Boolean = true, extra: Map<String, String> = emptyMap()): Map<String, String> =
        buildMap {
            putAll(jsonHeaders)
            if (cf) put("Cf-Control", cfControl())
            putAll(extra)
        }

    suspend fun getJson(api: MainAPI, path: String): JsonNode? = with(api) {
        val bases = listOf(API, LEGACY, WEB)
        for (base in bases) {
            val url = if (path.startsWith("http")) path else "$base/${path.trimStart('/')}"
            try {
                val response = app.get(url, headers = headers())
                if (!response.isSuccessful) continue
                val body = response.text
                if (body.isBlank()) continue
                return mapper.readTree(body)
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
                data?.isObject == true -> data
                result?.isObject == true -> result
                payload?.isObject == true -> payload
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
