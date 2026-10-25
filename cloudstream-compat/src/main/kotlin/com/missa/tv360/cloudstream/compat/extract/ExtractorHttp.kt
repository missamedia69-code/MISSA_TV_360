package com.missa.tv360.cloudstream.compat.extract

import com.lagradost.cloudstream3.app

/** Réponse minimale d'une page d'hébergeur. */
data class PageResponse(val text: String, val url: String, val code: Int)

/**
 * Accès réseau des extracteurs. Séparé pour que les tests injectent de fausses pages
 * (aucun test ne contacte un vrai site).
 */
interface PageFetcher {
    suspend fun get(url: String, referer: String?): PageResponse?
    suspend fun post(url: String, referer: String?, data: Map<String, String>): PageResponse?
}

const val EXTRACTOR_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** Implémentation réelle : le client NiceHttp commun (avec journal HTTP diagnostique). */
object AppPageFetcher : PageFetcher {
    private val headers = mapOf("User-Agent" to EXTRACTOR_USER_AGENT)

    override suspend fun get(url: String, referer: String?): PageResponse? {
        val res = app.get(url, headers = headers, referer = referer)
        return PageResponse(res.text, res.url, res.code)
    }

    override suspend fun post(url: String, referer: String?, data: Map<String, String>): PageResponse? {
        val res = app.post(url, headers = headers, referer = referer, data = data)
        return PageResponse(res.text, res.url, res.code)
    }
}

object ExtractorHttp {
    @Volatile
    var fetcher: PageFetcher = AppPageFetcher
}
