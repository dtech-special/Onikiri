package com.onikiri.open

import com.onikiri.open.data.UiException
import com.onikiri.open.install.ModuleValidator
import com.onikiri.open.root.DetectedManager
import com.onikiri.open.root.RootManager
import com.onikiri.open.root.RootShell
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstallLogicTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = tmp.newFile()
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun expectError(resId: Int, block: () -> Unit) {
        try {
            block()
            fail("Expected UiException")
        } catch (e: UiException) {
            assertEquals(resId, e.resId)
        }
    }

    @Test
    fun acceptsValidModule() {
        val file = zip(
            "module.prop" to "id=my_mod\nname=My Module\nversion=v1.2\n",
            "service.sh" to "#!/system/bin/sh\n"
        )
        val result = ModuleValidator.validate(file)
        assertEquals("my_mod", result.moduleId)
        assertEquals("My Module", result.moduleName)
        assertEquals("v1.2", result.version)
        assertEquals(2, result.entryCount)
    }

    @Test
    fun rejectsEmptyFile() = expectError(R.string.val_empty) {
        ModuleValidator.validate(tmp.newFile())
    }

    @Test
    fun rejectsNonZip() = expectError(R.string.val_not_zip) {
        val file = tmp.newFile()
        file.writeText("<html>404 Not Found</html>")
        ModuleValidator.validate(file)
    }

    @Test
    fun rejectsMissingModuleProp() = expectError(R.string.val_no_prop) {
        ModuleValidator.validate(zip("readme.txt" to "hi"))
    }

    @Test
    fun rejectsModulePropInSubfolder() = expectError(R.string.val_no_prop) {
        ModuleValidator.validate(zip("folder/module.prop" to "id=x\n"))
    }

    @Test
    fun rejectsModulePropWithoutId() = expectError(R.string.val_bad_prop) {
        ModuleValidator.validate(zip("module.prop" to "name=No id here\n"))
    }

    @Test
    fun rejectsPathTraversal() = expectError(R.string.val_unsafe) {
        ModuleValidator.validate(zip("module.prop" to "id=x\n", "../evil.sh" to "rm -rf /"))
    }

    @Test
    fun rejectsTruncatedArchive() {
        val good = zip("module.prop" to "id=x\n", "big.bin" to "A".repeat(50_000))
        val bytes = good.readBytes()
        val broken = tmp.newFile()
        broken.writeBytes(bytes.copyOf(bytes.size / 2))
        try {
            ModuleValidator.validate(broken)
            fail("Expected UiException")
        } catch (_: UiException) {
            // any validation error is fine for a truncated file
        }
    }

    @Test
    fun buildsInstallCommandsPerManager() {
        val zip = "/data/user/0/com.onikiri.open/cache/downloads/mod.zip"
        assertEquals(
            "'/data/adb/ksud' module install '$zip'",
            RootShell.installCommand(DetectedManager(RootManager.KERNELSU, "/data/adb/ksud", null), zip)
        )
        assertEquals(
            "'/data/adb/ap/bin/apd' module install '$zip'",
            RootShell.installCommand(DetectedManager(RootManager.APATCH, "/data/adb/ap/bin/apd", null), zip)
        )
        assertEquals(
            "'magisk' --install-module '$zip'",
            RootShell.installCommand(DetectedManager(RootManager.MAGISK, "magisk", null), zip)
        )
    }

    @Test
    fun quotesPathsWithSingleQuotes() {
        val command = RootShell.installCommand(
            DetectedManager(RootManager.MAGISK, "magisk", null),
            "/tmp/it's.zip"
        )
        assertEquals("'magisk' --install-module '/tmp/it'\\''s.zip'", command)
    }
}
