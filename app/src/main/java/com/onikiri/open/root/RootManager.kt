package com.onikiri.open.root

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.onikiri.open.R
import com.onikiri.open.data.ManagerChoice

enum class RootManager(@param:StringRes val labelRes: Int) {
    MAGISK(R.string.magisk),
    KERNELSU(R.string.ksu),
    APATCH(R.string.apatch)
}

fun ManagerChoice.toManager(): RootManager? = when (this) {
    ManagerChoice.AUTO -> null
    ManagerChoice.MAGISK -> RootManager.MAGISK
    ManagerChoice.KERNELSU -> RootManager.KERNELSU
    ManagerChoice.APATCH -> RootManager.APATCH
}

@Immutable
data class DetectedManager(
    val manager: RootManager,
    val binary: String,
    val version: String?
)

@Immutable
data class RootStatus(
    val granted: Boolean,
    val managers: List<DetectedManager>
) {
    /** AUTO picks the first detected manager (KernelSU, APatch, Magisk in that order). */
    fun resolve(choice: ManagerChoice): DetectedManager? {
        val wanted = choice.toManager() ?: return managers.firstOrNull()
        return managers.firstOrNull { it.manager == wanted }
    }
}
