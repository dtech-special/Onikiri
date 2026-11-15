package com.onikiri.open.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikiri.open.data.AppSettings
import com.onikiri.open.data.ManagerChoice
import com.onikiri.open.data.RepoUrl
import com.onikiri.open.data.SettingsStore
import com.onikiri.open.data.ThemeMode
import com.onikiri.open.install.CacheManager
import com.onikiri.open.root.SystemSummary
import com.onikiri.open.root.readSystemSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AddRepoResult { ADDED, INVALID, DUPLICATE }

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val store = SettingsStore.get(ctx)

    val settings: StateFlow<AppSettings> = store.settings
    val system: SystemSummary = readSystemSummary()
    val appVersion: String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = withContext(Dispatchers.IO) { CacheManager.sizeBytes(ctx) }
        }
    }

    /** Deletes all downloaded archives and returns the number of freed bytes. */
    suspend fun clearCache(): Long {
        val freed = withContext(Dispatchers.IO) { CacheManager.clear(ctx) }
        _cacheSize.value = withContext(Dispatchers.IO) { CacheManager.sizeBytes(ctx) }
        return freed
    }

    fun setThemeMode(mode: ThemeMode) = store.update { it.copy(themeMode = mode) }
    fun setAmoled(enabled: Boolean) = store.update { it.copy(amoled = enabled) }
    fun setDynamicColor(enabled: Boolean) = store.update { it.copy(dynamicColor = enabled) }
    fun setPreset(id: String) = store.update { it.copy(presetId = id) }
    fun setAutoClean(enabled: Boolean) = store.update { it.copy(autoCleanTemp = enabled) }
    fun setConfirmSoftReboot(enabled: Boolean) = store.update { it.copy(confirmSoftReboot = enabled) }
    fun setManagerChoice(choice: ManagerChoice) = store.update { it.copy(managerChoice = choice) }
    fun completeSetup() = store.update { it.copy(setupDone = true) }

    fun addRepo(input: String): AddRepoResult {
        val url = RepoUrl.normalize(input) ?: return AddRepoResult.INVALID
        val current = store.settings.value
        if (url == RepoUrl.BUILT_IN || url in current.customRepos) return AddRepoResult.DUPLICATE
        store.update { it.copy(customRepos = it.customRepos + url) }
        return AddRepoResult.ADDED
    }

    fun removeRepo(url: String) = store.update { it.copy(customRepos = it.customRepos - url) }
}
