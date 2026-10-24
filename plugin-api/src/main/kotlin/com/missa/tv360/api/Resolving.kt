package com.missa.tv360.api

data class ResolvedStream(
    val url: String,
    val format: String = "mp4",            // "mp4" | "hls" | "dash"
    val headers: Map<String, String> = emptyMap(),
    val quality: Int = 0,                  // 1080, 720... 0 = inconnu
    val label: String = "",
)

data class ResolvedSubtitle(val lang: String, val url: String)

data class ResolvedEpisode(
    val title: String,
    val season: Int?,
    val episode: Int?,
    val data: String,
)

data class Resolution(
    val streams: List<ResolvedStream> = emptyList(),
    val subtitles: List<ResolvedSubtitle> = emptyList(),
    val episodes: List<ResolvedEpisode> = emptyList(),   // series : liste a choisir
    val error: String? = null,
)

/** Provider capable de transformer une page en flux lisibles. */
interface ResolvingProvider : Provider {
    suspend fun resolve(pageUrl: String, episodeData: String? = null): Resolution
}
