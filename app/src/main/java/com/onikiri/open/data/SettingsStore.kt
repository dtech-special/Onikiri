package com.onikiri.open.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Single source of truth for app settings, backed by SharedPreferences and exposed as a StateFlow. */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings: MutableStateFlow<AppSettings>
    val settings: StateFlow<AppSettings>

    init {
        migrateLegacy(context.applicationContext)
        _settings = MutableStateFlow(load())
        settings = _settings.asStateFlow()
    }

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        save(updated)
        _settings.value = updated
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            setupDone = prefs.getBoolean(KEY_SETUP_DONE, defaults.setupDone),
            managerChoice = enumOf(prefs.getString(KEY_MANAGER, null), defaults.managerChoice),
            themeMode = enumOf(prefs.getString(KEY_THEME, null), defaults.themeMode),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, defaults.dynamicColor),
            amoled = prefs.getBoolean(KEY_AMOLED, defaults.amoled),
            presetId = prefs.getString(KEY_PRESET, defaults.presetId) ?: defaults.presetId,
            autoCleanTemp = prefs.getBoolean(KEY_AUTO_CLEAN, defaults.autoCleanTemp),
            confirmSoftReboot = prefs.getBoolean(KEY_CONFIRM_SOFT, defaults.confirmSoftReboot),
            customRepos = readRepos()
        )
    }

    private fun save(s: AppSettings) {
        val array = JSONArray()
        s.customRepos.forEach { array.put(it) }
        prefs.edit()
            .putBoolean(KEY_SETUP_DONE, s.setupDone)
            .putString(KEY_MANAGER, s.managerChoice.name)
            .putString(KEY_THEME, s.themeMode.name)
            .putBoolean(KEY_DYNAMIC, s.dynamicColor)
            .putBoolean(KEY_AMOLED, s.amoled)
            .putString(KEY_PRESET, s.presetId)
            .putBoolean(KEY_AUTO_CLEAN, s.autoCleanTemp)
            .putBoolean(KEY_CONFIRM_SOFT, s.confirmSoftReboot)
            .putString(KEY_REPOS, array.toString())
            .apply()
    }

    private fun readRepos(): List<String> {
        val raw = prefs.getString(KEY_REPOS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index, "").takeIf { it.isNotBlank() }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Carries over a custom repository chosen in the pre-1.0 settings screen. */
    private fun migrateLegacy(context: Context) {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (!legacy.getBoolean("repo_standard", true)) {
            val url = RepoUrl.normalize(legacy.getString("repo_url", "") ?: "")
            if (url != null && url != RepoUrl.BUILT_IN) {
                val array = JSONArray()
                array.put(url)
                editor.putString(KEY_REPOS, array.toString())
            }
        }
        editor.putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        private const val PREFS = "onikiri_prefs"
        private const val LEGACY_PREFS = "settings"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_MANAGER = "manager_choice"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_PRESET = "preset_id"
        private const val KEY_AUTO_CLEAN = "auto_clean_temp"
        private const val KEY_CONFIRM_SOFT = "confirm_soft_reboot"
        private const val KEY_REPOS = "custom_repos"
        private const val KEY_LEGACY_MIGRATED = "legacy_migrated"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
