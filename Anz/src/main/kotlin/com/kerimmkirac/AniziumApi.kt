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


    // Matches the token-generation scheme found in the Anizium client ecosystem:
    // token = XOR-hex(JSON({ random6: unixMillis }), key + "_" + englishWeekday)
    // Key confirmed in the public Anizium-compatible client implementation.
    private const val CF_TOKEN_KEY = "16ghkdz5qnwinkyebwopbd94b49xhs"


    private val jsonHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36",
        "Origin" to WEB,
        "Referer" to "$WEB/",
        "device" to "browser",
        "device_type" to "browser",
        "language" to "tr",
        "site" to "main",
    )
