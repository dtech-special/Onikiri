package com.onikiri.open.data

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE }

private val NUMBER = Regex("\\d+")

/**
 * Compares the numeric parts of two version strings ("v1.2.10" > "1.2.9").
 * Returns 0 when either side has no digits, i.e. when the order cannot be known.
 */
fun compareVersions(a: String, b: String): Int {
    val x = NUMBER.findAll(a).map { it.value.toBigInteger() }.toList()
    val y = NUMBER.findAll(b).map { it.value.toBigInteger() }.toList()
    if (x.isEmpty() || y.isEmpty()) return 0
    for (i in 0 until maxOf(x.size, y.size)) {
        val l = x.getOrElse(i) { java.math.BigInteger.ZERO }
        val r = y.getOrElse(i) { java.math.BigInteger.ZERO }
        val c = l.compareTo(r)
        if (c != 0) return c
    }
    return 0
}

/**
 * NOT_INSTALLED when the module is not in /data/adb/modules, UPDATE when the repository or its
 * latest release is newer (by versionCode or by version) and a zip is actually downloadable.
 */
fun installStateOf(installed: InstalledModule?, details: ModuleDetails?): InstallState {
    if (installed == null) return InstallState.NOT_INSTALLED
    if (details == null || details.assets.none { it.name.endsWith(".zip", ignoreCase = true) }) {
        return InstallState.INSTALLED
    }
    val remoteCode = details.remoteVersionCode
    if (remoteCode != null && installed.versionCode > 0 && remoteCode > installed.versionCode) {
        return InstallState.UPDATE
    }
    val candidate = details.version ?: details.remoteVersion
    if (candidate != null && installed.version.isNotBlank() &&
        compareVersions(candidate, installed.version) > 0
    ) {
        return InstallState.UPDATE
    }
    return InstallState.INSTALLED
}

private fun isUnlikelyModule(name: String): Boolean =
    name.equals("png.zip", ignoreCase = true) || name.contains("source", ignoreCase = true)

/** All .zip assets, narrowed by the optional `asset_filter` substring when it matches anything. */
fun selectAssets(assets: List<ReleaseAsset>, filter: String): List<ReleaseAsset> {
    val zips = assets.filter { it.name.endsWith(".zip", ignoreCase = true) }
    if (filter.isBlank()) return zips
    val matched = zips.filter { it.name.contains(filter, ignoreCase = true) }
    return matched.ifEmpty { zips }
}

/** First archive that looks like a module (skips png.zip and source archives) and was not rejected. */
fun defaultAsset(candidates: List<ReleaseAsset>, rejected: Set<String> = emptySet()): ReleaseAsset? =
    candidates.firstOrNull { it.name !in rejected && !isUnlikelyModule(it.name) }
        ?: candidates.firstOrNull { it.name !in rejected }
