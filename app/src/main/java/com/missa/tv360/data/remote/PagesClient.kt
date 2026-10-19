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
 */
object PagesClient {

    private val gson = Gson()

    /**
     * Recupere et deserialise un JSON distant vers le type [type].
     * Retourne null en cas d'erreur (reseau, HTTP != 200, JSON invalide) pour
     * que l'appelant bascule silencieusement sur le catalogue local embarque.
     */
    fun <T> fetchJson(baseUrl: String, path: String, type: Type, timeoutMs: Int = 12_000): T? {
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
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            gson.fromJson(body, type)
        } catch (e: Exception) {
            null
        }
    }
}
