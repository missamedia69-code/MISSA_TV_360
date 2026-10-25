package com.missa.tv360.cloudstream.compat

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Uqload
import com.lagradost.cloudstream3.extractors.Vidoza
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.Vudeo
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.missa.tv360.api.Provider

/** Registre du prototype, nettoyé avec le même chemin de fichier que le plugin. */
object CloudstreamCompatRegistry {
    private data class Entry(
        val filename: String,
        val api: MainAPI,
        val provider: CloudstreamProvider,
    )

    private val entries = LinkedHashMap<String, Entry>()
    private val extractors = mutableListOf<ExtractorApi>()

    /** Extracteurs fournis par l'application elle-même (comme la liste intégrée du Cloudstream officiel). */
    private val builtIn: List<ExtractorApi> by lazy {
        listOf(Uqload(), Vudeo(), Vidoza(), DoodLaExtractor(), Voe(), XStreamCdn())
    }

    fun builtInExtractors(): List<ExtractorApi> = builtIn

    @Synchronized
    fun register(filename: String?, api: MainAPI) {
        val file = filename ?: "local"
        val provider = CloudstreamProvider(api)
        entries["$file::${provider.id}"] = Entry(file, api, provider)
    }

    @Synchronized
    fun register(filename: String?, extractor: ExtractorApi) {
        extractors.removeAll { it === extractor }
        extractors += extractor
    }

    @Synchronized
    fun providers(): List<Provider> = entries.values.map { it.provider }

    @Synchronized
    fun providerFor(id: String): MainAPI? = entries.values.firstOrNull {
        it.provider.id == id
    }?.api

    @Synchronized
    fun extractors(): List<ExtractorApi> = extractors.toList()

    @Synchronized
    fun unload(filename: String?) {
        val file = filename ?: "local"
        entries.entries.removeAll { it.value.filename == file }
        extractors.removeAll { (it.sourcePlugin ?: "local") == file }
    }
}
