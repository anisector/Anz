package com.kerimmkirac

data class EpisodeRef(
    val animeId: String,
    val tmdbId: String,
    val season: Int,
    val episode: Int,
    val episodeId: String? = null,
    val movie: Boolean = false,
)
