package com.onikiri.open.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

// Values written by earlier builds in the "onikiri_prefs" SharedPreferences are migrated automatically.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onikiri_settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "onikiri_prefs")) }
)

/** Single source of truth for app settings, backed by DataStore and exposed as a StateFlow. */
class SettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.settingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings: MutableStateFlow<AppSettings>
    val settings: StateFlow<AppSettings>

    init {
        // One short blocking read so the first frame already uses the saved theme and font.
        val initial = runBlocking { read(dataStore.data.first()) }
        _settings = MutableStateFlow(initial)
        settings = _settings.asStateFlow()
        scope.launch { dataStore.data.map { read(it) }.collect { _settings.value = it } }
        scope.launch { migrateLegacy() }
    }

    /** Applies [transform] atomically; the new value arrives through [settings]. */
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
        }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            setupDone = p[SETUP_DONE] ?: d.setupDone,
            managerChoice = enumOf(p[MANAGER], d.managerChoice),
            themeMode = enumOf(p[THEME], d.themeMode),
            dynamicColor = p[DYNAMIC] ?: d.dynamicColor,
            amoled = p[AMOLED] ?: d.amoled,
            presetId = p[PRESET] ?: d.presetId,
            fontId = p[FONT] ?: d.fontId,
            launcherIcon = p[LAUNCHER_ICON] ?: d.launcherIcon,
            autoCleanTemp = p[AUTO_CLEAN] ?: d.autoCleanTemp,
            confirmSoftReboot = p[CONFIRM_SOFT] ?: d.confirmSoftReboot,
            customRepos = readList(p[REPOS]),
            disabledRepos = readList(p[DISABLED_REPOS]),
            checkModuleUpdates = p[CHECK_UPDATES] ?: d.checkModuleUpdates
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[SETUP_DONE] = s.setupDone
        p[MANAGER] = s.managerChoice.name
        p[THEME] = s.themeMode.name
        p[DYNAMIC] = s.dynamicColor
        p[AMOLED] = s.amoled
        p[PRESET] = s.presetId
        p[FONT] = s.fontId
        p[LAUNCHER_ICON] = s.launcherIcon
        p[AUTO_CLEAN] = s.autoCleanTemp
        p[CONFIRM_SOFT] = s.confirmSoftReboot
        p[REPOS] = writeList(s.customRepos)
        p[DISABLED_REPOS] = writeList(s.disabledRepos)
        p[CHECK_UPDATES] = s.checkModuleUpdates
    }

    private fun readList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it, "").takeIf { s -> s.isNotBlank() } }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeList(list: List<String>): String {
        val array = JSONArray()
        list.distinct().forEach { array.put(it) }
        return array.toString()
    }

    /** Carries over a custom repository chosen in the original (pre-4.0) settings screen. */
    private suspend fun migrateLegacy() {
        dataStore.edit { p ->
            if (p[LEGACY_MIGRATED] == true) return@edit
            val legacy = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (!legacy.getBoolean("repo_standard", true)) {
                val url = RepoUrl.normalize(legacy.getString("repo_url", "") ?: "")
                if (url != null && url != RepoUrl.BUILT_IN) {
                    val current = readList(p[REPOS])
                    if (url !in current) p[REPOS] = writeList(current + url)
                }
            }
            p[LEGACY_MIGRATED] = true
        }
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        private val SETUP_DONE = booleanPreferencesKey("setup_done")
        private val MANAGER = stringPreferencesKey("manager_choice")
        private val THEME = stringPreferencesKey("theme_mode")
        private val DYNAMIC = booleanPreferencesKey("dynamic_color")
        private val AMOLED = booleanPreferencesKey("amoled")
        private val PRESET = stringPreferencesKey("preset_id")
        private val FONT = stringPreferencesKey("font_id")
        private val LAUNCHER_ICON = stringPreferencesKey("launcher_icon")
        private val AUTO_CLEAN = booleanPreferencesKey("auto_clean_temp")
        private val CONFIRM_SOFT = booleanPreferencesKey("confirm_soft_reboot")
        private val REPOS = stringPreferencesKey("custom_repos")
        private val DISABLED_REPOS = stringPreferencesKey("disabled_repos")
        private val CHECK_UPDATES = booleanPreferencesKey("check_module_updates")
        private val LEGACY_MIGRATED = booleanPreferencesKey("legacy_migrated")

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
