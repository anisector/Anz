package com.kerimmkirac


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink


class Anizium : MainAPI() {
    override var mainUrl = AniziumApi.WEB
    override var name = "Anizium"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val instantLinkLoading = true


    private val mapper = jacksonObjectMapper()


    override val mainPage = listOf(
        MainPageData("Yeni Bölümler", "/page/last-added-episodes?page=%d"),
        MainPageData("Top 100", "/page/top?platform=favorite&page=%d"),
        MainPageData("Aksiyon", "/page/catalog?id=23813&type=genre&page=%d"),
        MainPageData("Macera", "/page/catalog?id=43261&type=genre&page=%d"),
        MainPageData("Komedi", "/page/catalog?id=47450&type=genre&page=%d"),
        MainPageData("Drama", "/page/catalog?id=59624&type=genre&page=%d"),
        MainPageData("Fantastik", "/page/catalog?id=62263&type=genre&page=%d"),
        MainPageData("Romantik", "/page/catalog?id=87910&type=genre&page=%d"),
        MainPageData("Bilim Kurgu", "/page/catalog?id=94032&type=genre&page=%d"),
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val node = AniziumApi.getJson(request.data.replace("%d", page.toString()))
            ?: return newHomePageResponse(request.name, emptyList())
        val items = extractItems(AniziumApi.unwrap(node))
