package com.missa.tv360.cloudstream.compat

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.missa.tv360.api.Item
import com.missa.tv360.api.Kind
import com.missa.tv360.api.PluginLog
import com.missa.tv360.api.ResolvingProvider
import com.missa.tv360.api.Resolution
import com.missa.tv360.api.ResolvedEpisode
import com.missa.tv360.api.ResolvedStream
import com.missa.tv360.api.ResolvedSubtitle
import com.missa.tv360.api.Row
import com.missa.tv360.api.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Pont d'une seule MainAPI vers le contrat ResolvingProvider natif. */
class CloudstreamProvider(private val api: MainAPI) : ResolvingProvider {
    override val id: String = idFor(api)
    override val name: String get() = api.name

    override suspend fun rows(): List<Row> {
        if (!api.hasMainPage) return emptyList()
        val rows = mutableListOf<Row>()
        api.mainPage.take(MAX_MAIN_PAGES).forEachIndexed { pageIndex, page ->
            val response = try {
                api.getMainPage(
                    1,
                    MainPageRequest(page.name, page.data, page.horizontalImages),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                PluginLog.e("Cloudstream ${api.name} : page ${page.name} en échec", error)
                null
            } ?: return@forEachIndexed
            rows += response.toRows(pageIndex, page.name)
        }
        return rows
    }

    override suspend fun resolve(pageUrl: String, episodeData: String?): Resolution = withContext(Dispatchers.IO) {
        try {
            withTimeout(45_000) {
                val data = if (episodeData != null) {
                    episodeData
                } else {
                    val loadRes = api.load(pageUrl) ?: return@withTimeout Resolution(error = "Page introuvable ou load() null")
                    when (loadRes) {
                        is MovieLoadResponse -> {
                            if (loadRes.dataUrl.isBlank()) {
                                loadRes.url.ifBlank { pageUrl }
                            } else {
                                loadRes.dataUrl
                            }
                        }
                        is TvSeriesLoadResponse -> {
                            val eps = loadRes.episodes.map { ep ->
                                ResolvedEpisode(
                                    title = ep.name ?: "Episode",
                                    season = ep.season,
                                    episode = ep.episode,
                                    data = ep.data
                                )
                            }
                            return@withTimeout Resolution(episodes = eps)
                        }
                        is AnimeLoadResponse -> {
                            val eps = loadRes.episodes.map { ep ->
                                ResolvedEpisode(
                                    title = ep.name ?: "Episode",
                                    season = ep.season,
                                    episode = ep.episode,
                                    data = ep.data
                                )
                            }
                            return@withTimeout Resolution(episodes = eps)
                        }
                        else -> {
                            loadRes.url.ifBlank { pageUrl }
                        }
                    }
                }

                val links = mutableListOf<ExtractorLink>()
                val subs = mutableListOf<SubtitleFile>()

                api.loadLinks(data, false, { subs += it }, { links += it })

                val streams = links.filter { it.url.isNotBlank() }.map { link ->
                    val url = link.url
                    val format = when {
                        link.isM3u8 || url.contains(".m3u8", ignoreCase = true) -> "hls"
                        url.contains(".mpd", ignoreCase = true) -> "dash"
                        else -> "mp4"
                    }
                    val headers = runCatching { link.getAllHeaders() }.getOrDefault(emptyMap())
                    val quality = link.quality
                    val label = buildString {
                        if (!link.source.isNullOrBlank()) append("${link.source} ")
                        if (quality > 0) append("${quality}p")
                    }.trim().ifBlank { format.uppercase() }

                    ResolvedStream(
                        url = url,
                        format = format,
                        headers = headers,
                        quality = quality,
                        label = label,
                    )
                }.sortedByDescending { it.quality }.distinctBy { it.url }

                val resolvedSubs = subs.map { ResolvedSubtitle(it.lang, it.url) }

                Resolution(streams = streams, subtitles = resolvedSubs)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            PluginLog.e("Cloudstream ${api.name} : resolve($pageUrl) en échec", t)
            Resolution(error = "${t::class.java.simpleName}: ${t.message ?: "Erreur inconnue"}")
        }
    }

    private fun HomePageResponse.toRows(pageIndex: Int, fallbackTitle: String): List<Row> =
        items.mapIndexedNotNull { listIndex, homeList ->
            val mapped = homeList.list.take(MAX_ITEMS_PER_ROW).mapNotNull { it.toMissaItem(api) }
            if (mapped.isEmpty()) {
                null
            } else {
                Row(
                    id = "$pageIndex-$listIndex",
                    title = homeList.name.ifBlank { fallbackTitle },
                    items = mapped,
                )
            }
        }

    companion object {
        private const val MAX_MAIN_PAGES = 2
        private const val MAX_ITEMS_PER_ROW = 24

        fun idFor(api: MainAPI): String {
            val base = api.name.lowercase().map { char ->
                if (char.isLetterOrDigit() || char == '-' || char == '_') char else '-'
            }.joinToString("").trim('-').ifBlank { "cloudstream" }
            val className = api::class.java.simpleName.lowercase()
                .replace(Regex("[^a-z0-9_-]"), "-")
            return "$base-$className"
        }
    }
}

internal fun SearchResponse.toMissaItem(api: MainAPI): Item? {
    val page = url.trim().takeIf { it.isNotEmpty() } ?: return null
    val title = name.trim().takeIf { it.isNotEmpty() } ?: return null
    return Item(
        id = page,
        title = title,
        kind = type.toMissaKind(),
        // Aucune URL de page n'est présentée comme un flux lisible.
        stream = Stream(url = "", format = "none"),
        description = "",
        thumbnailUrl = posterUrl,
        source = api.name,
        license = null,
    ).apply { pageUrl = page }
}

internal fun TvType?.toMissaKind(): Kind = when (this) {
    TvType.TvSeries,
    TvType.Anime,
    TvType.Cartoon,
    TvType.OVA,
    TvType.AsianDrama,
    -> Kind.SERIES
    TvType.Live -> Kind.LIVE_TV
    TvType.Movie,
    TvType.AnimeMovie,
    TvType.Others,
    TvType.Torrent,
    TvType.Documentary,
    TvType.Music,
    null,
    -> Kind.MOVIE
}
