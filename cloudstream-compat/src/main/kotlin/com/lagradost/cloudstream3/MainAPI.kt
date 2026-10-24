package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.utils.ExtractorLink

/** Jackson partagé, compatible avec les helpers présents dans le DEX du prototype. */
val mapper: JsonMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

enum class TvType {
    Movie,
    TvSeries,
    Anime,
    AnimeMovie,
    Cartoon,
    OVA,
    AsianDrama,
    Live,
    Others,
}

enum class DubStatus { Dubbed, Subbed, None }

data class SubtitleFile(val lang: String, val url: String)

data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

interface SearchResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType?
    var posterUrl: String?
}

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var plot: String?
}

data class MovieSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var id: Int? = null,
    var posterHeaders: Map<String, String>? = null,
) : SearchResponse

data class TvSeriesSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var id: Int? = null,
    var posterHeaders: Map<String, String>? = null,
) : SearchResponse

data class AnimeSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Anime,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var dubStatus: MutableSet<DubStatus>? = null,
    var episodes: MutableMap<DubStatus, Int> = mutableMapOf(),
    var id: Int? = null,
    var posterHeaders: Map<String, String>? = null,
) : SearchResponse

data class LiveSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
) : SearchResponse

data class Episode(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var description: String? = null,
)

data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Movie,
    var dataUrl: String = "",
    override var posterUrl: String? = null,
    override var plot: String? = null,
    var year: Int? = null,
    var tags: List<String>? = null,
    var rating: Int? = null,
    var recommendations: List<SearchResponse>? = null,
    var comingSoon: Boolean = false,
) : LoadResponse

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var episodes: List<Episode>,
    override var posterUrl: String? = null,
    override var plot: String? = null,
    var year: Int? = null,
    var tags: List<String>? = null,
) : LoadResponse

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
)

data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false,
)

abstract class MainAPI {
    open var name: String = "NONE"
    open var mainUrl: String = "NONE"
    open var lang: String = "en"
    open val hasMainPage: Boolean = false
    open val hasQuickSearch: Boolean = false
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    open val mainPage: List<MainPageData> = emptyList()
    var sourcePlugin: String? = null

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        throw NotImplementedError()
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        throw NotImplementedError()
    }
}

fun mainPageOf(vararg items: Pair<String, String>): List<MainPageData> =
    items.map { (url, name) -> MainPageData(name = name, data = url) }

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http") || url.startsWith("{\"") || url.startsWith("[")) return url
    if (url.isEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith('/')) mainUrl + url else "$mainUrl/$url"
}

fun newHomePageResponse(
    name: String,
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): HomePageResponse = HomePageResponse(
    items = listOf(HomePageList(name, list)),
    hasNext = hasNext ?: list.isNotEmpty(),
)

fun newHomePageResponse(
    list: List<HomePageList>,
    hasNext: Boolean? = null,
): HomePageResponse = HomePageResponse(
    items = list,
    hasNext = hasNext ?: list.any { it.list.isNotEmpty() },
)

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    initializer: MovieSearchResponse.() -> Unit = {},
): MovieSearchResponse {
    val response = MovieSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    response.initializer()
    return response
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    initializer: TvSeriesSearchResponse.() -> Unit = {},
): TvSeriesSearchResponse {
    val response = TvSeriesSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    response.initializer()
    return response
}

fun MainAPI.newLiveSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    fix: Boolean = true,
    initializer: LiveSearchResponse.() -> Unit = {},
): LiveSearchResponse {
    val response = LiveSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    response.initializer()
    return response
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    initializer: AnimeSearchResponse.() -> Unit = {},
): AnimeSearchResponse {
    val response = AnimeSearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    response.initializer()
    return response
}

fun AnimeSearchResponse.addDubStatus(isDub: Boolean, episodes: Int? = null) {
    val status = if (isDub) DubStatus.Dubbed else DubStatus.Subbed
    dubStatus = (dubStatus ?: mutableSetOf()).also { it.add(status) }
    if (episodes != null && episodes > 0) this.episodes[status] = episodes
}

fun AnimeSearchResponse.addDubStatus(
    dubExist: Boolean,
    subExist: Boolean,
    dubEpisodes: Int? = null,
    subEpisodes: Int? = null,
) {
    if (dubExist) {
        addDubStatus(true, dubEpisodes)
    }
    if (subExist) {
        addDubStatus(false, subEpisodes)
    }
}

fun MainAPI.newEpisode(
    data: Any?,
    initializer: Episode.() -> Unit = {},
): Episode {
    val encoded = when (data) {
        is String -> data
        null -> ""
        else -> mapper.writeValueAsString(data)
    }
    return Episode(encoded).apply(initializer)
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    initializer: suspend MovieLoadResponse.() -> Unit = {},
): MovieLoadResponse {
    val response = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        dataUrl = dataUrl,
    )
    response.initializer()
    return response
}

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    initializer: suspend TvSeriesLoadResponse.() -> Unit = {},
): TvSeriesLoadResponse {
    val response = TvSeriesLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        episodes = episodes,
    )
    response.initializer()
    return response
}
