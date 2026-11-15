package com.onikiri.open.install

import android.content.Context
import com.onikiri.open.R
import com.onikiri.open.data.Http
import com.onikiri.open.data.UiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Streams a module zip into cacheDir/downloads and reports real progress. */
class ModuleDownloader(context: Context) {

    private val appContext = context.applicationContext

    /**
     * @param expectedSize size announced by the release asset, used when the server does not send
     * a Content-Length. Pass 0 when unknown.
     * @param onProgress called with (downloadedBytes, totalBytes); totalBytes is -1 when unknown.
     */
    suspend fun download(
        url: String,
        moduleId: String,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = CacheManager.downloadsDir(appContext)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create ${dir.path}")

        val safeName = moduleId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, "$safeName.zip")
        val part = File(dir, "$safeName.zip.part")
        part.delete()
        target.delete()

        val connection = Http.open(url = url, accept = "application/octet-stream, */*")
        val completionHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            // Unblocks a read() that is waiting on the socket when the coroutine is cancelled.
            if (cause != null) connection.disconnect()
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw UiException(R.string.err_http, code)

            val total = connection.contentLengthLong.takeIf { it > 0 }
                ?: expectedSize.takeIf { it > 0 }
                ?: -1L
            if (total > 0 && dir.usableSpace < total + MIN_FREE_BYTES) {
                throw UiException(R.string.err_no_space)
            }

            var downloaded = 0L
            connection.inputStream.use { input ->
                part.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.nanoTime()
                        if (now - lastReport >= REPORT_INTERVAL_NS) {
                            lastReport = now
                            onProgress(downloaded, total)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(downloaded, if (total > 0) total else downloaded)
            if (total > 0 && downloaded != total) {
                throw UiException(R.string.err_incomplete, downloaded, total)
            }
            if (!part.renameTo(target)) throw IOException("Cannot finalize ${target.name}")
            target
        } catch (t: Throwable) {
            part.delete()
            throw t
        } finally {
            completionHandle?.dispose()
            connection.disconnect()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val REPORT_INTERVAL_NS = 80_000_000L
        const val MIN_FREE_BYTES = 16L * 1024 * 1024
    }
}
