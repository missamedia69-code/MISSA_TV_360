package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import com.missa.tv360.cloudstream.compat.CloudstreamCompatRegistry

enum class Qualities(val value: Int) {
    Unknown(400),
    P144(144),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P1440(1440),
    P2160(2160),
}

/** Constructeur historique à huit paramètres, conservé avec sa forme $default. */
open class ExtractorLink(
    open val source: String,
    open val name: String,
    open val url: String,
    referer: String? = null,
    open val quality: Int = Qualities.Unknown.value,
    open val isM3u8: Boolean = false,
    open val headers: Map<String, String> = emptyMap(),
    open val extractorData: String? = null,
) {
    open var referer: String = referer.orEmpty()

    fun getAllHeaders(): Map<String, String> =
        if (referer.isBlank() || headers.keys.any { it.equals("referer", true) }) {
            headers
        } else {
            headers + ("Referer" to referer)
        }
}

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean
    var sourcePlugin: String? = null

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? = emptyList()
}

/** Résolution générique ; aucun lien n'est fabriqué si aucun extracteur ne correspond. */
suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val comparable = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    val extractor = CloudstreamCompatRegistry.extractors().asReversed().firstOrNull { candidate ->
        val base = candidate.mainUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
        base.isNotBlank() && comparable.startsWith(base)
    } ?: return false
    extractor.getUrl(url, null, subtitleCallback, callback)
    return true
}
