package com.missa.tv360.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** État catalogue conservé pendant rotations et changements de fragments. */
class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    data class UiState(
        val rows: List<VideoRow> = emptyList(),
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val source: CatalogSource? = null,
        val remoteFailed: Boolean = false,
        val networkFailed: Boolean = false,
    )

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(UiState())
    val state: kotlinx.coroutines.flow.StateFlow<UiState> = _state
    private var started = false
    private var refreshJob: Job? = null

    fun initialize() {
        if (started) return
        started = true
        refreshJob = viewModelScope.launch {
            val initial = CatalogRepository.initialCatalog(getApplication())
            publish(initial, initialLoading = false, refreshing = true)
            refreshRemote()
        }
    }

    fun refresh() {
        if (!started) {
            initialize()
            return
        }
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { refreshRemote() }
    }

    private suspend fun refreshRemote() {
        val app = getApplication<Application>()
        _state.value = _state.value.copy(refreshing = true)
        val current = _state.value
        val result = if (!NetworkStatus.isOnline(app)) {
            CatalogLoadResult(
                rows = current.rows,
                source = current.source ?: CatalogSource.BUNDLED,
                remoteFailed = true,
                networkFailed = true,
            )
        } else {
            withTimeoutOrNull(8_000L) { CatalogRepository.loadCatalogWithStatus(app) }
                ?: CatalogLoadResult(
                    rows = current.rows,
                    source = current.source ?: CatalogSource.BUNDLED,
                    remoteFailed = true,
                    networkFailed = true,
                )
        }
        publish(
            result.copy(rows = result.rows.ifEmpty { current.rows }),
            initialLoading = false,
            refreshing = false,
        )
    }

    private fun publish(
        result: CatalogLoadResult,
        initialLoading: Boolean,
        refreshing: Boolean,
    ) {
        CatalogStore.replaceCatalog(getApplication(), result.rows)
        _state.value = UiState(
            rows = result.rows,
            initialLoading = initialLoading,
            refreshing = refreshing,
            source = result.source,
            remoteFailed = result.remoteFailed,
            networkFailed = result.networkFailed,
        )
    }
}
