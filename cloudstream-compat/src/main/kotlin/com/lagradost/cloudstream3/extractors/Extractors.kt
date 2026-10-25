package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi

/**
 * Identités d'extracteurs requises à l'enregistrement. Leur extraction réelle
 * est volontairement laissée à P4 ; la classe de base ne renvoie aucun lien.
 */
open class Voe : ExtractorApi() {
    override val name: String = "Voe"
    override val mainUrl: String = "https://voe.sx"
    override val requiresReferer: Boolean = true
}

open class Uqload : ExtractorApi() {
    override val name: String = "Uqload"
    override val mainUrl: String = "https://www.uqload.com"
    override val requiresReferer: Boolean = true
    override val supportedHosts: Set<String> = setOf("uqload.com", "uqload.co")
}

open class XStreamCdn : ExtractorApi() {
    override val name: String = "XStreamCdn"
    override val mainUrl: String = "https://embedsito.com"
    override val requiresReferer: Boolean = false
}

open class DoodLaExtractor : ExtractorApi() {
    override val name: String = "DoodStream"
    override val mainUrl: String = "https://dood.la"
    override val requiresReferer: Boolean = false
    override val supportedHosts: Set<String> = setOf(
        "dood.la", "dood.so", "doodstream.com", "dood.ws", "dood.pm", "dood.re"
    )
}
