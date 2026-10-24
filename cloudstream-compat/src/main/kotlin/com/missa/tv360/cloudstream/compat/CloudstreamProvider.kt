package com.missa.tv360.cloudstream.compat

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.missa.tv360.api.Item
import com.missa.tv360.api.Kind
import com.missa.tv360.api.PluginLog
import kotlinx.coroutines.CancellationException
import com.missa.tv360.api.Provider
import com.missa.tv360.api.Row
import com.missa.tv360.api.Stream

/** Pont d'une seule MainAPI vers le contrat Provider natif. */
class CloudstreamProvider(private val api: MainAPI) : Provider {
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
