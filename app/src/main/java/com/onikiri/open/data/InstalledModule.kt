package com.onikiri.open.data

import androidx.compose.runtime.Immutable

@Immutable
data class InstalledModule(
    /** Directory name inside /data/adb/modules (the module id as the manager sees it). */
    val dir: String,
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val disabled: Boolean,
    val removing: Boolean,
    /** `catalog_id` from data.onikiri (written by Onikiri on install); empty for manual installs. */
    val catalogId: String = "",
    /** `github_repo` from data.onikiri; empty for manual installs. */
    val githubRepo: String = ""
)

/**
 * Parses the output of the scan script: for each module a `@@MODULE|dir|disabled|removing`
 * line, the module.prop contents, optionally `@@DATA` followed by the data.onikiri contents,
 * then `@@END`.
 */
fun parseInstalledModules(lines: List<String>): List<InstalledModule> {
    val result = ArrayList<InstalledModule>()
    var dir: String? = null
    var disabled = false
    var removing = false
    var props = HashMap<String, String>()
    var data = HashMap<String, String>()
    var inData = false

    fun flush() {
        val d = dir ?: return
        result.add(
            InstalledModule(
                dir = d,
                id = props["id"]?.takeIf { it.isNotBlank() } ?: d,
                name = props["name"]?.takeIf { it.isNotBlank() } ?: d,
                version = props["version"].orEmpty(),
                versionCode = props["versionCode"]?.trim()?.toLongOrNull() ?: 0L,
                author = props["author"].orEmpty(),
                description = props["description"].orEmpty(),
                disabled = disabled,
                removing = removing,
                catalogId = data["catalog_id"].orEmpty(),
                githubRepo = data["github_repo"].orEmpty()
            )
        )
        dir = null
        props = HashMap()
        data = HashMap()
        inData = false
    }

    for (raw in lines) {
        val line = raw.trimEnd('\r')
        when {
            line.startsWith("@@MODULE|") -> {
                flush()
                val parts = line.split('|')
                val name = parts.getOrNull(1).orEmpty()
                if (MODULE_DIR_REGEX.matches(name)) {
                    dir = name
                    disabled = parts.getOrNull(2) == "1"
                    removing = parts.getOrNull(3) == "1"
                }
            }
            line == "@@END" -> flush()
            line == "@@DATA" -> if (dir != null) inData = true
            dir != null && line.contains('=') && !line.trimStart().startsWith("#") -> {
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim()
                if (inData) data[key] = value else props[key] = value
            }
        }
    }
    flush()
    return result.sortedBy { it.name.lowercase() }
}

/** Only plain directory names are ever passed to a root shell. */
val MODULE_DIR_REGEX = Regex("^[A-Za-z0-9._-]+$")
