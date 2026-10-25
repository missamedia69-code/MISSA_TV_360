package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import com.missa.tv360.cloudstream.compat.CloudstreamCompatRegistry
import com.missa.tv360.api.PluginLog
import java.net.URI

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

    /**
     * Hôtes reconnus par cet extracteur. Les alias doivent être déclarés explicitement
     * pour éviter qu'un domaine ressemblant (ex. uqload.example) soit accepté.
     */
    open val supportedHosts: Set<String>
        get() = runCatching {
            URI(mainUrl).host?.lowercase()?.removePrefix("www.")?.let(::setOf)
        }.getOrNull() ?: emptySet()

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
private suspend fun resolveExtractor(
    url: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val host = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "inconnu"
    val extractor = CloudstreamCompatRegistry.extractors().asReversed().firstOrNull { candidate ->
        val supportedHosts = candidate.supportedHosts
            .map { it.lowercase().trim().trimEnd('.').removePrefix("www.") }
            .filter { it.isNotBlank() }
        supportedHosts.any { supportedHost ->
            host == supportedHost || host.endsWith(".$supportedHost")
        }
    }
    PluginLog.i("loadExtractor $host : extracteur=${extractor?.name ?: "AUCUN"}")
    extractor ?: return false
    extractor.getUrl(url, referer, subtitleCallback, callback)
    return true
}

/** Signature de compatibilité historique à trois paramètres. */
suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean = resolveExtractor(url, null, subtitleCallback, callback)

/**
 * Signature binaire attendue par les extensions Cloudstream :
 * loadExtractor(String, String, Function1, Function1, Continuation).
 */
suspend fun loadExtractor(
    url: String,
    referer: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean = resolveExtractor(url, referer, subtitleCallback, callback)
