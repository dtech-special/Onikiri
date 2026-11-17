package com.onikiri.open.data

import org.json.JSONException
import org.json.JSONObject
import java.net.URI

private val REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
private val NAME_PREFIX = Regex("^name\\s*[:=]\\s*", RegexOption.IGNORE_CASE)

internal fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name, "")

data class ParsedIndex(val name: String?, val modules: List<RootModule>)

/**
 * Parses a JSONL repository index.
 * - Lines starting with `#` are comments. If the first non-empty line is a comment it is the
 *   display name of the repository (`# Optimize Pack` or `# Name: Optimize Pack`).
 * - Malformed lines, lines without id or with an invalid `github_repo`, and duplicate ids are
 *   skipped instead of failing the whole catalog.
 */
fun parseRepoIndex(text: String, repoUrl: String = ""): ParsedIndex {
    val result = LinkedHashMap<String, RootModule>()
    var name: String? = null
    var firstSeen = false
    for (raw in text.lineSequence()) {
        val line = raw.trim().removePrefix("\uFEFF").trim()
        if (line.isEmpty()) continue
        if (!firstSeen) {
            firstSeen = true
            if (line.startsWith("#")) name = headerName(line)
        }
        if (line.startsWith("#")) continue
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
                    status = ModuleStatus.fromRaw(json.str("status")),
                    repoUrl = repoUrl,
                    assetFilter = json.str("asset_filter").trim()
                )
            }
        } catch (_: JSONException) {
            null
        }
        if (module != null && !result.containsKey(module.id)) result[module.id] = module
    }
    return ParsedIndex(name, result.values.toList())
}

fun parseModules(text: String, repoUrl: String = ""): List<RootModule> =
    parseRepoIndex(text, repoUrl).modules

private fun headerName(line: String): String? {
    var title = line.trimStart('#').trim()
    val prefix = NAME_PREFIX.find(title)
    if (prefix != null) title = title.substring(prefix.range.last + 1).trim()
    return title.ifEmpty { null }
}

/** `https://host/path/Optimize.jsonl` -> `Optimize`; falls back to the host name. */
fun repoNameFromUrl(url: String): String {
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        return url
    }
    // URI.path is already percent-decoded.
    val file = uri.path.orEmpty().substringAfterLast('/')
    val base = file.substringBeforeLast('.', file).ifBlank { file }
    return base.ifBlank { uri.host ?: url }
}
