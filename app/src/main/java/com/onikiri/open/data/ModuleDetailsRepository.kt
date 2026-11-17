package com.onikiri.open.data

import android.content.Context
import com.onikiri.open.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads release info (version, author, date, every .zip asset) and README for a module from the
 * GitHub API. Results are cached in memory for the session and on disk for a few hours to stay
 * below the unauthenticated API rate limit.
 */
class ModuleDetailsRepository(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "details")

    suspend fun load(module: RootModule, force: Boolean): ModuleDetails = withContext(Dispatchers.IO) {
        if (!force) memory[module.id]?.let { return@withContext it }
        if (force) {
            memory.remove(module.id)
            requestCache.keys.removeAll { it.contains("/repos/${module.githubRepo}/") }
        }
        dir.mkdirs()
        val cache = File(dir, module.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".v2.json")
        if (!force) {
            readCache(cache, requireFresh = true)?.let {
                memory[module.id] = it
                return@withContext it
            }
        }
        try {
            val details = fetch(module)
            writeCache(cache, details)
            memory[module.id] = details
            details
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val stale = readCache(cache, requireFresh = false) ?: throw e
            memory[module.id] = stale
            stale
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
        val readme = requestText("$base/readme", "application/vnd.github.raw+json")
        val remoteProp = fetchRemoteProp(repo)

        var author = repo.substringBefore('/')
        var version: String? = null
        var releaseName: String? = null
        var publishedAt: String? = null
        var notes: String? = null
        val assets = ArrayList<ReleaseAsset>()

        if (release != null) {
            release.optJSONObject("author")?.str("login")?.takeIf { it.isNotBlank() }?.let { author = it }
            version = release.str("tag_name").takeIf { it.isNotBlank() }
            releaseName = release.str("name").takeIf { it.isNotBlank() }
            publishedAt = release.str("published_at").takeIf { it.isNotBlank() }
            notes = release.str("body").takeIf { it.isNotBlank() }
            val array = release.optJSONArray("assets") ?: JSONArray()
            for (i in 0 until array.length()) {
                val asset = array.optJSONObject(i) ?: continue
                val name = asset.str("name")
                val url = asset.str("browser_download_url")
                if (!name.endsWith(".zip", ignoreCase = true) || !url.startsWith("https://")) continue
                assets.add(ReleaseAsset(name, url, asset.optLong("size", 0L)))
            }
        }

        return ModuleDetails(
            author = author,
            version = version,
            releaseName = releaseName,
            publishedAt = publishedAt,
            releaseNotes = notes,
            readme = readme?.take(MAX_README_CHARS),
            assets = assets,
            remoteVersion = remoteProp?.get("version")?.takeIf { it.isNotBlank() },
            remoteVersionCode = remoteProp?.get("versionCode")?.trim()?.toLongOrNull()
        )
    }

    /** module.prop from the repository root; many module repos keep it there. Failures are ignored. */
    private fun fetchRemoteProp(repo: String): Map<String, String>? {
        val connection = try {
            Http.open("https://raw.githubusercontent.com/$repo/HEAD/module.prop", accept = "text/plain")
        } catch (_: Exception) {
            return null
        }
        return try {
            if (connection.responseCode != 200) {
                null
            } else {
                val text = String(connection.inputStream.use { Http.readLimited(it, 64 * 1024) }, Charsets.UTF_8)
                text.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun request(url: String, accept: String): Pair<Int, String?> {
        requestCache[url]?.let { return it }
        val connection = Http.open(
            url = url,
            accept = accept,
            headers = mapOf("X-GitHub-Api-Version" to "2022-11-28")
        )
        try {
            val code = connection.responseCode
            val result = when (code) {
                200 -> code to String(
                    connection.inputStream.use { Http.readLimited(it, MAX_RESPONSE_BYTES) },
                    Charsets.UTF_8
                )
                404 -> code to null
                403, 429 -> throw UiException(R.string.err_rate_limit, code)
                else -> throw UiException(R.string.err_http, code)
            }
            requestCache[url] = result
            return result
        } finally {
            connection.disconnect()
        }
    }

    private fun requestJson(url: String): JSONObject? =
        request(url, "application/vnd.github+json").second?.let { JSONObject(it) }

    private fun requestJsonArray(url: String): JSONArray? =
        request(url, "application/vnd.github+json").second?.let { JSONArray(it) }

    private fun requestText(url: String, accept: String): String? = request(url, accept).second

    private fun readCache(file: File, requireFresh: Boolean): ModuleDetails? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            val age = System.currentTimeMillis() - json.optLong("fetchedAt", 0L)
            if (requireFresh && age > CACHE_TTL_MS) return null
            val assets = ArrayList<ReleaseAsset>()
            val array = json.optJSONArray("assets") ?: JSONArray()
            for (i in 0 until array.length()) {
                val a = array.optJSONObject(i) ?: continue
                assets.add(ReleaseAsset(a.str("name"), a.str("url"), a.optLong("size", 0L)))
            }
            ModuleDetails(
                author = json.str("author"),
                version = json.str("version").takeIf { it.isNotBlank() },
                releaseName = json.str("releaseName").takeIf { it.isNotBlank() },
                publishedAt = json.str("publishedAt").takeIf { it.isNotBlank() },
                releaseNotes = json.str("releaseNotes").takeIf { it.isNotBlank() },
                readme = json.str("readme").takeIf { it.isNotBlank() },
                assets = assets,
                remoteVersion = json.str("remoteVersion").takeIf { it.isNotBlank() },
                remoteVersionCode = if (json.has("remoteVersionCode")) json.optLong("remoteVersionCode") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(file: File, details: ModuleDetails) {
        try {
            val assets = JSONArray()
            details.assets.forEach {
                assets.put(JSONObject().put("name", it.name).put("url", it.url).put("size", it.size))
            }
            val json = JSONObject()
                .put("fetchedAt", System.currentTimeMillis())
                .put("author", details.author)
                .put("version", details.version ?: "")
                .put("releaseName", details.releaseName ?: "")
                .put("publishedAt", details.publishedAt ?: "")
                .put("releaseNotes", details.releaseNotes ?: "")
                .put("readme", details.readme ?: "")
                .put("assets", assets)
                .put("remoteVersion", details.remoteVersion ?: "")
            details.remoteVersionCode?.let { json.put("remoteVersionCode", it) }
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

        /** Process-wide, lives for the session only. */
        val memory = ConcurrentHashMap<String, ModuleDetails>()
        val requestCache = ConcurrentHashMap<String, Pair<Int, String?>>()
    }
}
