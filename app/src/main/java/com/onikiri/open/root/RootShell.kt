package com.onikiri.open.root

import com.onikiri.open.data.InstalledModule
import com.onikiri.open.data.MODULE_DIR_REGEX
import com.onikiri.open.data.parseInstalledModules
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

    /**
     * Triggers the root prompt of the manager and reports whether root was granted.
     *
     * libsu caches the first shell it opens. If that shell was opened before the user granted root
     * in KernelSU / APatch it stays non-root forever, so a re-check must close it first
     * ([fresh] = true) to make the next [Shell.getShell] ask the manager again.
     */
    suspend fun detect(fresh: Boolean = false): RootStatus = withContext(Dispatchers.IO) {
        if (fresh) Shell.getCachedShell()?.close()
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

    private val SCAN_SCRIPT = listOf(
        "for d in /data/adb/modules/*/; do",
        "  [ -f \"§{d}module.prop\" ] || continue",
        "  n=§(basename \"§d\")",
        "  if [ -f \"§{d}disable\" ]; then dis=1; else dis=0; fi",
        "  if [ -f \"§{d}remove\" ]; then rem=1; else rem=0; fi",
        "  echo \"@@MODULE|§n|§dis|§rem\"",
        "  cat \"§{d}module.prop\"",
        "  echo",
        "  echo \"@@END\"",
        "done"
    ).joinToString("\n").replace('§', '$')

    /** Every module under /data/adb/modules, including ones that are not in any repository. Null without root. */
    suspend fun listInstalledModules(): List<InstalledModule>? = withContext(Dispatchers.IO) {
        if (!Shell.getShell().isRoot) return@withContext null
        parseInstalledModules(Shell.cmd(SCAN_SCRIPT).exec().out)
    }

    /** A `disable` file turns a module off at the next boot; removing it turns it back on. */
    suspend fun setModuleDisabled(dir: String, disabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!MODULE_DIR_REGEX.matches(dir)) return@withContext false
            val path = "/data/adb/modules/$dir/disable"
            Shell.cmd(if (disabled) "touch '$path'" else "rm -f '$path'").exec().isSuccess
        }

    /** A `remove` file makes the manager delete the module at the next boot. */
    suspend fun setModuleRemoved(dir: String, removed: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!MODULE_DIR_REGEX.matches(dir)) return@withContext false
            val path = "/data/adb/modules/$dir/remove"
            Shell.cmd(if (removed) "touch '$path'" else "rm -f '$path'").exec().isSuccess
        }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
