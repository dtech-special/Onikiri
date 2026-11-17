package com.onikiri.open.data

import androidx.compose.runtime.Immutable

enum class ModuleStatus {
    VERIFIED, REVIEW, HAZARD, UNKNOWN;

    companion object {
        fun fromRaw(raw: String): ModuleStatus = when (raw.trim()) {
            "1" -> VERIFIED
            "2" -> REVIEW
            "3" -> HAZARD
            else -> UNKNOWN
        }
    }
}

@Immutable
data class RootModule(
    val id: String,
    val title: String,
    val description: String,
    val githubRepo: String,
    val status: ModuleStatus,
    /** URL of the repository index this module came from. */
    val repoUrl: String = "",
    /** Optional substring used to pick the right release asset automatically. */
    val assetFilter: String = ""
) {
    val owner: String get() = githubRepo.substringBefore('/')
}

@Immutable
data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long
)

@Immutable
data class ModuleDetails(
    val author: String,
    val version: String?,
    val releaseName: String?,
    val publishedAt: String?,
    val releaseNotes: String?,
    val readme: String?,
    val assets: List<ReleaseAsset>,
    /** Version / versionCode from module.prop in the repository root, when it exists. */
    val remoteVersion: String? = null,
    val remoteVersionCode: Long? = null
)
