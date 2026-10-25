package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.missa.tv360.api.PluginLog
import com.missa.tv360.cloudstream.compat.extract.EXTRACTOR_USER_AGENT
import com.missa.tv360.cloudstream.compat.extract.ExtractorHttp
import com.missa.tv360.cloudstream.compat.extract.ExtractorParsing
import com.missa.tv360.cloudstream.compat.extract.PageResponse
import java.net.URI
import kotlinx.coroutines.CancellationException

/**
 * Extracteurs intégrés. Les extracteurs des extensions héritent de ces classes : ce qui est
 * écrit ici dans getUrl() sert donc aussi à DoodStream, Fembed, etc. déclarés par les extensions.
 * L'hôte utilisé est TOUJOURS celui de l'URL reçue (jamais mainUrl : il peut être un autre miroir).
 */

private fun hostOf(url: String): String? =
    runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }

private fun resolveUrl(base: String, target: String): String =
    runCatching { URI(base).resolve(target).toString() }.getOrDefault(target)

private suspend fun fetchPage(label: String, url: String, referer: String?): PageResponse? {
    val page = ExtractorHttp.fetcher.get(url, referer ?: hostOf(url)?.let { "https://$it/" })
    PluginLog.i("$label : page HTTP ${page?.code ?: "aucune réponse"}")
    return page
}

private fun ExtractorApi.emit(
    urls: List<Pair<String, Int>>,
    pageHost: String,
    callback: (ExtractorLink) -> Unit,
) {
    for ((link, quality) in urls) {
        callback(
            ExtractorLink(
                source = name,
                name = name,
                url = link,
                referer = "https://$pageHost/",
                quality = if (quality > 0) quality else Qualities.Unknown.value,
                isM3u8 = link.contains(".m3u8", ignoreCase = true),
                headers = mapOf("User-Agent" to EXTRACTOR_USER_AGENT),
            ),
        )
    }
}

/** Exécute une extraction en journalisant l'échec ; ne masque jamais l'annulation. */
private suspend fun guarded(label: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        PluginLog.e("$label : extraction en échec", t)
    }
}

/** Page unique avec parseur (Uqload, Vudeo, Vidoza). */
private suspend fun ExtractorApi.extractFromPage(
    label: String,
    url: String,
    referer: String?,
    parse: (String) -> List<String>,
    callback: (ExtractorLink) -> Unit,
) = guarded(label) {
    val page = fetchPage(label, url, referer) ?: return@guarded
    val links = parse(page.text)
    PluginLog.i("$label : motif trouvé : ${if (links.isEmpty()) "non" else "oui (${links.size})"}")
    val pageHost = hostOf(page.url) ?: hostOf(url) ?: return@guarded
    emit(links.map { it to 0 }, pageHost, callback)
}

open class Voe : ExtractorApi() {
    override val name: String = "Voe"
    override val mainUrl: String = "https://voe.sx"
    override val requiresReferer: Boolean = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = guarded("Voe") {
        var page = fetchPage("Voe", url, referer) ?: return@guarded
        var links = ExtractorParsing.parseVoe(page.text)
        if (links.isEmpty()) {
            // Voe renvoie parfois une page qui redirige en JavaScript vers le vrai lecteur.
            val next = ExtractorParsing.findJsRedirect(page.text)?.let { resolveUrl(page.url, it) }
            if (next != null && next != page.url) {
                page = fetchPage("Voe (redirection)", next, page.url) ?: return@guarded
                links = ExtractorParsing.parseVoe(page.text)
            }
        }
        PluginLog.i("Voe : motif trouvé : ${if (links.isEmpty()) "non" else "oui (${links.size})"}")
        val pageHost = hostOf(page.url) ?: hostOf(url) ?: return@guarded
        emit(links.map { it to 0 }, pageHost, callback)
    }
}

open class Uqload : ExtractorApi() {
    override val name: String = "Uqload"
    override val mainUrl: String = "https://www.uqload.com"
    override val requiresReferer: Boolean = true
    override val supportedHosts: Set<String> =
        setOf("uqload.com", "uqload.co", "uqload.io", "uqload.ws", "uqload.to")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = extractFromPage("Uqload", url, referer, ExtractorParsing::parseSourcesPage, callback)
}

open class Vudeo : ExtractorApi() {
    override val name: String = "Vudeo"
    override val mainUrl: String = "https://vudeo.io"
    override val requiresReferer: Boolean = false
    override val supportedHosts: Set<String> = setOf("vudeo.io", "vudeo.net", "vudeo.ws")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = extractFromPage("Vudeo", url, referer, ExtractorParsing::parseSourcesPage, callback)
}

open class Vidoza : ExtractorApi() {
    override val name: String = "Vidoza"
    override val mainUrl: String = "https://vidoza.net"
    override val requiresReferer: Boolean = false
    override val supportedHosts: Set<String> = setOf("vidoza.net", "vidoza.org", "vidoza.co")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = extractFromPage("Vidoza", url, referer, ExtractorParsing::parseVidoza, callback)
}

open class XStreamCdn : ExtractorApi() {
    override val name: String = "XStreamCdn"
    override val mainUrl: String = "https://embedsito.com"
    override val requiresReferer: Boolean = false
    override val supportedHosts: Set<String> =
        setOf("embedsito.com", "fembed.com", "fembed.net", "fembed.one")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = guarded("Fembed") {
        val host = hostOf(url) ?: return@guarded
        val id = url.substringBefore('?').trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return@guarded
        val page = ExtractorHttp.fetcher.post(
            "https://$host/api/source/$id",
            referer ?: "https://$host/",
            mapOf("r" to "", "d" to host),
        )
        PluginLog.i("Fembed : API HTTP ${page?.code ?: "aucune réponse"}")
        val links = page?.let { ExtractorParsing.parseFembedJson(it.text) }.orEmpty()
        PluginLog.i("Fembed : motif trouvé : ${if (links.isEmpty()) "non" else "oui (${links.size})"}")
        emit(links, host, callback)
    }
}

open class DoodLaExtractor : ExtractorApi() {
    override val name: String = "DoodStream"
    override val mainUrl: String = "https://dood.la"
    override val requiresReferer: Boolean = false
    override val supportedHosts: Set<String> = setOf(
        "dood.la", "dood.so", "doodstream.com", "dood.ws", "dood.pm", "dood.re",
        "dood.to", "dood.wf", "dood.cx", "dood.watch", "d000d.net", "do7go.com", "d0o0d.com",
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) = guarded("Dood") {
        val embedUrl = url.replace("/d/", "/e/")
        val page = fetchPage("Dood", embedUrl, referer) ?: return@guarded
        val host = hostOf(page.url) ?: hostOf(embedUrl) ?: return@guarded
        val parsed = ExtractorParsing.parseDood(page.text)
        if (parsed == null) {
            PluginLog.i("Dood : page inattendue (protection ou lecteur supprimé ?)")
            return@guarded
        }
        val (passPath, token) = parsed
        val base = ExtractorHttp.fetcher.get("https://$host$passPath", page.url)
        PluginLog.i("Dood : pass_md5 HTTP ${base?.code ?: "aucune réponse"}")
        val finalUrl = base?.let {
            ExtractorParsing.buildDoodUrl(
                it.text, token, ExtractorParsing.randomAlnum(10), System.currentTimeMillis(),
            )
        }
        if (finalUrl == null) {
            PluginLog.i("Dood : réponse pass_md5 inattendue")
            return@guarded
        }
        PluginLog.i("Dood : motif trouvé : oui")
        emit(listOf(finalUrl to 0), host, callback)
    }
}
