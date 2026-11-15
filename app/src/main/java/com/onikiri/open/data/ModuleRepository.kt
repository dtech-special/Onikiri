package com.onikiri.open.data

import android.content.Context
import com.onikiri.open.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest

/**
 * Downloads and caches JSONL module indexes. Uses ETag conditional requests, writes atomically
 * and falls back to the on-disk copy when the network is unavailable.
 */
class ModuleRepository(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "repos")

    data class CatalogResult(
        val modules: List<RootModule>,
        val failedRepos: Int,
        val offline: Boolean
    )

    private class Fetched(val text: String, val fromCache: Boolean)

    suspend fun load(urls: List<String>, force: Boolean): CatalogResult = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val merged = LinkedHashMap<String, RootModule>()
        var failed = 0
        var servedFromCache = 0
        var firstError: Throwable? = null
        for (url in urls) {
            ensureActive()
            try {
                val fetched = fetch(url, force)
                if (fetched.fromCache) servedFromCache++
                for (module in parseModules(fetched.text)) {
                    if (!merged.containsKey(module.id)) merged[module.id] = module
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                if (firstError == null) firstError = e
            }
        }
        if (failed == urls.size && merged.isEmpty()) {
            throw firstError ?: IOException("No repositories configured")
        }
        CatalogResult(merged.values.toList(), failed, servedFromCache > 0)
    }

    private fun fetch(url: String, force: Boolean): Fetched {
        val key = sha(url)
        val data = File(dir, "$key.jsonl")
        val etagFile = File(dir, "$key.etag")
        val etag = if (!force && data.isFile && etagFile.isFile) {
            etagFile.readText().trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
        return try {
            fetchRemote(url, data, etagFile, etag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (data.isFile) Fetched(data.readText(), true) else throw e
        }
    }

    private fun fetchRemote(url: String, data: File, etagFile: File, etag: String?): Fetched {
        val headers = if (etag != null) mapOf("If-None-Match" to etag) else emptyMap()
        val connection = Http.open(
            url = url,
            accept = "text/plain, application/x-ndjson, */*",
            headers = headers
        )
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && data.isFile) {
                return Fetched(data.readText(), false)
            }
            if (code != HttpURLConnection.HTTP_OK) throw UiException(R.string.err_http, code)

            val bytes = connection.inputStream.use { Http.readLimited(it, MAX_INDEX_BYTES) }
            val tmp = File(data.parentFile, data.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(data)) {
                data.delete()
                if (!tmp.renameTo(data)) {
                    tmp.delete()
                    throw IOException("Cannot save repository index")
                }
            }
            val newEtag = connection.getHeaderField("ETag")
            if (newEtag.isNullOrBlank()) etagFile.delete() else etagFile.writeText(newEtag)
            return Fetched(String(bytes, Charsets.UTF_8), false)
        } finally {
            connection.disconnect()
        }
    }

    private fun sha(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

    private companion object {
        const val MAX_INDEX_BYTES = 5 * 1024 * 1024
    }
}
