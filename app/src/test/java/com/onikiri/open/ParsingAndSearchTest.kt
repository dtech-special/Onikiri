package com.onikiri.open

import com.onikiri.open.data.ModuleStatus
import com.onikiri.open.data.RepoUrl
import com.onikiri.open.data.filterModules
import com.onikiri.open.data.parseModules
import com.onikiri.open.ui.components.MdBlock
import com.onikiri.open.ui.components.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingAndSearchTest {

    private val sample = """
        {"id": "a", "title": "Alpha", "description": "First module", "github_repo": "foo/alpha", "status": "1"}
        {"id": "b", "title": "Beta", "description": "Second", "github_repo": "bar/beta", "status": "3"}
    """.trimIndent()

    @Test
    fun parsesValidLines() {
        val modules = parseModules(sample)
        assertEquals(2, modules.size)
        assertEquals(ModuleStatus.VERIFIED, modules[0].status)
        assertEquals(ModuleStatus.HAZARD, modules[1].status)
        assertEquals("foo", modules[0].owner)
    }

    @Test
    fun skipsBrokenLinesAndDuplicates() {
        val text = """
            {"id": "a", "title": "A", "description": "d", "github_repo": "foo/a", "status": "1"}
            this is not json
            {"id": "a", "title": "Duplicate", "description": "d", "github_repo": "foo/a2", "status": "1"}
            {"title": "No id", "github_repo": "foo/x"}
            {"id": "bad", "github_repo": "../../etc/passwd"}
            {"id": "c", "github_repo": "foo/c"}
        """.trimIndent()
        val modules = parseModules(text)
        assertEquals(listOf("a", "c"), modules.map { it.id })
        assertEquals("A", modules[0].title)
        assertEquals("c", modules[1].title)
        assertEquals(ModuleStatus.UNKNOWN, modules[1].status)
    }

    @Test
    fun searchMatchesTitleDescriptionAndOwnerCaseInsensitively() {
        val modules = parseModules(sample)
        assertEquals(listOf("a"), filterModules(modules, "ALPHA").map { it.id })
        assertEquals(listOf("b"), filterModules(modules, "second").map { it.id })
        assertEquals(listOf("b"), filterModules(modules, "bar").map { it.id })
        assertEquals(2, filterModules(modules, "   ").size)
        assertTrue(filterModules(modules, "alpha second").isEmpty())
    }

    @Test
    fun searchUsesAuthorFromDetails() {
        val modules = parseModules(sample)
        val result = filterModules(modules, "johndoe") { if (it.id == "b") "JohnDoe" else null }
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun normalizesRepositoryUrls() {
        assertEquals(
            "https://raw.githubusercontent.com/u/r/refs/heads/main/x.jsonl",
            RepoUrl.normalize("u/r/refs/heads/main/x.jsonl")
        )
        assertEquals(
            "https://raw.githubusercontent.com/u/r/main/x.jsonl",
            RepoUrl.normalize("https://github.com/u/r/blob/main/x.jsonl")
        )
        assertEquals("https://example.com/a.jsonl", RepoUrl.normalize("  https://example.com/a.jsonl "))
        assertNull(RepoUrl.normalize("http://example.com/a.jsonl"))
        assertNull(RepoUrl.normalize("not a url"))
        assertNull(RepoUrl.normalize(""))
    }

    @Test
    fun parsesMarkdownBlocks() {
        val blocks = parseMarkdownBlocks(
            "# Title\n\nHello <b>world</b> ![img](x.png)\n\n- one\n- two\n\n```\ncode\n```\n"
        )
        assertEquals(MdBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MdBlock.Paragraph("Hello world"), blocks[1])
        assertEquals(MdBlock.Bullet("one", 0), blocks[2])
        assertEquals(MdBlock.Bullet("two", 0), blocks[3])
        assertEquals(MdBlock.Code("code"), blocks[4])
    }
}
