package com.missa.tv360.data.source

import com.missa.tv360.R
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adaptateur Dailymotion, branché sur la **Data API publique**.
 *
 * Dailymotion héberge et diffuse ses propres contenus : la découverte passe
 * par `api.dailymotion.com` (aucune clé requise pour les listes publiques,
 * `family_filter=1` pour rester dans un cadre familial), la résolution par les
 * métadonnées du lecteur officiel (m3u8/MP4). Une intégration commerciale doit
 * passer par le programme partenaire Dailymotion, qui ouvre les quotas et les
 * conditions de diffusion.
 */
object DailymotionFilmSource : FilmSource {
    override val adapterId: String = "dailymotion"

    private const val API_ENDPOINT = "https://api.dailymotion.com/videos"
    private const val PLAYER_METADATA_ENDPOINT =
        "https://www.dailymotion.com/player/metadata/video/"
    private const val USER_AGENT = "MISSA-TV-360/1.0 (Android film engine)"
    private const val FIELDS =
        "id,title,description,duration,thumbnail_480_url,owner.screenname,created_time,tags"

    private val idPattern = Regex("[A-Za-z0-9]{1,32}")
    private val tagsSeparator = Regex("\\s+")

    override suspend fun trending(config: FilmSourceConfig): List<Video> =
        withContext(Dispatchers.IO) {
            fetchList(
                parameters = listOf(
                    "sort" to dailymotionSort(config.trendingSort, "trending"),
                ),
                limit = config.trendingLimit,
                config = config,
            )
        }

    override suspend fun search(query: String, config: FilmSourceConfig): List<Video> =
        withContext(Dispatchers.IO) {
            val cleaned = query.trim().take(120)
            if (cleaned.isBlank()) return@withContext emptyList()
            fetchList(
                parameters = listOf(
                    "search" to cleaned,
                    "sort" to dailymotionSort(config.searchSort, "relevance"),
                ),
                limit = config.searchLimit,
                config = config,
            )
        }

    /**
     * Dailymotion n'expose pas de « similaires » simple : on réutilise la
     * recherche sur le titre en excluant l'élément courant.
     */
    override suspend fun related(video: Video, config: FilmSourceConfig): List<Video> =
        withContext(Dispatchers.IO) {
            val query = video.title.split(tagsSeparator)
                .filter { it.length >= 3 }
                .take(4)
                .joinToString(" ")
                .trim()
                .take(120)
            if (query.isBlank()) return@withContext emptyList()
            fetchList(
                parameters = listOf("search" to query, "sort" to "relevance"),
                limit = config.relatedLimit,
                config = config,
            ).filter { it.id != video.id }
        }

    override suspend fun load(result: Video, config: FilmSourceConfig): ResolvedSource? =
        withContext(Dispatchers.IO) {
            if (result.sourceId != config.id || !idPattern.matches(result.id)) {
                return@withContext null
            }
            val payload = JSONObject(request(PLAYER_METADATA_ENDPOINT + result.id))
            if (payload.optBoolean("private") || payload.optBoolean("protected_delivery")) {
                return@withContext null
            }

            val links = readLinks(payload.optJSONObject("qualities"))
            if (links.isEmpty()) return@withContext null

            val durationSec = payload.optLong("duration", 0L).takeIf { it > 0 }?.toInt()
            val title = payload.optString("title").trim().take(240).ifBlank { result.title }
            val description = payload.optString("description").trim().take(1200)
                .ifBlank { result.description }

            ResolvedSource(
                video = result.copy(
                    title = title,
                    description = description,
                    durationSec = durationSec ?: result.durationSec,
                    format = ExtractorLink.best(links)?.format ?: "hls",
                    source = SOURCE_LABEL,
                ),
                links = links,
            )
        }

    // ------------------------------------------------------------------ API

    private fun fetchList(
        parameters: List<Pair<String, String>>,
        limit: Int,
        config: FilmSourceConfig,
    ): List<Video> {
        val query = buildList {
            addAll(parameters)
            add("fields" to FIELDS)
            add("limit" to limit.coerceIn(1, 50).toString())
            // Cadre familial : évite les résultats explicites sur un téléviseur.
            add("family_filter" to "1")
        }
        val url = "$API_ENDPOINT?" + query.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val payload = JSONObject(request(url))
        val list = payload.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            list.optJSONObject(index)?.let { toVideo(it, config) }
        }.distinctBy(Video::id)
    }

    private fun toVideo(json: JSONObject, config: FilmSourceConfig): Video? {
        val id = json.optString("id").trim()
        val title = json.optString("title").trim().take(240)
        if (!idPattern.matches(id) || title.isBlank()) return null

        val description = cleanText(json.optString("description")).take(1200)
        val duration = json.optLong("duration", 0L).takeIf { it > 0 }?.toInt()
        val creator = json.optString("owner.screenname").trim().take(120)
        val year = yearFromEpoch(json.optLong("created_time", 0L))
        val tags = readTags(json)

        return Video(
            id = id,
            title = title,
            description = description.ifBlank { "Vidéo Dailymotion." },
            cardRes = R.drawable.card_tbd,
            streamUrl = "",
            kind = ContentKind.MOVIE,
            durationSec = duration,
            cardUrl = json.optString("thumbnail_480_url").takeIf { it.startsWith("https://") },
            sourceId = config.id,
            tags = tags,
            creator = creator.ifBlank { null },
            releaseYear = year,
            source = SOURCE_LABEL,
        )
    }

    /** `tags` est un tableau JSON côté Dailymotion (parfois une chaîne). */
    private fun readTags(json: JSONObject): List<String> {
        val array = json.optJSONArray("tags")
        if (array != null) {
            return (0 until array.length())
                .mapNotNull { index ->
                    array.optString(index).trim()
                        .takeIf { it.isNotEmpty() && it.length <= 40 }
                }
                .take(12)
        }
        return json.optString("tags").split(tagsSeparator)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 40 }
            .take(12)
    }

    /** Convertit l'objet `qualities` du lecteur en liens triables. */
    private fun readLinks(qualities: JSONObject?): List<ExtractorLink> {
        if (qualities == null) return emptyList()
        val links = mutableListOf<ExtractorLink>()
        val keys = qualities.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entries: JSONArray = qualities.optJSONArray(key) ?: continue
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val url = entry.optString("url").trim()
                // Un flux distant ne doit jamais être servi en clair.
                if (!url.startsWith("https://")) continue
                val type = entry.optString("type").lowercase(Locale.ROOT)
                val format = if (type.contains("mpegurl")) "hls" else "mp4"
                val quality = key.toIntOrNull()
                    ?: entry.optInt("quality", 0).takeIf { it > 0 }
                    ?: 0
                links += ExtractorLink(
                    url = url,
                    quality = quality,
                    format = format,
                    headers = ExtractorLink.sanitizeHeaders(
                        mapOf("User-Agent" to USER_AGENT, "Referer" to "https://www.dailymotion.com/"),
                    ),
                )
            }
        }
        return links.distinctBy { it.url }
    }

    // -------------------------------------------------------------- outils

    private fun dailymotionSort(value: String, fallback: String): String =
        when (value.lowercase(Locale.ROOT)) {
            "trending" -> "trending"
            "recent", "date desc" -> "recent"
            "visited", "downloads desc" -> "visited"
            "relevance" -> "relevance"
            else -> fallback
        }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Dailymotion HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun cleanText(value: String): String = value
        .replace(tagsPattern, " ")
        .replace(whitespacePattern, " ")
        .trim()

    private fun yearFromEpoch(seconds: Long): String? {
        if (seconds <= 0L) return null
        return try {
            SimpleDateFormat("yyyy", Locale.ROOT).format(Date(seconds * 1000L))
        } catch (_: Exception) {
            null
        }
    }

    private const val SOURCE_LABEL = "Dailymotion"

    private val tagsPattern = Regex("<[^>]*>")
    private val whitespacePattern = Regex("\\s+")
}
