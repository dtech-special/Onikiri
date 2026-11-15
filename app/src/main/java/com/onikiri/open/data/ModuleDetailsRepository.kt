package com.onikiri.open.data

import android.content.Context
import com.onikiri.open.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Loads release info (version, author, date, zip asset) and README for a module from the
 * GitHub API. Results are cached on disk for a few hours to stay below API rate limits.
 */
class ModuleDetailsRepository(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "details")

    suspend fun load(module: RootModule, force: Boolean): ModuleDetails = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val cache = File(dir, module.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")
        if (!force) {
            readCache(cache, requireFresh = true)?.let { return@withContext it }
        }
        try {
            val details = fetch(module)
            writeCache(cache, details)
            details
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            readCache(cache, requireFresh = false) ?: throw e
        }
    }

    private fun fetch(module: RootModule): ModuleDetails {
        val repo = module.githubRepo
        val base = "https://api.github.com/repos/$repo"

        var release = requestJson("$base/releases/latest")
        if (release == null) {
            val list = requestJsonArray("$base/releases?per_page=5")
            if (list != null) {
                for (i in 0 until list.length()) {
                    val candidate = list.optJSONObject(i) ?: continue
                    if (!candidate.optBoolean("draft", false)) {
                        release = candidate
                        break
                    }
                }
            }
        }
        val readme = requestText(
            url = "$base/readme",
            accept = "application/vnd.github.raw+json"
        )

        var author = repo.substringBefore('/')
        var version: String? = null
        var releaseName: String? = null
        var publishedAt: String? = null
        var notes: String? = null
        var downloadUrl: String? = null
        var downloadName: String? = null
        var downloadSize = 0L

        if (release != null) {
            release.optJSONObject("author")?.str("login")?.takeIf { it.isNotBlank() }?.let { author = it }
            version = release.str("tag_name").takeIf { it.isNotBlank() }
            releaseName = release.str("name").takeIf { it.isNotBlank() }
            publishedAt = release.str("published_at").takeIf { it.isNotBlank() }
            notes = release.str("body").takeIf { it.isNotBlank() }

            val assets = release.optJSONArray("assets") ?: JSONArray()
            var bestScore = -1
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.str("name")
                val url = asset.str("browser_download_url")
                if (!name.endsWith(".zip", ignoreCase = true) || !url.startsWith("https://")) continue
                val score = when {
                    name.contains("magisk", true) || name.contains("module", true) -> 2
                    name.contains("source", true) || name.contains("debug", true) -> 0
                    else -> 1
                }
                if (score > bestScore) {
                    bestScore = score
                    downloadUrl = url
                    downloadName = name
                    downloadSize = asset.optLong("size", 0L)
                }
            }
        }

        return ModuleDetails(
            author = author,
            version = version,
            releaseName = releaseName,
            publishedAt = publishedAt,
            releaseNotes = notes,
            readme = readme?.take(MAX_README_CHARS),
            downloadUrl = downloadUrl,
            downloadName = downloadName,
            downloadSize = downloadSize
        )
    }

    private fun request(url: String, accept: String): Pair<Int, String?> {
        val connection = Http.open(
            url = url,
            accept = accept,
            headers = mapOf("X-GitHub-Api-Version" to "2022-11-28")
        )
        try {
            val code = connection.responseCode
            return when (code) {
                200 -> code to String(
                    connection.inputStream.use { Http.readLimited(it, MAX_RESPONSE_BYTES) },
                    Charsets.UTF_8
                )
                404 -> code to null
                403, 429 -> throw UiException(R.string.err_rate_limit)
                else -> throw UiException(R.string.err_http, code)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestJson(url: String): JSONObject? =
        request(url, "application/vnd.github+json").second?.let { JSONObject(it) }

    private fun requestJsonArray(url: String): JSONArray? =
        request(url, "application/vnd.github+json").second?.let { JSONArray(it) }

    private fun requestText(url: String, accept: String): String? =
        request(url, accept).second

    private fun readCache(file: File, requireFresh: Boolean): ModuleDetails? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            val age = System.currentTimeMillis() - json.optLong("fetchedAt", 0L)
            if (requireFresh && age > CACHE_TTL_MS) return null
            ModuleDetails(
                author = json.str("author"),
                version = json.str("version").takeIf { it.isNotBlank() },
                releaseName = json.str("releaseName").takeIf { it.isNotBlank() },
                publishedAt = json.str("publishedAt").takeIf { it.isNotBlank() },
                releaseNotes = json.str("releaseNotes").takeIf { it.isNotBlank() },
                readme = json.str("readme").takeIf { it.isNotBlank() },
                downloadUrl = json.str("downloadUrl").takeIf { it.isNotBlank() },
                downloadName = json.str("downloadName").takeIf { it.isNotBlank() },
                downloadSize = json.optLong("downloadSize", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(file: File, details: ModuleDetails) {
        try {
            val json = JSONObject()
                .put("fetchedAt", System.currentTimeMillis())
                .put("author", details.author)
                .put("version", details.version ?: "")
                .put("releaseName", details.releaseName ?: "")
                .put("publishedAt", details.publishedAt ?: "")
                .put("releaseNotes", details.releaseNotes ?: "")
                .put("readme", details.readme ?: "")
                .put("downloadUrl", details.downloadUrl ?: "")
                .put("downloadName", details.downloadName ?: "")
                .put("downloadSize", details.downloadSize)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
            // Cache is best-effort only.
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 6L * 60 * 60 * 1000
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_README_CHARS = 12_000
    }
}
