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

    override suspend fun getMainPage(page: Int, request: MainPageData): HomePageResponse {
        val node = AniziumApi.getJson(this, request.data.replace("%d", page.toString()))
            ?: return newHomePageResponse(request.name, emptyList())
        val items = extractItems(AniziumApi.unwrap(node))
        return newHomePageResponse(request.name, items.mapNotNull { it.asSearchResponse() }, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val node = AniziumApi.getJson(this, "/page/search?value=${AniziumApi.encode(query)}&page=1") ?: return emptyList()
        return extractItems(AniziumApi.unwrap(node)).mapNotNull { it.asSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val animeId = Regex("[?&]id=([^&]+)").find(url)?.groupValues?.get(1)
            ?: Regex("/anime/(\\d+)").find(url)?.groupValues?.get(1)
            ?: url.substringAfterLast('/').takeIf { it.matches(Regex("\\d+")) }
            ?: return null

        val node = AniziumApi.getJson(this, "/anime/get?id=$animeId") ?: return null
        val root = AniziumApi.unwrap(node)
        val title = AniziumApi.text(root, "name", "title", "animeName") ?: return null
        val poster = AniziumApi.text(root, "poster", "posterUrl", "image", "cover", "coverUrl")
        val plot = AniziumApi.text(root, "overview", "description", "synopsis", "summary")
        val year = AniziumApi.int(root, "year", "release_year")
        val type = (AniziumApi.text(root, "type", "mediaType", "contentType") ?: "series").lowercase()
        val tmdb = AniziumApi.text(root, "tmdb_id", "tmdbId") ?: ""
        val seasons = AniziumApi.array(root, "seasons")

        val episodeList = buildList {
            for (seasonNode in seasons) {
                val seasonNo = AniziumApi.int(seasonNode, "number", "season", "seasonNumber") ?: 1
                for (episodeNode in AniziumApi.array(seasonNode, "episodes")) {
                    val epNo = AniziumApi.int(episodeNode, "number", "episode", "episodeNumber") ?: 0
                    val epId = AniziumApi.text(episodeNode, "ID", "id", "episodeId")
                    val epTitle = AniziumApi.text(episodeNode, "name", "title", "episodeTitle") ?: "Bölüm $epNo"
                    val ref = EpisodeRef(animeId, tmdb, seasonNo, epNo, epId, false)
                    add(newEpisode(mapper.writeValueAsString(ref)) {
                        name = epTitle
                        episode = epNo
                        season = seasonNo
                    })
                }
            }
        }

        return if (type.contains("movie") || type == "film") {
            val movieRef = EpisodeRef(animeId, tmdb, 1, 1, null, true)
            newMovieLoadResponse(title, url, TvType.AnimeMovie, mapper.writeValueAsString(movieRef)) {
                posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodeList) {
                posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val ref = runCatching { mapper.readValue(data, EpisodeRef::class.java) }.getOrNull() ?: return false

        for (plan in listOf("standart", "premium")) {
            val sourceNode = AniziumApi.getJson(
                this,
                "/anime/source?id=${AniziumApi.encode(ref.animeId)}&site=main&plan=$plan&season=${ref.season}&episode=${ref.episode}&server=1"
            )
            if (sourceNode != null && emitApiSources(sourceNode, subtitleCallback, callback)) return true
        }

        if (ref.tmdbId.isNotBlank() && probePublicCdn(ref, callback)) return true
        return false
    }

    private fun extractItems(root: JsonNode): List<JsonNode> {
        val direct = AniziumApi.array(root, "items", "results", "animes", "anime", "episodes")
        if (direct.isNotEmpty()) return direct
        val page = root.get("page")
        val pageData = AniziumApi.array(page, "data", "items", "results", "animes", "anime", "episodes")
        if (pageData.isNotEmpty()) return pageData
        return AniziumApi.findFirstArray(root)
    }

    private fun JsonNode.asSearchResponse(): SearchResponse? {
        val title = AniziumApi.text(this, "name", "title", "animeName") ?: return null
        val id = AniziumApi.text(this, "ID", "id", "animeId", "slug") ?: return null
        val href = if (id.startsWith("http")) id else "$mainUrl/anime/$id"
        val poster = AniziumApi.text(this, "poster", "posterUrl", "image", "cover", "coverUrl")
        val type = (AniziumApi.text(this, "type", "mediaType", "contentType") ?: "series").lowercase()
        return if (type.contains("movie") || type == "film") {
            newMovieSearchResponse(title, href, TvType.AnimeMovie) { posterUrl = poster }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) { posterUrl = poster }
        }
    }

    private suspend fun emitApiSources(
        node: JsonNode,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val root = AniziumApi.unwrap(node)
        var emitted = false
        for (group in AniziumApi.array(root, "groups")) {
            val sound = AniziumApi.text(group, "group", "sound", "name") ?: ""
            for (item in AniziumApi.array(group, "items", "sources")) {
                val link = AniziumApi.text(item, "link", "url", "src") ?: continue
                if (!link.startsWith("http")) continue
                val quality = AniziumApi.int(item, "quality", "resolution") ?: inferQuality(link)
                callback(newExtractorLink(name, buildLabel(quality, sound), link) {
                    type = if (link.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    this.quality = quality
                    referer = "$mainUrl/"
                })
                emitted = true
            }
        }
        for (sub in AniziumApi.array(root, "subtitles")) {
            val link = AniziumApi.text(sub, "link", "url", "src") ?: continue
            if (link.startsWith("http")) {
                val lang = AniziumApi.text(sub, "group", "language", "lang") ?: "Türkçe"
                subtitleCallback(SubtitleFile(lang, if (link.contains("type=")) link else "$link&type=vtt"))
            }
        }
        return emitted
    }

    private suspend fun probePublicCdn(ref: EpisodeRef, callback: (ExtractorLink) -> Unit): Boolean {
        val servers = listOf(
            "https://x.aniziumserver.sbs", "https://f.aniziumserver.sbs", "https://a.aniziumserver.sbs",
            "https://k.aniziumserver.sbs", "https://r.aniziumserver.sbs", "https://u.aniziumserver.sbs",
            "https://x.aniziumserver.site", "https://f.aniziumserver.site", "https://a.aniziumserver.site",
            "https://k.aniziumserver.site", "https://r.aniziumserver.site", "https://u.aniziumserver.site",
        )
        val qualities = listOf(2160, 1440, 1080, 720, 480)
        val sounds = listOf("original", "trdub", "endub", "trsub")
        for (quality in qualities) {
            var found = false
            for (sound in sounds) {
                val path = if (ref.movie) "/${ref.tmdbId}/${quality}p-$sound/master.m3u8" else "/${ref.tmdbId}/${ref.season}/${ref.episode}/${quality}p-$sound/master.m3u8"
                val url = servers.firstOrNull { server -> runCatching { app.get("$server$path", headers = mapOf("User-Agent" to "Mozilla/5.0")).isSuccessful }.getOrDefault(false) } ?: continue
                callback(newExtractorLink(name, buildLabel(quality, sound), url) {
                    type = ExtractorLinkType.M3U8
                    this.quality = quality
                    referer = "$mainUrl/"
                })
                found = true
            }
            if (found) return true
        }
        return false
    }

    private fun inferQuality(url: String): Int = when {
        Regex("2160|4k", RegexOption.IGNORE_CASE).containsMatchIn(url) -> 2160
        Regex("1440|2k", RegexOption.IGNORE_CASE).containsMatchIn(url) -> 1440
        Regex("1080|fullhd", RegexOption.IGNORE_CASE).containsMatchIn(url) -> 1080
        Regex("720", RegexOption.IGNORE_CASE).containsMatchIn(url) -> 720
        Regex("480", RegexOption.IGNORE_CASE).containsMatchIn(url) -> 480
        else -> 0
    }

    private fun buildLabel(quality: Int, sound: String): String {
        val q = when (quality) { 2160 -> "4K"; 1440 -> "2K"; else -> "${quality}p" }
        val s = when (sound.lowercase()) {
            "trdub" -> "Türkçe Dublaj"
            "endub" -> "İngilizce Dublaj"
            "cndub" -> "Çince Dublaj"
            "trsub" -> "Türkçe Altyazı"
            "original" -> "Japonca"
            else -> sound.ifBlank { "Kaynak" }
        }
        return "$q ($s)"
    }
}
