package com.onikiri.open.root

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

/** Thin libsu wrapper: root detection, streamed module installation and reboots. */
object RootShell {

    private val DETECT_SCRIPT = listOf(
        "for p in /data/adb/ksud /data/adb/ksu/bin/ksud; do",
        "  if [ -x \"\$p\" ]; then echo \"KERNELSU|\$p|\$(\$p --version 2>/dev/null | head -n 1)\"; break; fi",
        "done",
        "for p in /data/adb/ap/bin/apd /data/adb/apd; do",
        "  if [ -x \"\$p\" ]; then echo \"APATCH|\$p|\$(\$p --version 2>/dev/null | head -n 1)\"; break; fi",
        "done",
        "m=\$(command -v magisk 2>/dev/null)",
        "[ -z \"\$m\" ] && [ -x /data/adb/magisk/magisk ] && m=/data/adb/magisk/magisk",
        "if [ -n \"\$m\" ]; then echo \"MAGISK|\$m|\$(\$m -v 2>/dev/null | head -n 1)\"; fi"
    ).joinToString("\n")

    private val ANSI = Regex("\u001B\\[[0-9;?]*[A-Za-z]")

    /** Triggers the root prompt of the manager (first call) and reports whether root was granted. */
    suspend fun detect(): RootStatus = withContext(Dispatchers.IO) {
        val shell = Shell.getShell()
        if (!shell.isRoot) return@withContext RootStatus(granted = false, managers = emptyList())
        val result = Shell.cmd(DETECT_SCRIPT).exec()
        val managers = result.out.mapNotNull { parseDetectLine(it) }
        RootStatus(granted = true, managers = managers)
    }

    private fun parseDetectLine(line: String): DetectedManager? {
        val parts = line.trim().split('|', limit = 3)
        if (parts.size < 2) return null
        val manager = RootManager.entries.firstOrNull { it.name == parts[0] } ?: return null
        val binary = parts[1].trim()
        if (binary.isEmpty()) return null
        val version = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
        return DetectedManager(manager, binary, version)
    }

    fun installCommand(manager: DetectedManager, zipPath: String): String {
        val bin = quote(manager.binary)
        val zip = quote(zipPath)
        return when (manager.manager) {
            RootManager.MAGISK -> "$bin --install-module $zip"
            RootManager.KERNELSU -> "$bin module install $zip"
            RootManager.APATCH -> "$bin module install $zip"
        }
    }

    /**
     * Runs [command] as root and reports every output line (stdout and stderr merged) in real
     * time through [onLine]. Returns the exit code.
     */
    suspend fun runStreaming(command: String, onLine: (String) -> Unit): Int =
        withContext(Dispatchers.IO) {
            val executor = Executor { it.run() }
            val sink = object : CallbackList<String>(executor) {
                override fun onAddElement(e: String?) {
                    if (e != null) onLine(ANSI.replace(e, ""))
                }
            }
            Shell.cmd(command).to(sink).exec().code
        }

    suspend fun reboot() = withContext(Dispatchers.IO) {
        Shell.cmd("svc power reboot || reboot").submit()
        Unit
    }

    /** Restarts the Android framework (zygote) without a full reboot. */
    suspend fun softReboot() = withContext(Dispatchers.IO) {
        Shell.cmd("setprop ctl.restart zygote || { stop; start; }").submit()
        Unit
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
