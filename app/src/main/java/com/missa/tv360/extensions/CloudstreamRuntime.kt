package com.missa.tv360.extensions

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.extractorApis

/** Accès au registre réel de la bibliothèque Cloudstream, sans double registre parallèle. */
internal object CloudstreamRuntime {

    fun providers(): List<MainAPI> = APIHolder.allProviders.withLock {
        APIHolder.allProviders.toList()
    }

    fun provider(name: String): MainAPI? = providers().firstOrNull { it.name == name }

    /** Reprend le nettoyage de PluginManager.unloadPlugin de Cloudstream. */
    fun unload(plugin: BasePlugin) {
        val filename = plugin.filename
        APIHolder.apis.filter { it.sourcePlugin == filename }.forEach {
            APIHolder.removePluginMapping(it)
        }
        APIHolder.allProviders.withLock {
            APIHolder.allProviders.removeAll { it.sourcePlugin == filename }
        }
        extractorApis.withLock {
            extractorApis.removeAll { it.sourcePlugin == filename }
        }
        VideoClickActionHolder.allVideoClickActions.withLock {
            VideoClickActionHolder.allVideoClickActions.removeAll { it.sourcePlugin == filename }
        }
    }
}
