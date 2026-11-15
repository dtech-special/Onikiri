package com.onikiri.open.install

import android.content.Context
import java.io.File

/** Everything the installer downloads lives in cacheDir/downloads. */
object CacheManager {

    private const val STALE_AFTER_MS = 15L * 60 * 1000

    fun downloadsDir(context: Context): File =
        File(context.applicationContext.cacheDir, "downloads")

    fun sizeBytes(context: Context): Long =
        downloadsDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Deletes every downloaded archive. Returns the number of freed bytes. */
    fun clear(context: Context): Long {
        val dir = downloadsDir(context)
        val before = sizeBytes(context)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        return before - sizeBytes(context)
    }

    /** Removes unfinished downloads and archives left behind by an interrupted installation. */
    fun cleanStale(context: Context) {
        val limit = System.currentTimeMillis() - STALE_AFTER_MS
        downloadsDir(context).listFiles()?.forEach { file ->
            if (file.name.endsWith(".part") || file.lastModified() < limit) file.deleteRecursively()
        }
    }
}
