package com.onikiri.open.data

import org.json.JSONException
import org.json.JSONObject

private val REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

internal fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name, "")

/**
 * Parses a JSONL repository index. Malformed lines, lines without id or with an invalid
 * `github_repo`, and duplicate ids are skipped instead of failing the whole catalog.
 */
fun parseModules(text: String): List<RootModule> {
    val result = LinkedHashMap<String, RootModule>()
    for (raw in text.lineSequence()) {
        val line = raw.trim().removePrefix("\uFEFF").trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val module = try {
            val json = JSONObject(line)
            val id = json.str("id").trim()
            val repo = json.str("github_repo").trim()
            if (id.isEmpty() || !REPO_REGEX.matches(repo)) {
                null
            } else {
                RootModule(
                    id = id,
                    title = json.str("title").trim().ifEmpty { id },
                    description = json.str("description").trim(),
                    githubRepo = repo,
                    status = ModuleStatus.fromRaw(json.str("status"))
                )
            }
        } catch (_: JSONException) {
            null
        }
        if (module != null && !result.containsKey(module.id)) result[module.id] = module
    }
    return result.values.toList()
}
