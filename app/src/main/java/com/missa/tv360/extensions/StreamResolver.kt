package com.missa.tv360.extensions

import android.net.Uri
import android.util.Log
import com.missa.tv360.api.Resolution
import com.missa.tv360.api.ResolvingProvider
import com.missa.tv360.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StreamResolver {
    private const val TAG = "StreamResolver"

    suspend fun resolve(video: Video): Resolution {
        val page = video.pageUrl ?: return Resolution(error = "Pas de page")
        val id = video.providerId ?: return Resolution(error = "Pas de fournisseur")
        val provider = ExtensionManager.providers().firstOrNull { it.id == id }
            as? ResolvingProvider ?: return Resolution(error = "Fournisseur non résolvable")

        Log.i(TAG, "resolveStart provider=$id page=${safeUrlSummary(page)}")
        val result = withContext(Dispatchers.IO) { provider.resolve(page) }
        val streamSummary = result.streams.take(8).joinToString(";") { stream ->
            "${safeUrlSummary(stream.url)} format=${stream.format} " +
                "headerNames=${stream.headers.keys.joinToString(",")}"
        }
        Log.i(
            TAG,
            "resolveEnd provider=$id streams=${result.streams.size} " +
                "candidates=${streamSummary.ifBlank { "none" }} " +
                "hasError=${!result.error.isNullOrBlank()}"
        )
        return result
    }

    /** N'enregistre jamais les jetons ni les paramètres temporaires des URL vidéo. */
    private fun safeUrlSummary(raw: String): String {
        val uri = Uri.parse(raw)
        val host = uri.host ?: return "invalid-uri"
        val lastSegment = uri.lastPathSegment.orEmpty()
        val extension = lastSegment.substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{2,6}")) }
            ?: "none"
        return "${uri.scheme ?: "unknown"}://$host segments=${uri.pathSegments.size} " +
            "extension=$extension hasQuery=${!uri.query.isNullOrEmpty()}"
    }
}
