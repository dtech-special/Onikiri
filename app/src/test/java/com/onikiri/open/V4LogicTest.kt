package com.onikiri.open

import com.onikiri.open.data.InstallState
import com.onikiri.open.data.InstalledModule
import com.onikiri.open.data.ModuleDetails
import com.onikiri.open.data.ReleaseAsset
import com.onikiri.open.data.RepoUrl
import com.onikiri.open.data.compareVersions
import com.onikiri.open.data.defaultAsset
import com.onikiri.open.data.installStateOf
import com.onikiri.open.data.parseInstalledModules
import com.onikiri.open.data.parseRepoIndex
import com.onikiri.open.data.repoNameFromUrl
import com.onikiri.open.data.selectAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V4LogicTest {

    private val module = """{"id": "a", "title": "A", "github_repo": "foo/a", "asset_filter": "Google"}"""

    @Test
    fun repoNameComesFromHeaderComment() {
        assertEquals("Optimize Pack", parseRepoIndex("# Optimize Pack\n$module").name)
        assertEquals("Optimize Pack", parseRepoIndex("\n  # Name: Optimize Pack\n$module").name)
        assertEquals("Optimize Pack", parseRepoIndex("# name = Optimize Pack\n$module").name)
        assertNull(parseRepoIndex(module).name)
        // a comment that is not the first line is only ignored, it is not a name
        assertNull(parseRepoIndex("$module\n# later comment").name)
    }

    @Test
    fun commentLinesAreIgnoredAndAssetFilterIsRead() {
        val parsed = parseRepoIndex("# Pack\n# another comment\n$module\n", "http://x/p.jsonl")
        assertEquals(1, parsed.modules.size)
        assertEquals("Google", parsed.modules[0].assetFilter)
        assertEquals("http://x/p.jsonl", parsed.modules[0].repoUrl)
    }

    @Test
    fun repoNameFallsBackToFileName() {
        assertEquals("Optimize", repoNameFromUrl("https://example.com/files/Optimize.jsonl"))
        assertEquals("My Pack", repoNameFromUrl("http://192.168.1.5:8080/My%20Pack.jsonl"))
        assertEquals("127.0.0.1", repoNameFromUrl("http://127.0.0.1:8000/"))
    }

    @Test
    fun anyHttpServerIsAccepted() {
        assertEquals("http://127.0.0.1:8080/m.jsonl", RepoUrl.normalize("http://127.0.0.1:8080/m.jsonl"))
        assertEquals("http://192.168.0.2/m.jsonl", RepoUrl.normalize("HTTP://192.168.0.2/m.jsonl"))
        assertEquals("https://codeberg.org/u/r/raw/branch/main/m.jsonl", RepoUrl.normalize("https://codeberg.org/u/r/raw/branch/main/m.jsonl"))
        assertNull(RepoUrl.normalize("ftp://example.com/m.jsonl"))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertEquals(1, compareVersions("v1.10", "1.9"))
        assertEquals(-1, compareVersions("2.0", "2.0.1"))
        assertEquals(0, compareVersions("1.2", "v1.2.0"))
        assertEquals(0, compareVersions("beta", "1.0"))
    }

    private fun installed(version: String = "1.0", code: Long = 10) = InstalledModule(
        dir = "a", id = "a", name = "A", version = version, versionCode = code,
        author = "", description = "", disabled = false, removing = false
    )

    private fun details(version: String? = null, remoteCode: Long? = null, zip: Boolean = true) = ModuleDetails(
        author = "x", version = version, releaseName = null, publishedAt = null, releaseNotes = null,
        readme = null,
        assets = if (zip) listOf(ReleaseAsset("m.zip", "https://x/m.zip", 1)) else emptyList(),
        remoteVersionCode = remoteCode
    )

    @Test
    fun installStateIsNotInstalledInstalledOrUpdate() {
        assertEquals(InstallState.NOT_INSTALLED, installStateOf(null, details("v2")))
        assertEquals(InstallState.INSTALLED, installStateOf(installed(), null))
        assertEquals(InstallState.INSTALLED, installStateOf(installed("1.0"), details("v1.0")))
        assertEquals(InstallState.UPDATE, installStateOf(installed("1.0"), details("v1.1")))
        assertEquals(InstallState.UPDATE, installStateOf(installed("1.0", 10), details("v1.0", remoteCode = 11)))
        assertEquals(InstallState.INSTALLED, installStateOf(installed("1.0", 10), details("v1.0", remoteCode = 10)))
        // no downloadable zip -> never offer an update
        assertEquals(InstallState.INSTALLED, installStateOf(installed("1.0"), details("v9.9", zip = false)))
    }

    @Test
    fun picksAssetsAndDefaults() {
        val assets = listOf(
            ReleaseAsset("png.zip", "u1", 1),
            ReleaseAsset("Emojify-Google.zip", "u2", 2),
            ReleaseAsset("source.zip", "u3", 3),
            ReleaseAsset("notes.txt", "u4", 4)
        )
        assertEquals(3, selectAssets(assets, "").size)
        assertEquals(listOf("Emojify-Google.zip"), selectAssets(assets, "google").map { it.name })
        assertEquals(3, selectAssets(assets, "nomatch").size)
        assertEquals("Emojify-Google.zip", defaultAsset(selectAssets(assets, ""))?.name)
        assertEquals("png.zip", defaultAsset(selectAssets(assets, ""), setOf("Emojify-Google.zip", "source.zip"))?.name)
        assertNull(defaultAsset(selectAssets(assets, ""), setOf("png.zip", "Emojify-Google.zip", "source.zip")))
    }

    @Test
    fun parsesInstalledModulesAndFlags() {
        val lines = listOf(
            "@@MODULE|zygisk_x|0|0",
            "id=zygisk_x", "name=Zygisk X", "version=v1.2", "versionCode=12", "author=me", "description=desc",
            "",
            "@@END",
            "@@MODULE|old|1|1",
            "name=Old one",
            "@@END",
            "@@MODULE|../evil|0|0",
            "id=evil",
            "@@END"
        )
        val modules = parseInstalledModules(lines)
        assertEquals(listOf("old", "zygisk_x"), modules.map { it.dir })
        val x = modules.first { it.dir == "zygisk_x" }
        assertEquals("Zygisk X", x.name)
        assertEquals(12L, x.versionCode)
        assertEquals(false, x.disabled)
        val old = modules.first { it.dir == "old" }
        assertEquals("old", old.id)
        assertEquals(true, old.disabled)
        assertEquals(true, old.removing)
    }
}
