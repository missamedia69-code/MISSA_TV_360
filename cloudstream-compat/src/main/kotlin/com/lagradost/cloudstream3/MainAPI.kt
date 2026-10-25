package com.lagradost.cloudstream3

import android.app.Activity
import android.util.Base64
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.serialization.json.Json
import java.util.Date

/** Jackson partagé, compatible avec les helpers présents dans le DEX du prototype. */
val mapper: JsonMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

fun getJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

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
    Torrent,
    Documentary,
    Music,
}

enum class DubStatus { Dubbed, Subbed, None }

enum class ShowStatus {
    Completed,
    Ongoing,
    Unknown;
    companion object
}

enum class SearchQuality {
    SD,
    HD,
    FHD,
    UHD,
    FourK,
    BlueRay,
    CAM,
    HQ,
    HdCam,
    CamRip,
    WebRip;

    companion object
}

fun getQualityFromString(str: String?): SearchQuality? {
    if (str == null) return null
    val upper = str.uppercase()
    return when {
        upper.contains("4K") || upper.contains("2160") -> SearchQuality.FourK
        upper.contains("1080") || upper.contains("FHD") -> SearchQuality.FHD
        upper.contains("720") || upper.contains("HD") -> SearchQuality.HD
        upper.contains("BLURAY") || upper.contains("BRRIP") -> SearchQuality.BlueRay
        upper.contains("CAM") -> SearchQuality.CAM
        upper.contains("HQ") -> SearchQuality.HQ
        else -> SearchQuality.SD
    }
}

data class Score(val value: Int) {
    companion object {
        fun from10(value: Any?): Score? {
            val v = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                is Double -> value.toInt()
                is Float -> value.toInt()
                else -> null
            } ?: return null
            return Score(v)
        }
        fun from100(value: Any?): Score? = from10(value)
        fun from5(value: Any?): Score? = from10(value)
    }
}

enum class ActorRole { Main, Supporting, Background; companion object }

data class Actor(
    val name: String,
    val image: String? = null,
)

data class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val voiceActor: Actor? = null,
)

object CommonActivity {
    fun getActivity(): Activity? = null
    fun showToast(activity: Activity?, message: String?, duration: Int?) {}
    fun showToast(message: String?, duration: Int?) {}
}

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
    var score: Score?
    var quality: SearchQuality?
    var posterHeaders: Map<String, String>?
}

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var plot: String?
    var score: Score?
    var backgroundPosterUrl: String?
    var logoUrl: String?
    var contentRating: String?
    var duration: Int?
    var actors: List<Actor>?
    var recommendations: List<SearchResponse>?
    var showStatus: ShowStatus?

    companion object {
        fun addTrailer(load: LoadResponse, url: String) {}
        fun addImdbId(load: LoadResponse, id: String) {}
        fun addTMDbId(load: LoadResponse, id: String) {}
        fun addAniListId(load: LoadResponse, id: Int) {}
        fun addMalId(load: LoadResponse, id: Int) {}
        fun addSimklId(load: LoadResponse, id: Int) {}
        fun addKitsuId(load: LoadResponse, id: String) {}
        fun addDuration(load: LoadResponse, duration: String) {
            // Cloudstream extensions commonly pass values such as "120 min".
            // Keep a usable numeric duration when the string contains minutes.
            val minutes = Regex("\\d+").find(duration)?.value?.toIntOrNull()
            if (minutes != null) load.duration = minutes
        }
        fun addActorsOnly(load: LoadResponse, actors: List<Actor>) { load.actors = actors }
        fun addActorNames(load: LoadResponse, names: List<String>) { load.actors = names.map { Actor(it) } }
    }
}

// Companion extension container for LoadResponse helpers
class LoadResponseCompanion {
    companion object {
        fun addTrailer(load: LoadResponse, url: String) {}
        fun addImdbId(load: LoadResponse, id: String) {}
        fun addTMDbId(load: LoadResponse, id: String) {}
        fun addAniListId(load: LoadResponse, id: Int) {}
        fun addMalId(load: LoadResponse, id: Int) {}
        fun addSimklId(load: LoadResponse, id: Int) {}
        fun addKitsuId(load: LoadResponse, id: String) {}
        fun addActorsOnly(load: LoadResponse, actors: List<Actor>) { load.actors = actors }
        fun addActorNames(load: LoadResponse, names: List<String>) { load.actors = names.map { Actor(it) } }
    }
}

data class MovieSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.Movie,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse {
    // Keep score as a property, but not in the primary constructor: the binary
    // constructor signature must match the Cloudstream plugin ABI.
    override var score: Score? = null
}

data class TvSeriesSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = TvType.TvSeries,
    override var posterUrl: String? = null,
    var year: Int? = null,
    var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
    override var quality: SearchQuality? = null,
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
    override var posterHeaders: Map<String, String>? = null,
    override var score: Score? = null,
    override var quality: SearchQuality? = null,
    var otherName: String? = null,
) : SearchResponse

data class LiveSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    override var score: Score? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    var lang: String? = null,
) : SearchResponse

data class Episode(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var description: String? = null,
    var date: Long? = null,
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
    override var recommendations: List<SearchResponse>? = null,
    var comingSoon: Boolean = false,
    override var score: Score? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var duration: Int? = null,
    override var actors: List<Actor>? = null,
    override var showStatus: ShowStatus? = null,
    var posterHeaders: Map<String, String>? = null,
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
    override var recommendations: List<SearchResponse>? = null,
    override var score: Score? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var duration: Int? = null,
    override var actors: List<Actor>? = null,
    override var showStatus: ShowStatus? = null,
    var posterHeaders: Map<String, String>? = null,
) : LoadResponse

data class AnimeLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    var episodes: List<Episode> = emptyList(),
    override var posterUrl: String? = null,
    override var plot: String? = null,
    var year: Int? = null,
    var tags: List<String>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var score: Score? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var duration: Int? = null,
    override var actors: List<Actor>? = null,
    override var showStatus: ShowStatus? = null,
    var engName: String? = null,
    var japName: String? = null,
    var posterHeaders: Map<String, String>? = null,
) : LoadResponse

data class LiveStreamLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Live,
    override var posterUrl: String? = null,
    override var plot: String? = null,
    override var score: Score? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var contentRating: String? = null,
    override var duration: Int? = null,
    override var actors: List<Actor>? = null,
    override var recommendations: List<SearchResponse>? = null,
    override var showStatus: ShowStatus? = null,
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

data class SearchResponseList(
    val items: List<SearchResponse>,
    val hasNext: Boolean? = null,
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

    open suspend fun search(query: String, page: Int): SearchResponseList? {
        val res = search(query) ?: return null
        return SearchResponseList(res, false)
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

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url == null) return null
    return fixUrl(url)
}

fun base64Encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
fun base64DecodeArray(str: String): ByteArray = Base64.decode(str, Base64.DEFAULT)
fun base64Decode(str: String): String = String(base64DecodeArray(str), Charsets.UTF_8)

fun newHomePageResponse(
    name: String,
    list: List<SearchResponse>,
    hasNext: Boolean? = null,
): HomePageResponse = HomePageResponse(
    items = listOf(HomePageList(name, list)),
    hasNext = hasNext ?: list.isNotEmpty(),
)

fun newHomePageResponse(
    list: HomePageList,
    hasNext: Boolean? = null,
): HomePageResponse = HomePageResponse(
    items = listOf(list),
    hasNext = hasNext ?: list.list.isNotEmpty(),
)

fun newHomePageResponse(
    list: List<HomePageList>,
    hasNext: Boolean? = null,
): HomePageResponse = HomePageResponse(
    items = list,
    hasNext = hasNext ?: list.any { it.list.isNotEmpty() },
)

fun List<SearchResponse>.toNewSearchResponseList(hasNext: Boolean? = null): SearchResponseList {
    return SearchResponseList(this, hasNext)
}

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

fun AnimeSearchResponse.addSub(episodes: Int? = null) {
    addDubStatus(isDub = false, episodes = episodes)
}

fun AnimeSearchResponse.addDub(episodes: Int? = null) {
    addDubStatus(isDub = true, episodes = episodes)
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

fun addDate(episode: Episode, date: String?) {}
fun addDate(episode: Episode, timestamp: Long?) { episode.date = timestamp }
fun addDate(episode: Episode, date: Date?) { episode.date = date?.time }

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

suspend fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    comingSoonIfNone: Boolean = true,
    initializer: suspend AnimeLoadResponse.() -> Unit = {},
): AnimeLoadResponse {
    val response = AnimeLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
    )
    response.initializer()
    return response
}

suspend fun MainAPI.newLiveStreamLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Live,
    dataUrl: String = "",
    initializer: suspend LiveStreamLoadResponse.() -> Unit = {},
): LiveStreamLoadResponse {
    val response = LiveStreamLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
    )
    response.initializer()
    return response
}

fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<Episode>) {
    this.episodes = this.episodes + episodes
}

fun AnimeLoadResponse.addEpisodes(episodes: List<Episode>) {
    this.episodes = this.episodes + episodes
}

fun SearchResponse.addQuality(qualityStr: String?) {
    this.quality = getQualityFromString(qualityStr)
}
