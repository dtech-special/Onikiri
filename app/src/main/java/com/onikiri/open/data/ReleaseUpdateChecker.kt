package com.onikiri.open.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection

/**
 * Looks up the tag of the latest GitHub release of a repository (`GET /repos/{owner}/{repo}/releases/latest`).
 * The tag and the response ETag are kept on disk, so a repeated check is a conditional request
 * (`If-None-Match`) that normally ends with a tiny `304 Not Modified`.
 */
class ReleaseUpdateChecker(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "updates")

    /** The latest release tag, the cached one when the server says nothing changed or fails, null when unknown. */
    suspend fun latestTag(repo: String): String? = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val key = repo.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val tagFile = File(dir, "$key.tag")
        val etagFile = File(dir, "$key.etag")
        val cachedTag = tagFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val etag = if (cachedTag != null) {
            etagFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val headers = HashMap<String, String>()
        headers["X-GitHub-Api-Version"] = "2022-11-28"
        if (etag != null) headers["If-None-Match"] = etag

        val connection = try {
            Http.open("https://api.github.com/repos/$repo/releases/latest", "application/vnd.github+json", headers)
        } catch (e: Exception) {
            return@withContext cachedTag
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> cachedTag
                HttpURLConnection.HTTP_OK -> {
                    val body = String(connection.inputStream.use { Http.readLimited(it, MAX_BYTES) }, Charsets.UTF_8)
                    val tag = JSONObject(body).str("tag_name").trim().takeIf { it.isNotEmpty() }
                    if (tag != null) {
                        tagFile.writeText(tag)
                        val newEtag = connection.getHeaderField("ETag")
                        if (newEtag.isNullOrBlank()) etagFile.delete() else etagFile.writeText(newEtag)
                    }
                    tag
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    tagFile.delete()
                    etagFile.delete()
                    null
                }
                else -> cachedTag // rate limit or server error: keep what is known
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cachedTag
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
