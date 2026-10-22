package com.missa.tv360.api

/**
 * Registre partagé entre l'application et les extensions.
 *
 * Point clé du chargement dynamique : le classloader parent du plugin est
 * celui de l'application, donc la classe `PluginRegistry` chargée par le
 * plugin est EXACTEMENT la même instance que celle utilisée par l'app.
 * C'est ce qui permet à une extension d'appeler [MissaPlugin.registerProvider]
 * et à l'application de retrouver immédiatement le provider.
 *
 * Toutes les méthodes sont synchronisées : les extensions peuvent charger en
 * parallèle, et l'interface lit le registre depuis un autre thread.
 */
object PluginRegistry {

    data class Registered(
        val pluginFile: String?,
        val provider: Provider,
    )

    private val entries = LinkedHashMap<String, Registered>()

    private fun key(pluginFile: String?, providerId: String): String =
        "${pluginFile ?: "local"}::$providerId"

    @Synchronized
    fun register(plugin: MissaPlugin, provider: Provider) {
        val k = key(plugin.filename, provider.id)
        entries[k] = Registered(plugin.filename, provider)
        PluginLog.i("PluginRegistry : provider '${provider.id}' enregistré ($k)")
    }

    @Synchronized
    fun providers(): List<Provider> = entries.values.map { it.provider }

    /** Retire tous les providers d'un fichier de plugin (désinstallation / rechargement). */
    @Synchronized
    fun clear(pluginFile: String) {
        val before = entries.size
        entries.entries.removeAll { it.value.pluginFile == pluginFile }
        val removed = before - entries.size
        if (removed > 0) {
            PluginLog.i("PluginRegistry : $removed provider(s) retiré(s) pour $pluginFile")
        }
    }

    @Synchronized
    fun clearAll() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
