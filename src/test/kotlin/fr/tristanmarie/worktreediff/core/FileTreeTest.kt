package fr.tristanmarie.worktreediff.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTreeTest {
    private val worktree = Worktree("/repo", "main", "abc", detached = false, locked = false, prunable = false, isMain = true)

    private val files = listOf(
        ChangedFile("front-end/src/js/pages/a.ts", ChangeKind.MODIFIED),
        ChangedFile("front-end/src/js/pages/b.ts", ChangeKind.ADDED),
        ChangedFile("docs/readme.md", ChangeKind.MODIFIED),
        ChangedFile("CLAUDE.md", ChangeKind.MODIFIED),
    )

    @Test
    fun `compact mode merges single-child folder chains`() {
        val nodes = buildFileNodes(worktree, files, "sha", ViewMode.TREE, compact = true)
        val dirs = nodes.filterIsInstance<DirNode>()
        assertEquals(listOf("docs", "front-end/src/js/pages"), dirs.map { it.label })
        assertEquals("front-end/src/js/pages", dirs[1].relPath)
        assertEquals(listOf("a.ts", "b.ts"), dirs[1].children.filterIsInstance<FileNode>().map { it.file.path.substringAfterLast('/') })
        // Files after folders, at the root too.
        assertTrue(nodes.last() is FileNode)
        assertEquals("CLAUDE.md", (nodes.last() as FileNode).file.path)
    }

    @Test
    fun `without compacting every level is a node`() {
        val nodes = buildFileNodes(worktree, files, "sha", ViewMode.TREE, compact = false)
        val frontEnd = nodes.filterIsInstance<DirNode>().first { it.label == "front-end" }
        val src = frontEnd.children.single() as DirNode
        assertEquals("src", src.label)
        assertEquals("front-end/src", src.relPath)
    }

    @Test
    fun `list mode is flat with the folder as a hint`() {
        val nodes = buildFileNodes(worktree, files, "sha", ViewMode.LIST, compact = true)
        assertEquals(4, nodes.size)
        assertTrue(nodes.all { it is FileNode })
        assertEquals("front-end/src/js/pages", (nodes[0] as FileNode).folderHint)
        assertEquals("", (nodes[3] as FileNode).folderHint)
    }

    @Test
    fun `touched change names ignore the archive and files outside openspec`() {
        val names = touchedChangeNames(
            listOf(
                "openspec/changes/alpha/tasks.md",
                "openspec/changes/alpha/proposal.md",
                "openspec/changes/archive/old/tasks.md",
                "openspec/changes/beta/specs/x.md",
                "src/main.kt",
            ),
        )
        assertEquals(setOf("alpha", "beta"), names)
    }
}
