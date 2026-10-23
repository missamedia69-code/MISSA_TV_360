package com.missa.tv360.data.json

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Client HTTPS minimal et borné pour les fichiers JSON du dépôt. */
object JsonHttpClient {
    private const val MAX_INDEX_BYTES = 256 * 1024
    private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024

    sealed interface FetchResult {
        data class Success(val text: String) : FetchResult
        data class HttpError(val code: Int) : FetchResult
        data class NetworkError(val error: Exception) : FetchResult
    }

    fun fetch(baseUrl: String, path: String): FetchResult {
        val url = try {
            val base = URL(baseUrl.trimEnd('/') + "/")
            require(base.protocol == "https" && base.host.isNotBlank())
            URL(base, path.trimStart('/'))
        } catch (error: Exception) {
            return FetchResult.NetworkError(error)
        }
        if (url.protocol != "https") {
            return FetchResult.NetworkError(IOException("Une source JSON doit utiliser HTTPS"))
        }
        val connection = try {
            url.openConnection() as HttpURLConnection
        } catch (error: Exception) {
            return FetchResult.NetworkError(error)
        }
        return try {
            connection.apply {
                connectTimeout = 5_000
                readTimeout = 8_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    if (connection.url.protocol != "https") {
                        throw IOException("Redirection JSON non HTTPS refusée")
                    }
                    val limit = if (path == "index.json") MAX_INDEX_BYTES else MAX_CATALOG_BYTES
                    val text = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > limit) throw IOException("Fichier JSON trop volumineux")
                            output.write(buffer, 0, read)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                    FetchResult.Success(text)
                }
                else -> FetchResult.HttpError(code)
            }
        } catch (error: Exception) {
            FetchResult.NetworkError(error)
        } finally {
            connection.disconnect()
        }
    }
}
