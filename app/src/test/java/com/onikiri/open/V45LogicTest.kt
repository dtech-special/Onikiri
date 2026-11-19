package com.onikiri.open

import com.onikiri.open.data.InstallState
import com.onikiri.open.data.InstalledModule
import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.data.RootModule
import com.onikiri.open.data.cleanVersion
import com.onikiri.open.data.compareVersions
import com.onikiri.open.data.installStateOf
import com.onikiri.open.data.matchInstalled
import com.onikiri.open.data.normalizeKey
import com.onikiri.open.data.parseInstalledModules
import com.onikiri.open.data.versionParts
import org.junit.Assert.assertEquals
import org.junit.Test

class V45LogicTest {

    private fun catalog(id: String, title: String = id) =
        RootModule(id, title, "", "o/$id", ModuleStatus.VERIFIED)

    private fun installed(dir: String, id: String = dir, name: String = dir, catalogId: String = "", version: String = "1.0") =
        InstalledModule(dir, id, name, version, 1, "", "", false, false, catalogId)

    @Test
    fun versionIsCleaned() {
        assertEquals("1.5.3", cleanVersion("1.5.3 (880-22a1bf5-release)"))
        assertEquals("2.0", cleanVersion("v2.0-beta"))
        assertEquals("2.0", cleanVersion("V2.0-release"))
        assertEquals(listOf(1L, 5L, 3L), versionParts("1.5.3 (880-22a1bf5-release)"))
        assertEquals(0, compareVersions("1.5.3 (880-22a1bf5-release)", "v1.5.3"))
        assertEquals(1, compareVersions("v1.5.4", "1.5.3 (880-22a1bf5-release)"))
    }

    @Test
    fun latestTagMakesUpdate() {
        val m = installed("a", version = "1.5.3 (880-x-release)")
        assertEquals(InstallState.UPDATE, installStateOf(m, null, "v1.6.0"))
        assertEquals(InstallState.INSTALLED, installStateOf(m, null, "v1.5.3"))
        assertEquals(InstallState.INSTALLED, installStateOf(m, null, null))
    }

    @Test
    fun matchesByCatalogIdFirstThenByNormalizedNames() {
        val cat = listOf(catalog("zygisk-x", "Zygisk X"), catalog("emoji", "Emoji Pack"))
        val list = listOf(
            installed("dir1", catalogId = "emoji"),
            installed("zygisk_x", id = "zygisk_x", name = "Zygisk-X")
        )
        val map = matchInstalled(cat, list)
        assertEquals("dir1", map["emoji"]?.dir)
        assertEquals("zygisk_x", map["zygisk-x"]?.dir)
        assertEquals("zygiskx", normalizeKey("  Zygisk-X "))
    }

    @Test
    fun parsesDataOnikiri() {
        val lines = listOf(
            "@@MODULE|m|0|0", "id=m", "name=M", "version=1", "", "@@DATA",
            "catalog_id=cat", "github_repo=o/r", "", "@@END"
        )
        val m = parseInstalledModules(lines).single()
        assertEquals("cat", m.catalogId)
        assertEquals("o/r", m.githubRepo)
        assertEquals("M", m.name)
    }
}
