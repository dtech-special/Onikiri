package com.onikiri.open

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.onikiri.open.data.SettingsStore
import com.onikiri.open.install.CacheManager
import com.onikiri.open.ui.OnikiriRoot
import com.onikiri.open.ui.theme.OnikiriTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = SettingsStore.get(applicationContext)
        if (store.settings.value.autoCleanTemp) {
            // Removes unfinished downloads and archives left over from an interrupted install.
            lifecycleScope.launch(Dispatchers.IO) { CacheManager.cleanStale(applicationContext) }
        }

        setContent {
            val settings by store.settings.collectAsStateWithLifecycle()
            OnikiriTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                presetId = settings.presetId,
                amoled = settings.amoled,
                fontId = settings.fontId
            ) {
                OnikiriRoot()
            }
        }
    }
}
