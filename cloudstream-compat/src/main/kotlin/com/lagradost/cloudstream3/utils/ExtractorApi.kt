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

private fun normalizeExtractorHost(value: String?): String? =
    value?.lowercase()?.trim()?.trimEnd('.')?.removePrefix("www.")?.takeIf { it.isNotBlank() }

/**
 * Choisit d'abord l'extracteur dont l'hôte canonique correspond à l'URL.
 * Les alias ne sont consultés qu'ensuite : un alias erroné déclaré par un
 * autre plugin ne doit pas détourner uqload.com vers Vudeo, par exemple.
 */
internal fun findExtractorForHost(
    host: String,
    extractors: List<ExtractorApi>,
    trustAliases: Boolean = false,
): ExtractorApi? {
    val normalizedHost = normalizeExtractorHost(host) ?: return null
    fun matches(candidateHost: String): Boolean =
        normalizedHost == candidateHost || normalizedHost.endsWith(".$candidateHost")

    val normalized = extractors.asReversed()
    val canonicalMatch = normalized.firstOrNull { candidate ->
        val canonicalHost = runCatching { normalizeExtractorHost(URI(candidate.mainUrl).host) }
            .getOrNull()
        canonicalHost != null && matches(canonicalHost)
    }
    if (canonicalMatch != null) return canonicalMatch

    // Ne jamais détourner Uqload vers un extracteur tiers via un alias erroné.
    // Le vrai extracteur Uqload doit être enregistré pour que ce domaine soit résolu.
    // Cette règle ne vise que les alias déclarés par les plugins ; les extracteurs intégrés
    // à l'application (trustAliases = true) sont fiables et déclarent leurs propres alias.
    if (!trustAliases && (normalizedHost == "uqload.com" || normalizedHost == "uqload.co")) return null

    return normalized.firstOrNull { candidate ->
        candidate.supportedHosts.mapNotNull(::normalizeExtractorHost).any(::matches)
    }
}

/**
 * Résolution générique ; aucun lien n'est fabriqué si aucun extracteur ne correspond.
 * Ordre : extracteur déclaré par l'extension, puis extracteur intégré à l'application.
 * Si le premier n'émet aucun lien (ou plante), le suivant est essayé.
 */
private suspend fun resolveExtractor(
    url: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val host = runCatching { normalizeExtractorHost(URI(url).host) }
        .getOrNull()
        ?: "inconnu"
    val fromPlugin = findExtractorForHost(host, CloudstreamCompatRegistry.extractors())
    val builtIn = findExtractorForHost(host, CloudstreamCompatRegistry.builtInExtractors(), trustAliases = true)
    val candidates = listOfNotNull(fromPlugin, builtIn).distinctBy { System.identityHashCode(it) }
    PluginLog.i("loadExtractor $host : extracteur=${candidates.joinToString("+") { it.name }.ifEmpty { "AUCUN" }}")
    if (candidates.isEmpty()) return false

    for (extractor in candidates) {
        var emittedLinks = 0
        var failure: String? = null
        try {
            extractor.getUrl(url, referer, subtitleCallback) { link ->
                emittedLinks++
                callback(link)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            failure = "${t.javaClass.simpleName}: ${t.message}"
        }
        PluginLog.i(
            "extractorResult host=$host name=${extractor.name} liens=$emittedLinks " +
                "sourcePlugin=${extractor.sourcePlugin ?: "intégré"}" +
                (failure?.let { " erreur=$it" } ?: "")
        )
        if (emittedLinks > 0) return true
    }
    return false
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
