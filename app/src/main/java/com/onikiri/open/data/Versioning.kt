package com.onikiri.open.data

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE }

private val NUMBER = Regex("\\d+")
private val VERSION_PREFIX = Regex("^[vV](?=\\d)")
private val VERSION_SUFFIX = Regex("-(release|beta)$", RegexOption.IGNORE_CASE)

/**
 * Cleans a module.prop / tag version: `1.5.3 (880-22a1bf5-release)` -> `1.5.3`, `v2.0-beta` -> `2.0`.
 * Everything from `(` is dropped, a `v`/`V` prefix and `-release` / `-beta` suffixes are removed.
 */
fun cleanVersion(raw: String): String {
    var s = raw.substringBefore('(').trim().replace(VERSION_PREFIX, "")
    while (true) {
        val next = s.replace(VERSION_SUFFIX, "").trim()
        if (next == s) break
        s = next
    }
    return s
}

/** `1.5.3 (880-22a1bf5-release)` -> `[1, 5, 3]`. */
fun versionParts(raw: String): List<Long> =
    NUMBER.findAll(cleanVersion(raw)).map { it.value.toLongOrNull() ?: Long.MAX_VALUE }.toList()

/**
 * Compares the numeric parts of two version strings ("v1.2.10" > "1.2.9").
 * Returns 0 when either side has no digits, i.e. when the order cannot be known.
 */
fun compareVersions(a: String, b: String): Int {
    val x = versionParts(a)
    val y = versionParts(b)
    if (x.isEmpty() || y.isEmpty()) return 0
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = x.getOrElse(i) { 0L }.compareTo(y.getOrElse(i) { 0L })
        if (c != 0) return c
    }
    return 0
}

/**
 * NOT_INSTALLED when the module is not in /data/adb/modules, UPDATE when the repository, its latest
 * release ([details], needs a downloadable zip) or the latest release tag from the background
 * check ([latestTag]) is newer than the version in the installed module.prop.
 */
fun installStateOf(installed: InstalledModule?, details: ModuleDetails?, latestTag: String? = null): InstallState {
    if (installed == null) return InstallState.NOT_INSTALLED
    if (details != null && details.assets.any { it.name.endsWith(".zip", ignoreCase = true) }) {
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
    }
    if (latestTag != null && installed.version.isNotBlank() &&
        compareVersions(latestTag, installed.version) > 0
    ) {
        return InstallState.UPDATE
    }
    return InstallState.INSTALLED
}

private val NON_ALNUM = Regex("[^a-z0-9]")

/** `trim().lowercase()` without anything but a-z and 0-9; used to match modules installed by hand. */
fun normalizeKey(value: String): String = value.trim().lowercase().replace(NON_ALNUM, "")

/**
 * Maps catalog module id -> installed module. First by `catalog_id` from data.onikiri; modules
 * without that file fall back to the normalized module.prop id / name against the catalog id / title.
 */
fun matchInstalled(catalog: List<RootModule>, installed: List<InstalledModule>): Map<String, InstalledModule> {
    if (catalog.isEmpty() || installed.isEmpty()) return emptyMap()
    val byCatalogId = HashMap<String, InstalledModule>()
    val byKey = HashMap<String, InstalledModule>()
    for (module in installed) {
        if (module.catalogId.isNotBlank()) {
            byCatalogId.putIfAbsent(module.catalogId, module)
        } else {
            for (key in listOf(module.id, module.name)) {
                val k = normalizeKey(key)
                if (k.isNotEmpty()) byKey.putIfAbsent(k, module)
            }
        }
    }
    val result = LinkedHashMap<String, InstalledModule>()
    for (entry in catalog) {
        val hit = byCatalogId[entry.id] ?: run {
            var found: InstalledModule? = null
            for (key in listOf(entry.id, entry.title)) {
                val k = normalizeKey(key)
                if (k.isNotEmpty()) {
                    found = byKey[k]
                    if (found != null) break
                }
            }
            found
        }
        if (hit != null) result[entry.id] = hit
    }
    return result
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
