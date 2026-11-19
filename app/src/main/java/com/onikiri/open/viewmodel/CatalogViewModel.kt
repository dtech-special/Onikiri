package com.onikiri.open.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.data.InstallState
import com.onikiri.open.data.InstalledModule
import com.onikiri.open.data.ModuleDetails
import com.onikiri.open.data.ModuleDetailsRepository
import com.onikiri.open.data.ModuleRepository
import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.data.ReleaseUpdateChecker
import com.onikiri.open.data.RepoUrl
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.SettingsStore
import com.onikiri.open.data.installStateOf
import com.onikiri.open.data.matchInstalled
import com.onikiri.open.data.queryTokens
import com.onikiri.open.data.repoNameFromUrl
import com.onikiri.open.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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

@Immutable
sealed interface DetailsState {
    data object Loading : DetailsState

    @Immutable
    data class Loaded(val details: ModuleDetails) : DetailsState

    @Immutable
    data class Failed(val message: String) : DetailsState
}

/** Fixed filter set of the catalog's top bar. */
enum class CatalogFilter { ALL, INSTALLED, VERIFIED, REVIEW, HAZARD }

/** Everything a catalog card needs, already resolved off the main thread. */
@Immutable
data class ModuleUiItem(
    val id: String,
    val title: String,
    val description: String,
    val githubRepo: String,
    val owner: String,
    val status: ModuleStatus,
    val installState: InstallState,
    /** `version` from the installed module.prop, null when the module is not installed. */
    val installedVersion: String?,
    val installedVersionCode: Long,
    val expanded: Boolean,
    val details: DetailsState?,
    /** Lower-case text the search matches against (title, description, owner, id, authors). */
    val searchKey: String
)

@Immutable
data class InstalledModuleUiItem(
    val dir: String,
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val disabled: Boolean,
    val removing: Boolean,
    /** Catalog id of the matching entry, null for modules that are in no repository. */
    val catalogId: String?,
    val canUpdate: Boolean
)

class CatalogViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val store = SettingsStore.get(ctx)
    private val repository = ModuleRepository(ctx)
    private val detailsRepository = ModuleDetailsRepository(ctx)
    private val updateChecker = ReleaseUpdateChecker(ctx)
    private val gate = Semaphore(3)

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    private val _details = MutableStateFlow<Map<String, DetailsState>>(emptyMap())

    /** Readable name of every configured repository, keyed by URL. */
    private val _repoNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val repoNames: StateFlow<Map<String, String>> = _repoNames.asStateFlow()

    private val _filter = MutableStateFlow(CatalogFilter.ALL)
    val filter: StateFlow<CatalogFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")

    private val installedList = MutableStateFlow<List<InstalledModule>>(emptyList())

    /** Latest GitHub release tag per catalog id; filled only while "check module updates" is on. */
    private val _latestTags = MutableStateFlow<Map<String, String>>(emptyMap())
    private val recheck = MutableStateFlow(0)
    private val checkedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var index: Map<String, RootModule> = emptyMap()
    private var bound = false
    private var loadJob: Job? = null

    /** Catalog id -> installed module (data.onikiri first, normalized id/name as a fallback). */
    private val matched: StateFlow<Map<String, InstalledModule>> =
        combine(_state, installedList) { catalog, installed ->
            val modules = (catalog as? CatalogState.Ready)?.modules.orEmpty()
            matchInstalled(modules, installed)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** All catalog cards; null until the catalog is loaded and mapped. */
    private val items: StateFlow<List<ModuleUiItem>?> =
        combine(_state, matched, _details, _latestTags, _expanded) { catalog, match, details, tags, expanded ->
            val ready = catalog as? CatalogState.Ready ?: return@combine null
            ready.modules.map { module ->
                val installed = match[module.id]
                val detail = details[module.id]
                val loaded = (detail as? DetailsState.Loaded)?.details
                ModuleUiItem(
                    id = module.id,
                    title = module.title,
                    description = module.description,
                    githubRepo = module.githubRepo,
                    owner = module.owner,
                    status = module.status,
                    installState = installStateOf(installed, loaded, tags[module.id]),
                    installedVersion = installed?.version,
                    installedVersionCode = installed?.versionCode ?: 0L,
                    expanded = module.id in expanded,
                    details = detail,
                    searchKey = buildString {
                        append(module.title).append(' ').append(module.description).append(' ')
                        append(module.owner).append(' ').append(module.id)
                        loaded?.author?.let { append(' ').append(it) }
                        installed?.author?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
                    }.lowercase()
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cards of the catalog tab after the fixed filter was applied. */
    val visibleItems: StateFlow<List<ModuleUiItem>?> =
        combine(items, _filter) { list, filter ->
            list?.filter {
                when (filter) {
                    CatalogFilter.ALL -> true
                    CatalogFilter.INSTALLED -> it.installedVersion != null
                    CatalogFilter.VERIFIED -> it.status == ModuleStatus.VERIFIED
                    CatalogFilter.REVIEW -> it.status == ModuleStatus.REVIEW
                    CatalogFilter.HAZARD -> it.status == ModuleStatus.HAZARD
                }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cards of the search tab: every word of the query must occur in the search key. */
    val searchResults: StateFlow<List<ModuleUiItem>?> =
        combine(items, _query) { list, query ->
            val tokens = queryTokens(query)
            if (tokens.isEmpty()) list else list?.filter { item -> tokens.all { item.searchKey.contains(it) } }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cards of the installed tab, including modules that are in no repository. */
    val installedItems: StateFlow<List<InstalledModuleUiItem>> =
        combine(installedList, matched, _details, _latestTags) { installed, match, details, tags ->
            val catalogIdByDir = HashMap<String, String>()
            for ((catalogId, module) in match) catalogIdByDir.putIfAbsent(module.dir, catalogId)
            installed.map { module ->
                val catalogId = catalogIdByDir[module.dir]
                val loaded = catalogId?.let { (details[it] as? DetailsState.Loaded)?.details }
                InstalledModuleUiItem(
                    dir = module.dir,
                    id = module.id,
                    name = module.name,
                    version = module.version,
                    versionCode = module.versionCode,
                    author = module.author,
                    description = module.description,
                    disabled = module.disabled,
                    removing = module.removing,
                    catalogId = catalogId,
                    canUpdate = catalogId != null &&
                        installStateOf(module, loaded, tags[catalogId]) == InstallState.UPDATE
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Emits once on start and again whenever repositories are added, removed or toggled.
        viewModelScope.launch {
            store.settings
                .map { it.customRepos to it.disabledRepos }
                .distinctUntilChanged()
                .collect { refresh(force = false) }
        }
        // Opt-in update check: only for installed modules with a known github_repo, in the background.
        viewModelScope.launch {
            combine(
                store.settings.map { it.checkModuleUpdates }.distinctUntilChanged(),
                _state,
                matched,
                recheck
            ) { enabled, catalog, match, _ -> Triple(enabled, catalog, match) }
                .collectLatest { (enabled, catalog, match) ->
                    if (!enabled) {
                        checkedAt.clear()
                        _latestTags.value = emptyMap()
                    } else if (catalog is CatalogState.Ready) {
                        runUpdateCheck(catalog.modules, match)
                    }
                }
        }
    }

    /** Starts listening to the installed modules once; called by the UI root. */
    fun bindInstalled(source: StateFlow<List<InstalledModule>>) {
        if (bound) return
        bound = true
        viewModelScope.launch { source.collect { installedList.value = it } }
    }

    fun nameOf(url: String): String = _repoNames.value[url] ?: repoNameFromUrl(url)

    fun moduleById(id: String): RootModule? = index[id]

    fun setFilter(filter: CatalogFilter) {
        _filter.value = filter
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun refresh(force: Boolean = true) {
        if (force) {
            checkedAt.clear()
            recheck.update { it + 1 }
        }
        loadJob?.cancel()
        val job = viewModelScope.launch {
            val self = coroutineContext[Job]
            _refreshing.value = true
            if (_state.value !is CatalogState.Ready) _state.value = CatalogState.Loading
            try {
                val settings = store.settings.value
                val urls = listOf(RepoUrl.BUILT_IN) + settings.customRepos
                val result = repository.load(urls, settings.disabledRepos.toSet(), force)
                _repoNames.value = result.names
                index = result.modules.associateBy { it.id }
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

    private suspend fun runUpdateCheck(modules: List<RootModule>, match: Map<String, InstalledModule>) {
        val now = System.currentTimeMillis()
        val targets = modules.filter {
            it.id in match && it.githubRepo.isNotBlank() && now - (checkedAt[it.id] ?: 0L) > RECHECK_MS
        }
        if (targets.isEmpty()) return
        val updateGate = Semaphore(permits = 4)
        val found = withContext(Dispatchers.IO) {
            coroutineScope {
                targets.map { module ->
                    async { updateGate.withPermit { module.id to updateChecker.latestTag(module.githubRepo) } }
                }.awaitAll()
            }
        }
        val checkedNow = System.currentTimeMillis()
        found.forEach { (id, _) -> checkedAt[id] = checkedNow }
        val tags = found.mapNotNull { (id, tag) -> tag?.let { id to it } }
        if (tags.isNotEmpty()) _latestTags.update { it + tags }
    }

    fun toggle(id: String) {
        val willExpand = id !in _expanded.value
        _expanded.update { if (willExpand) it + id else it - id }
        if (willExpand) index[id]?.let { loadDetails(it) }
    }

    fun retryDetails(id: String) {
        index[id]?.let { loadDetails(it, force = true) }
    }

    fun loadDetails(module: RootModule, force: Boolean = false) {
        val current = _details.value[module.id]
        if (!force && (current is DetailsState.Loaded || current is DetailsState.Loading)) return
        _details.update { it + (module.id to DetailsState.Loading) }
        viewModelScope.launch {
            try {
                val loaded = gate.withPermit { detailsRepository.load(module, force) }
                _details.update { it + (module.id to DetailsState.Loaded(loaded)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _details.update { it + (module.id to DetailsState.Failed(e.userMessage(ctx))) }
            }
        }
    }

    private companion object {
        const val RECHECK_MS = 10L * 60 * 1000
    }
}
