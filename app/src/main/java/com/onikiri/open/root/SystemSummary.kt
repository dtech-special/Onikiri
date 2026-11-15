package com.onikiri.open.root

import android.os.Build
import androidx.compose.runtime.Immutable

@Immutable
data class SystemSummary(
    val kernel: String,
    val architecture: String,
    val android: String,
    val device: String
)

fun readSystemSummary(): SystemSummary {
    val abis = Build.SUPPORTED_ABIS.joinToString().ifEmpty { "unknown" }
    val arch = System.getProperty("os.arch")
    return SystemSummary(
        kernel = System.getProperty("os.version") ?: "unknown",
        architecture = if (arch.isNullOrBlank()) abis else "$abis ($arch)",
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    )
}
