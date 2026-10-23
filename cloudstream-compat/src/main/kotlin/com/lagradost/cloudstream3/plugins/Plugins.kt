package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.missa.tv360.cloudstream.compat.CloudstreamCompatRegistry

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin

abstract class BasePlugin {
    var filename: String? = null

    open fun load() {}
    open fun beforeUnload() {}

    fun registerMainAPI(element: MainAPI) {
        element.sourcePlugin = filename
        CloudstreamCompatRegistry.register(filename, element)
    }

    fun registerExtractorAPI(element: ExtractorApi) {
        element.sourcePlugin = filename
        CloudstreamCompatRegistry.register(filename, element)
    }
}

abstract class Plugin : BasePlugin() {
    open fun load(context: Context) {
        load()
    }

    var resources: Resources? = null
    var openSettings: ((context: Context) -> Unit)? = null
}

data class PluginData(
    val internalName: String,
    val filePath: String,
)

/** Les commandes de rechargement des réglages ne simulent aucune installation. */
object PluginManager {
    fun getPluginsOnline(): Array<PluginData> = emptyArray()

    fun hotReloadAllLocalPlugins(activity: FragmentActivity) = Unit

    suspend fun loadSinglePlugin(context: Context, path: String): Boolean = false

    fun unloadPlugin(path: String) {
        CloudstreamCompatRegistry.unload(path)
    }
}
