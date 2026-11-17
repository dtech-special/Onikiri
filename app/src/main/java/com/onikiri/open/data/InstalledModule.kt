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
    val removing: Boolean
)

/**
 * Parses the output of the scan script: for each module a `@@MODULE|dir|disabled|removing`
 * line, the module.prop contents, then `@@END`.
 */
fun parseInstalledModules(lines: List<String>): List<InstalledModule> {
    val result = ArrayList<InstalledModule>()
    var dir: String? = null
    var disabled = false
    var removing = false
    var props = HashMap<String, String>()

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
                removing = removing
            )
        )
        dir = null
        props = HashMap()
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
            dir != null && line.contains('=') && !line.trimStart().startsWith("#") -> {
                props[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
            }
        }
    }
    flush()
    return result.sortedBy { it.name.lowercase() }
}

/** Only plain directory names are ever passed to a root shell. */
val MODULE_DIR_REGEX = Regex("^[A-Za-z0-9._-]+$")
