package com.missa.tv360.data.remote

import com.google.gson.Gson
import java.io.BufferedReader
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client minimal pour les catalogues servis par GitHub Pages (JSON statiques).
 *
 * Conception clean-room : on ne fait que GET un JSON et le deserialiser via Gson.
 * Pas de Retrofit ici — le catalogue du dépôt est un ensemble de fichiers statiques
 * exposes sur HTTPS par GitHub Pages, ce qui se prete mieux a un acces direct.
 *
 * Les erreurs sont journalisées avec le code HTTP / type d'exception pour
 * permettre le diagnostic et distinguer un échec distant d'un succès local.
 */
object PagesClient {

    private val gson = Gson()

    /** Résultat détaillé d'une récupération JSON. */
    sealed class FetchResult<out T> {
        data class Success<T>(val data: T) : FetchResult<T>()
        data class HttpError(val code: Int, val message: String) : FetchResult<Nothing>()
        data class NetworkError(val exception: Exception) : FetchResult<Nothing>()
        data class ParseError(val exception: Exception) : FetchResult<Nothing>()
        object NoContent : FetchResult<Nothing>()
    }

    /**
     * Recupere et deserialise un JSON distant vers le type [type].
     * Retourne un [FetchResult] distinguant succès, erreur HTTP, erreur réseau
     * et erreur de parsing JSON. Aucun échec n'est silencieusement transformé
     * en succès.
     */
    fun <T> fetchJson(baseUrl: String, path: String, type: Type, timeoutMs: Int = 12_000): FetchResult<T> {
        val url = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        return try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val body = conn.errorStream.bufferedReader().use { it.readText() }
                FetchResult.HttpError(code, "HTTP $code pour $path (${body.take(200)})")
            } else {
                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                try {
                    FetchResult.Success(gson.fromJson(body, type))
                } catch (e: Exception) {
                    FetchResult.ParseError(e)
                }
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e)
        }
    }

    /**
     * Version pratique retournant [T]? (null en cas d'erreur) pour les
     * appelants qui ne ont pas besoin du détail de l'erreur.
     * @deprecated Préférer [fetchJson] pour distinguer les causes d'échec.
     */
    @Deprecated("Utiliser fetchJson qui retourne FetchResult pour distinguer les erreurs", level = DeprecationLevel.WARNING)
    fun <T> fetchJsonSilent(baseUrl: String, path: String, type: Type, timeoutMs: Int = 12_000): T? {
        val result: FetchResult<T> = fetchJson(baseUrl, path, type, timeoutMs)
        return when (result) {
            is FetchResult.Success<*> -> result.data as T?
            else -> null
        }
    }
}
