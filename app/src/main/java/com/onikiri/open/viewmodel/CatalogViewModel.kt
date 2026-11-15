package com.onikiri.open.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.data.ModuleDetails
import com.onikiri.open.data.ModuleDetailsRepository
import com.onikiri.open.data.ModuleRepository
import com.onikiri.open.data.RepoUrl
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.SettingsStore
import com.onikiri.open.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Error(val message: String) : CatalogState

    @Immutable
    data class Ready(
        val modules: List<RootModule>,
        val offline: Boolean,
        val failedRepos: Int
    ) : CatalogState
}

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Loaded(val details: ModuleDetails) : DetailsState
    data class Failed(val message: String) : DetailsState
}

class CatalogViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val store = SettingsStore.get(ctx)
    private val repository = ModuleRepository(ctx)
    private val detailsRepository = ModuleDetailsRepository(ctx)

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()

    private val _details = MutableStateFlow<Map<String, DetailsState>>(emptyMap())
    val details: StateFlow<Map<String, DetailsState>> = _details.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Emits once on start and again whenever the list of custom repositories changes.
        viewModelScope.launch {
            store.settings
                .map { it.customRepos }
                .distinctUntilChanged()
                .collect { refresh(force = false) }
        }
    }

    fun refresh(force: Boolean = true) {
        loadJob?.cancel()
        val job = viewModelScope.launch {
            val self = coroutineContext[Job]
            _refreshing.value = true
            if (_state.value !is CatalogState.Ready) _state.value = CatalogState.Loading
            try {
                val urls = listOf(RepoUrl.BUILT_IN) + store.settings.value.customRepos
                val result = repository.load(urls, force)
                _state.value = CatalogState.Ready(result.modules, result.offline, result.failedRepos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value !is CatalogState.Ready) {
                    _state.value = CatalogState.Error(e.userMessage(ctx))
                }
            } finally {
                if (loadJob === self) _refreshing.value = false
            }
        }
        loadJob = job
    }

    fun toggle(module: RootModule) {
        val willExpand = module.id !in _expanded.value
        _expanded.update { if (willExpand) it + module.id else it - module.id }
        if (willExpand) loadDetails(module)
    }

    fun loadDetails(module: RootModule, force: Boolean = false) {
        val current = _details.value[module.id]
        if (!force && (current is DetailsState.Loaded || current is DetailsState.Loading)) return
        _details.update { it + (module.id to DetailsState.Loading) }
        viewModelScope.launch {
            try {
                val loaded = detailsRepository.load(module, force)
                _details.update { it + (module.id to DetailsState.Loaded(loaded)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _details.update { it + (module.id to DetailsState.Failed(e.userMessage(ctx))) }
            }
        }
    }
}
