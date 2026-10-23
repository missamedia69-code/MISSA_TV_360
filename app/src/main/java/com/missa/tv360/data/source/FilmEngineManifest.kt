package com.missa.tv360.data.source

import com.google.gson.annotations.SerializedName

/** JSON livrable du dossier engines/films/; les opérations exécutées restent natives à l'app. */
data class FilmEngineManifest(
    @SerializedName("schema_version") val schemaVersion: Int? = null,
    @SerializedName("engine_id") val engineId: String? = null,
    val version: Int? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val sources: List<FilmSourceManifest?>? = null,
) {
    fun toRuntimeConfig(): FilmEngineConfig? {
        if (schemaVersion != 1 || engineId != "films" || (version ?: 0) < 1) return null
        val sourceConfigs = sources.orEmpty().mapNotNull { source -> source?.toRuntimeConfig() }
        if (sourceConfigs.isEmpty()) return null
        return FilmEngineConfig(version = version ?: 1, sources = sourceConfigs)
    }
}

data class FilmSourceManifest(
    val id: String? = null,
    val adapter: String? = null,
    val enabled: Boolean? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("trending_query") val trendingQuery: String? = null,
    @SerializedName("trending_sort") val trendingSort: String? = null,
    @SerializedName("trending_limit") val trendingLimit: Int? = null,
    @SerializedName("search_limit") val searchLimit: Int? = null,
    @SerializedName("search_fields") val searchFields: List<String>? = null,
    @SerializedName("search_sort") val searchSort: String? = null,
    @SerializedName("playable_formats") val playableFormats: List<String>? = null,
    @SerializedName("quality_order") val qualityOrder: String? = null,
    @SerializedName("related_limit") val relatedLimit: Int? = null,
    @SerializedName("related_sort") val relatedSort: String? = null,
    @SerializedName("related_fields") val relatedFields: List<String>? = null,
    /** Types de contenu servis par la source (même vocabulaire que les dépôts CloudStream). */
    @SerializedName("tv_types") val tvTypes: List<String>? = null,
) {
    fun toRuntimeConfig(): FilmSourceConfig? {
        if (enabled != true) return null
        val safeId = id?.trim()?.takeIf { it.matches(Regex("[a-z0-9_-]{1,48}")) } ?: return null
        val safeAdapter = adapter?.trim()?.takeIf { it.matches(Regex("[a-z0-9_-]{1,48}")) }
            ?: return null
        // Le manifeste ne transporte que des paramètres : jamais d'endpoint,
        // d'URL de flux ni d'en-tête. Chaque adaptateur ignore ce qu'il
        // n'utilise pas, et l'app reste seule à décider où elle appelle.
        val safeMediaType = mediaType?.trim()?.lowercase()
            ?.takeIf { it in MEDIA_TYPES } ?: return null
        val safeTrendingQuery = trendingQuery?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 160 && it.none(Char::isISOControl) }
            ?: DEFAULT_TRENDING_QUERY.getValue(safeMediaType)
        val safeSorts = setOf("downloads desc", "date desc", "title asc", "relevance", "recent", "trending")
        val safePlayableFormats = playableFormats.orEmpty()
            .map { it.trim().lowercase() }
            .filter { it in PLAYABLE_FORMATS }
            .distinct()
            .ifEmpty { listOf("mp4") }
        val safeTvTypes = tvTypes.orEmpty()
            .map { it.trim().lowercase() }
            .filter { it in TV_TYPES }
            .distinct()
            .take(4)
            .ifEmpty { listOf("movies") }
        val safeSearchFields = searchFields.orEmpty()
            .filter { it in setOf("title", "creator", "subject", "description") }
            .distinct()
            .take(4)
            .ifEmpty { listOf("title") }
        val safeRelatedFields = relatedFields.orEmpty()
            .filter { it in setOf("subject", "creator", "title") }
            .distinct()
            .take(3)
        return FilmSourceConfig(
            id = safeId,
            adapter = safeAdapter,
            mediaType = safeMediaType,
            trendingQuery = safeTrendingQuery,
            trendingSort = trendingSort?.takeIf { it in safeSorts } ?: "downloads desc",
            trendingLimit = (trendingLimit ?: 20).coerceIn(1, 50),
            searchLimit = (searchLimit ?: 40).coerceIn(1, 50),
            searchFields = safeSearchFields,
            searchSort = searchSort?.takeIf { it in safeSorts } ?: "downloads desc",
            playableFormats = safePlayableFormats,
            qualityOrder = qualityOrder?.takeIf { it == "height_then_size" } ?: "height_then_size",
            relatedLimit = (relatedLimit ?: 12).coerceIn(1, 30),
            relatedSort = relatedSort?.takeIf { it in safeSorts } ?: "downloads desc",
            relatedFields = safeRelatedFields.ifEmpty { listOf("subject", "creator", "title") },
            tvTypes = safeTvTypes,
        )
    }

    companion object {
        /** Types de médias acceptés dans un manifeste distant. */
        private val MEDIA_TYPES = setOf("movies", "series", "music", "games")

        /** Formats que le lecteur sait ouvrir (media3 + ExoPlayer HLS). */
        private val PLAYABLE_FORMATS = setOf("mp4", "hls", "mp3")

        /** Taxonomie déclarative, alignée sur les dépôts d'extensions. */
        private val TV_TYPES = setOf("live", "movies", "series", "others")

        private val DEFAULT_TRENDING_QUERY = mapOf(
            "movies" to "mediatype:movies",
            "series" to "mediatype:movies",
            "music" to "mediatype:audio",
            "games" to "mediatype:software",
        )
    }
}

data class FilmEngineConfig(
    val version: Int,
    val sources: List<FilmSourceConfig>,
)

data class FilmSourceConfig(
    val id: String,
    val adapter: String,
    val mediaType: String,
    val trendingQuery: String,
    val trendingSort: String,
    val trendingLimit: Int,
    val searchLimit: Int,
    val searchFields: List<String>,
    val searchSort: String,
    val playableFormats: List<String>,
    val qualityOrder: String,
    val relatedLimit: Int,
    val relatedSort: String,
    val relatedFields: List<String>,
    /** Types de contenu servis (live, movies, series, others). */
    val tvTypes: List<String> = listOf("movies"),
) {
    companion object {
        fun bundledDefault() = FilmSourceConfig(
            id = "internet_archive",
            adapter = "internet_archive",
            mediaType = "movies",
            trendingQuery = "mediatype:movies",
            trendingSort = "downloads desc",
            trendingLimit = 20,
            searchLimit = 40,
            searchFields = listOf("title", "creator", "subject"),
            searchSort = "downloads desc",
            playableFormats = listOf("mp4"),
            qualityOrder = "height_then_size",
            relatedLimit = 12,
            relatedSort = "downloads desc",
            relatedFields = listOf("subject", "creator", "title"),
            tvTypes = listOf("movies"),
        )

        /** Même configuration effective que la source Dailymotion du manifeste. */
        fun bundledDailymotion() = bundledDefault().copy(
            id = "dailymotion",
            adapter = "dailymotion",
            trendingSort = "trending",
            searchSort = "relevance",
            playableFormats = listOf("hls", "mp4"),
            relatedSort = "relevance",
            tvTypes = listOf("movies", "others"),
        )
    }
}

/** Repli en mémoire : conserve toutes les sources compilées, même sans manifeste lisible. */
fun bundledFilmEngineConfig() = FilmEngineConfig(
    version = 2,
    sources = listOf(
        FilmSourceConfig.bundledDefault(),
        FilmSourceConfig.bundledDailymotion(),
    ),
)
