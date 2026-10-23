package com.missa.tv360.data.json

import android.content.Context
import com.google.gson.Gson
import com.missa.tv360.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Charge tous les JSON déclarés par le dépôt et expose un catalogue unique. */
object JsonSourceRepository {
    private const val INDEX_PATH = "index.json"
    private const val PREFS_NAME = "json_sources"
    private const val KEY_INDEX = "last_valid_index"
    private const val KEY_AGGREGATE = "last_merged_catalog"
    private const val KEY_SOURCE_PREFIX = "last_valid_source:"
    private val gson = Gson()

    data class LoadResult(
        val catalog: MergedJsonCatalog,
        val remoteFailed: Boolean,
        val networkFailed: Boolean,
        val usedCache: Boolean,
    )

    suspend fun load(context: Context): LoadResult = withContext(Dispatchers.IO) {
        loadBlocking(context.applicationContext)
    }

    fun cachedCatalog(context: Context): MergedJsonCatalog? {
        return try {
            val raw = preferences(context).getString(KEY_AGGREGATE, null) ?: return null
            gson.fromJson(raw, MergedJsonCatalog::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadBlocking(context: Context): LoadResult {
        val prefs = preferences(context)
        val previousCatalog = cachedCatalog(context)
        var remoteFailed = false
        var networkFailed = false
        var usedCache = false

        val remoteIndex = JsonHttpClient.fetch(BuildConfig.JSON_SOURCE_BASE_URL, INDEX_PATH)
        val remoteText = when (val result = remoteIndex) {
            is JsonHttpClient.FetchResult.Success -> result.text
            is JsonHttpClient.FetchResult.HttpError -> {
                remoteFailed = true
                null
            }
            is JsonHttpClient.FetchResult.NetworkError -> {
                remoteFailed = true
                networkFailed = true
                null
            }
        }
        val cachedIndex = prefs.getString(KEY_INDEX, null)
        val indexText = remoteText ?: cachedIndex
        if (remoteText == null && cachedIndex != null) usedCache = true
        if (indexText == null) {
            return LoadResult(
                previousCatalog ?: MergedJsonCatalog(),
                remoteFailed = true,
                networkFailed = networkFailed,
                usedCache = true,
            )
        }

        val sources = try {
            JsonCatalog.parseIndex(indexText).also {
                if (remoteText != null) prefs.edit().putString(KEY_INDEX, remoteText).apply()
            }
        } catch (_: Exception) {
            remoteFailed = true
            val fallback = cachedIndex?.takeIf { it != indexText } ?: return LoadResult(
                previousCatalog ?: MergedJsonCatalog(),
                remoteFailed = true,
                networkFailed = networkFailed,
                usedCache = true,
            )
            usedCache = true
            try {
                JsonCatalog.parseIndex(fallback)
            } catch (_: Exception) {
                return LoadResult(
                    previousCatalog ?: MergedJsonCatalog(),
                    remoteFailed = true,
                    networkFailed = networkFailed,
                    usedCache = true,
                )
            }
        }

        val documents = mutableListOf<Pair<JsonSource, JsonCatalogDocument>>()
        sources.forEach { source ->
            val cachedSource = prefs.getString(KEY_SOURCE_PREFIX + source.id, null)
            val remoteSource = JsonHttpClient.fetch(BuildConfig.JSON_SOURCE_BASE_URL, source.path)
            when (val result = remoteSource) {
                is JsonHttpClient.FetchResult.Success -> {
                    try {
                        val document = JsonCatalog.parseDocument(result.text)
                        documents += source to document
                        prefs.edit().putString(KEY_SOURCE_PREFIX + source.id, result.text).apply()
                    } catch (_: Exception) {
                        remoteFailed = true
                        val cachedDocument = cachedSource?.let { raw ->
                            runCatching { JsonCatalog.parseDocument(raw) }.getOrNull()
                        }
                        if (cachedDocument != null) {
                            documents += source to cachedDocument
                            usedCache = true
                        }
                    }
                }
                is JsonHttpClient.FetchResult.HttpError -> {
                    remoteFailed = true
                    val cachedDocument = cachedSource?.let { raw ->
                        runCatching { JsonCatalog.parseDocument(raw) }.getOrNull()
                    }
                    if (cachedDocument != null) {
                        documents += source to cachedDocument
                        usedCache = true
                    }
                }
                is JsonHttpClient.FetchResult.NetworkError -> {
                    remoteFailed = true
                    networkFailed = true
                    val cachedDocument = cachedSource?.let { raw ->
                        runCatching { JsonCatalog.parseDocument(raw) }.getOrNull()
                    }
                    if (cachedDocument != null) {
                        documents += source to cachedDocument
                        usedCache = true
                    }
                }
            }
        }

        val merged = JsonCatalog.merge(documents)
        if (!remoteFailed || previousCatalog == null) {
            prefs.edit().putString(KEY_AGGREGATE, gson.toJson(merged)).apply()
        }
        return if (remoteFailed && previousCatalog != null) {
            LoadResult(previousCatalog, remoteFailed, networkFailed, usedCache = true)
        } else {
            LoadResult(merged, remoteFailed, networkFailed, usedCache)
        }
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
