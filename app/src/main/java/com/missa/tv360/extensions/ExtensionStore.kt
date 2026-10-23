package com.missa.tv360.extensions

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persistance de l'état des extensions (SharedPreferences + JSON).
 *
 * Volontairement simple et synchrone : les écritures sont petites (quelques
 * kilo-octets), et l'état doit être relu très tôt au démarrage, avant que le
 * premier écran ne demande des contenus.
 */
internal class ExtensionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── Dépôts ────────────────────────────────────────────────────────────

    fun repositories(): List<RepositoryEntry> =
        read(KEY_REPOSITORIES, object : TypeToken<List<RepositoryEntry>>() {}.type)

    fun saveRepositories(list: List<RepositoryEntry>) = write(KEY_REPOSITORIES, list)

    // ── Extensions installées ─────────────────────────────────────────────

    fun installed(): List<InstalledExtension> =
        read(KEY_INSTALLED, object : TypeToken<List<InstalledExtension>>() {}.type)

    fun saveInstalled(list: List<InstalledExtension>) = write(KEY_INSTALLED, list)

    // ── Drapeaux ──────────────────────────────────────────────────────────

    var bootstrapDone: Boolean
        get() = prefs.getBoolean(KEY_BOOTSTRAP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_BOOTSTRAP_DONE, value).apply()

    /** Mise à jour automatique au démarrage (activée par défaut). */
    var autoUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    var lastRefreshAt: Long
        get() = prefs.getLong(KEY_LAST_REFRESH, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REFRESH, value).apply()

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun <T> read(key: String, type: java.lang.reflect.Type): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            gson.fromJson<List<T>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            // Préférence corrompue : on repart d'une liste vide plutôt que de
            // planter au démarrage.
            emptyList()
        }
    }

    private fun write(key: String, value: Any) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }

    private companion object {
        const val PREFS_NAME = "missa_extensions"
        const val KEY_REPOSITORIES = "repositories"
        const val KEY_INSTALLED = "installed"
        const val KEY_BOOTSTRAP_DONE = "bootstrap_done"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_LAST_REFRESH = "last_refresh"
    }
}
