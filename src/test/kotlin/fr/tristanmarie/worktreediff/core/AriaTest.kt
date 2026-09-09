package fr.tristanmarie.worktreediff.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AriaTest {
    @Test
    fun `tasks are grouped by section and continuation lines are joined`() {
        val md = """
            # Tasks
            ## 1. Setup
            - [x] Create the **project**
            - [ ] Write the `README` that explains
              what the plugin does

            ## 2. Build
            - [ ] Compile
        """.trimIndent()
        val sections = parseTasks(md)
        assertEquals(listOf("1. Setup", "2. Build"), sections.map { it.title })
        assertEquals(2, sections[0].tasks.size)
        assertTrue(sections[0].tasks[0].done)
        assertEquals("Create the project", sections[0].tasks[0].label)
        assertEquals("Write the README that explains what the plugin does", sections[0].tasks[1].label)
        assertEquals(3, sections[0].tasks[1].line)
        assertEquals(1, sections[1].tasks.size)
    }

    @Test
    fun `tasks before any heading land in a default section`() {
        val sections = parseTasks("- [ ] one\n- [X] two\n")
        assertEquals("Tasks", sections.single().title)
        assertEquals(listOf(false, true), sections.single().tasks.map { it.done })
    }

    @Test
    fun `meta table yields the classification and the exec mode token`() {
        val md = """
            | **Impact level** | high |
            | **Exec mode** | MEDIUM — stop after each task for review |
            | **Baseline** | 1296 tests, 0 failures |
            | **Spec session** | claude-abc [ref 12] — historical trace, stale by construction |
            | Simplify | done |
        """.trimIndent()
        val meta = parseMeta(md)
        assertEquals("high", meta.impactLevel)
        assertEquals("MEDIUM", meta.execModeLabel)
        assertEquals("1296 tests, 0 failures", meta.baseline)
        assertEquals("claude-abc [ref 12]", meta.specSession)
        assertEquals("done", meta.simplify)
    }

    @Test
    fun `a spec session cell that disowns itself is not an address`() {
        assertNull(parseMeta("| **Spec session** | no reliable address - do not write to a name read here |").specSession)
        assertNull(parseMeta("| **Spec session** | ⚠ stale |").specSession)
        assertNull(parseMeta("| **Spec session** | --- |").specSession)
    }

    @Test
    fun `markdown decorations are stripped`() {
        assertEquals("a b c", plainMarkdown("**a** `b` [c](http://x)"))
        assertEquals("gone", plainMarkdown("~~gone~~"))
    }

    @Test
    fun `learnings are parsed and sorted most recent first`() {
        val md = """
            ### 2026-09-01 — First
            **Context:** ctx one
            **Learning:** learned one
            **Tags:** a, b
            ### 2026-09-03 — Second
            **Learning:** learned two
            ### Undated
            **Tags:** x
        """.trimIndent()
        val learnings = parseLearnings(md)
        assertEquals(listOf("Second", "First", "Undated"), learnings.map { it.title })
        assertEquals(listOf("a", "b"), learnings[1].tags)
        assertEquals("ctx one", learnings[1].context)
        assertNull(learnings[2].date)
    }

    @Test
    fun `memory index separates files from external references`() {
        val md = """
            Last updated: 2026-09-05
            ## Files
            | `note.md` | A note |
            ## External References
            | `C:/elsewhere/x.md` | Somewhere else |
        """.trimIndent()
        val index = parseMemoryIndex(md)
        assertEquals("2026-09-05", index.updated)
        assertEquals(listOf("note.md" to "A note"), index.entries)
        assertEquals("C:/elsewhere/x.md", index.externals.single().location)
        assertFalse(index.externals.single().description.isEmpty())
    }
}
