package com.onikiri.open.data

import androidx.compose.runtime.Immutable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ManagerChoice { AUTO, MAGISK, KERNELSU, APATCH }

@Immutable
data class AppSettings(
    val setupDone: Boolean = false,
    val managerChoice: ManagerChoice = ManagerChoice.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val presetId: String = "onikiri",
    val autoCleanTemp: Boolean = true,
    val confirmSoftReboot: Boolean = true,
    val customRepos: List<String> = emptyList()
)
