package com.onikiri.open.install

import com.onikiri.open.R
import com.onikiri.open.data.UiException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class ValidationResult(
    val moduleId: String,
    val moduleName: String?,
    val version: String?,
    val entryCount: Int
)

/**
 * Checks that a downloaded file is a real, intact module archive before it is flashed:
 * non-empty, ZIP signature, readable central directory, CRC of every entry, no path traversal,
 * and a module.prop with an id in the archive root.
 */
object ModuleValidator {

    private const val MAX_UNPACKED_BYTES = 4L * 1024 * 1024 * 1024
    private const val MAX_PROP_BYTES = 64 * 1024

    @Throws(UiException::class)
    fun validate(file: File): ValidationResult {
        if (!file.isFile) throw UiException(R.string.val_missing)
        if (file.length() == 0L) throw UiException(R.string.val_empty)
        if (!hasZipSignature(file)) throw UiException(R.string.val_not_zip)

        val zip = try {
            ZipFile(file)
        } catch (e: IOException) {
            throw UiException(R.string.val_corrupt, e.localizedMessage ?: "zip")
        }

        return zip.use { archive ->
            var entries = 0
            var unpacked = 0L
            var propText: String? = null
            val buffer = ByteArray(64 * 1024)
            val enumeration = archive.entries()

            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                val name = entry.name
                if (name.startsWith("/") || name.contains('\\') || name.split('/').any { it == ".." }) {
                    throw UiException(R.string.val_unsafe, name)
                }
                entries++
                if (entry.isDirectory) continue

                val isProp = name == "module.prop"
                val propBytes = if (isProp) ByteArrayOutputStream() else null
                val crc = CRC32()
                try {
                    archive.getInputStream(entry).use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            crc.update(buffer, 0, read)
                            unpacked += read
                            if (unpacked > MAX_UNPACKED_BYTES) throw UiException(R.string.val_too_large)
                            if (propBytes != null) {
                                if (propBytes.size() + read > MAX_PROP_BYTES) {
                                    throw UiException(R.string.val_bad_prop)
                                }
                                propBytes.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw UiException(R.string.val_corrupt, e.localizedMessage ?: name)
                }
                if (entry.crc != -1L && entry.crc != crc.value) throw UiException(R.string.val_crc, name)
                if (propBytes != null) propText = String(propBytes.toByteArray(), Charsets.UTF_8)
            }

            if (entries == 0) throw UiException(R.string.val_empty)
            val text = propText ?: throw UiException(R.string.val_no_prop)
            val props = text.lineSequence()
                .map { it.trim().removePrefix("\uFEFF") }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            val id = props["id"]?.takeIf { it.isNotBlank() } ?: throw UiException(R.string.val_bad_prop)
            ValidationResult(
                moduleId = id,
                moduleName = props["name"]?.takeIf { it.isNotBlank() },
                version = props["version"]?.takeIf { it.isNotBlank() },
                entryCount = entries
            )
        }
    }

    private fun hasZipSignature(file: File): Boolean {
        if (file.length() < 4) return false
        val head = ByteArray(4)
        RandomAccessFile(file, "r").use { it.readFully(head) }
        val local = head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
        val emptyArchive = head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x05.toByte() && head[3] == 0x06.toByte()
        return local || emptyArchive
    }
}
