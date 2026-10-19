package com.missa.tv360.data.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.missa.tv360.BuildConfig
import com.missa.tv360.data.remote.PagesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

/** Charge et valide le paquet déclaratif livré dans engines/films/manifest.json. */
object FilmEngineRepository {
    private const val MANIFEST_PATH = "engines/films/manifest.json"
    private const val PREFS_NAME = "film_engine_package"
    private const val PREF_MANIFEST = "last_valid_manifest"

    private val gson = Gson()
    private val manifestType: Type = object : TypeToken<FilmEngineManifest>() {}.type

    @Volatile
    private var currentConfig: FilmEngineConfig? = null

    /**
     * Vérifie la dernière version à chaque ouverture de l'accueil. Si le réseau
     * échoue, conserve le dernier paquet valide, puis le manifeste livré dans l'APK.
     */
    suspend fun refresh(context: Context): FilmEngineConfig {
        val appContext = context.applicationContext
        val config = withContext(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val remote = PagesClient.fetchJson<FilmEngineManifest>(
                BuildConfig.BFF_BASE_URL,
                MANIFEST_PATH,
                manifestType,
                4000,
            )
            val remoteConfig = remote?.toRuntimeConfig()
            if (remoteConfig != null) {
                prefs.edit().putString(PREF_MANIFEST, gson.toJson(remote)).apply()
                return@withContext remoteConfig
            }

            val cachedConfig = prefs.getString(PREF_MANIFEST, null)
                ?.let(::parseManifest)
            cachedConfig ?: readBundledManifest(appContext) ?: bundledFilmEngineConfig()
        }
        currentConfig = config
        return config
    }

    /** Utilisé par les fiches détails après le chargement initial du moteur. */
    suspend fun currentOrLoad(context: Context): FilmEngineConfig =
        currentConfig ?: refresh(context)

    private fun parseManifest(json: String): FilmEngineConfig? = try {
        gson.fromJson<FilmEngineManifest>(json, manifestType)?.toRuntimeConfig()
    } catch (_: Exception) {
        null
    }

    private fun readBundledManifest(context: Context): FilmEngineConfig? = try {
        val json = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
        parseManifest(json)
    } catch (_: Exception) {
        null
    }
}
