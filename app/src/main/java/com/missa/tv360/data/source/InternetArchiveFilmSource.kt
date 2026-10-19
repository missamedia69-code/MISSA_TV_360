package com.missa.tv360.data.source

import android.net.Uri
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

/** Première source brute du moteur Films : recherche et résolution Internet Archive. */
object InternetArchiveFilmSource : FilmSource {
    override val id: String = "internet_archive"

    private const val SEARCH_ENDPOINT = "https://archive.org/advancedsearch.php"
    private const val METADATA_ENDPOINT = "https://archive.org/metadata/"
    private const val THUMBNAIL_ENDPOINT = "https://archive.org/services/img/"
    private const val RESULT_LIMIT = 40

    private val tags = Regex("<[^>]*>")
    private val whitespace = Regex("\\s+")

    override suspend fun search(query: String): List<Video> = withContext(Dispatchers.IO) {
        val cleanedQuery = query.trim().take(120)
        if (cleanedQuery.isBlank()) return@withContext emptyList()

        // Aucun filtre de droits ou de collection : la recherche couvre les
        // notices Internet Archive de type « movies » correspondant au titre.
        val escapedPhrase = cleanedQuery
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
        val parameters = listOf(
            "q" to "mediatype:movies AND title:\"$escapedPhrase\"",
            "fl[]" to "identifier",
            "fl[]" to "title",
            "fl[]" to "description",
            "fl[]" to "year",
            "rows" to RESULT_LIMIT.toString(),
            "page" to "1",
            "output" to "json",
        )
        val url = "$SEARCH_ENDPOINT?" + parameters.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

        val payload = JSONObject(request(url))
        val documents = payload.optJSONObject("response")?.optJSONArray("docs")
            ?: return@withContext emptyList()
        (0 until documents.length()).mapNotNull { index ->
            val document = documents.optJSONObject(index) ?: return@mapNotNull null
            val identifier = document.optString("identifier").trim()
            val title = readText(document.opt("title")).take(240)
            if (identifier.isBlank() || title.isBlank()) return@mapNotNull null

            val description = cleanText(readText(document.opt("description"))).take(1200)
            val year = readText(document.opt("year")).trim()
            val cardDescription = when {
                description.isNotBlank() -> description
                year.isNotBlank() -> "Année : $year · Internet Archive"
                else -> "Film référencé sur Internet Archive."
            }
            Video(
                id = identifier,
                title = title,
                description = cardDescription,
                cardRes = R.drawable.card_tbd,
                streamUrl = "",
                kind = ContentKind.MOVIE,
                cardUrl = "$THUMBNAIL_ENDPOINT${encodePathSegment(identifier)}",
                sourceId = id,
            )
        }.distinctBy(Video::id)
    }

    override suspend fun resolve(result: Video): Video? = withContext(Dispatchers.IO) {
        if (result.sourceId != id || result.id.isBlank()) return@withContext null

        val identifier = result.id
        val payload = JSONObject(request(METADATA_ENDPOINT + encodePathSegment(identifier)))
        val metadata = payload.optJSONObject("metadata") ?: JSONObject()
        val files = payload.optJSONArray("files") ?: JSONArray()
        val playableFiles = (0 until files.length()).mapNotNull { index ->
            val file = files.optJSONObject(index) ?: return@mapNotNull null
            val name = file.optString("name").trim()
            if (!name.endsWith(".mp4", ignoreCase = true)) return@mapNotNull null
            val pathParts = name.replace('\\', '/').split('/')
            if (pathParts.any { it == "." || it == ".." }) return@mapNotNull null
            ArchiveFile(
                name = name,
                height = file.optString("height").toLongOrNull() ?: 0L,
                size = file.optString("size").toLongOrNull() ?: 0L,
            )
        }
        val selected = playableFiles.maxWithOrNull(
            compareBy<ArchiveFile> { it.height }.thenBy { it.size },
        ) ?: return@withContext null

        val title = readText(metadata.opt("title")).trim()
            .ifBlank { result.title }
        val description = cleanText(readText(metadata.opt("description")))
            .ifBlank { result.description }
        val encodedName = Uri.encode(selected.name, "-._~()@:+")
        Video(
            id = "ia:$identifier",
            title = title,
            description = description,
            cardRes = R.drawable.card_tbd,
            streamUrl = "https://archive.org/download/" +
                "${encodePathSegment(identifier)}/$encodedName",
            kind = ContentKind.MOVIE,
            format = "mp4",
            cardUrl = "$THUMBNAIL_ENDPOINT${encodePathSegment(identifier)}",
        )
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MISSA-TV-360/1.0 (Android film search)")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Internet Archive HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun encodePathSegment(value: String): String = Uri.encode(value, "-._~")

    private fun readText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> (0 until value.length())
            .map { value.optString(it) }
            .filter { it.isNotBlank() && it != "null" }
            .joinToString(" ")
        else -> ""
    }

    private fun cleanText(value: String): String =
        value.replace(tags, " ")
            .replace(whitespace, " ")
            .trim()
            .take(1200)

    private data class ArchiveFile(
        val name: String,
        val height: Long,
        val size: Long,
    )
}
