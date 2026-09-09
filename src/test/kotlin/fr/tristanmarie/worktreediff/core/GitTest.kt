package fr.tristanmarie.worktreediff.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Runs against a throwaway repository, so it needs a `git` on the PATH. */
class GitTest {
    private lateinit var dir: Path
    private lateinit var root: String

    private fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(dir.toFile())
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $out" }
    }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("worktree-diff-test")
        root = Git.normalize(dir.toRealPath().toString())
        git("init", "-q", "-b", "main")
        git("config", "user.email", "test@example.com")
        git("config", "user.name", "Test")
        git("config", "commit.gpgsign", "false")
        Files.writeString(dir.resolve("a.txt"), "one\n")
        Files.createDirectories(dir.resolve("openspec/changes/alpha"))
        Files.writeString(dir.resolve("openspec/changes/alpha/tasks.md"), "- [ ] do it\n")
        git("add", ".")
        git("commit", "-q", "-m", "init")
    }

    @After
    fun tearDown() {
        // A linked worktree keeps a lock on Windows for a moment; best effort.
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `worktrees are listed with the main one first`() {
        val worktrees = Git.listWorktrees(root)
        assertEquals(1, worktrees.size)
        assertTrue(worktrees[0].isMain)
        assertEquals("main", worktrees[0].branch)
        assertEquals(root, worktrees[0].path)
    }

    @Test
    fun `a branch's contribution is read against the merge base`() {
        git("checkout", "-q", "-b", "feature/x")
        Files.writeString(dir.resolve("b.txt"), "two\n")
        Files.writeString(dir.resolve("a.txt"), "one\nmore\n")
        git("add", ".")
        git("commit", "-q", "-m", "feature")
        // main moves on; the diff must not show that.
        git("checkout", "-q", "main")
        Files.writeString(dir.resolve("c.txt"), "three\n")
        git("add", ".")
        git("commit", "-q", "-m", "main moves")
        git("checkout", "-q", "feature/x")

        val diff = Git.diffAgainstBase(root, "main")
        assertEquals(listOf("a.txt" to ChangeKind.MODIFIED, "b.txt" to ChangeKind.ADDED), diff.map { it.path to it.kind })
        val counts = Git.aheadBehind(root, "main")
        assertEquals(AheadBehind(ahead = 1, behind = 1), counts)
        assertNotNull(Git.mergeBase(root, "main"))
        assertEquals("one\n", Git.showFile(root, Git.mergeBase(root, "main")!!, "a.txt"))
        assertEquals("", Git.showFile(root, "main", "does-not-exist.txt"))
    }

    @Test
    fun `uncommitted changes cover staged, unstaged and untracked files`() {
        Files.writeString(dir.resolve("a.txt"), "changed\n")
        Files.writeString(dir.resolve("new.txt"), "new\n")
        Files.writeString(dir.resolve("staged.txt"), "staged\n")
        git("add", "staged.txt")
        val changes = Git.uncommittedChanges(root).associate { it.path to it.kind }
        assertEquals(ChangeKind.MODIFIED, changes["a.txt"])
        assertEquals(ChangeKind.UNTRACKED, changes["new.txt"])
        assertEquals(ChangeKind.ADDED, changes["staged.txt"])
    }

    @Test
    fun `tracked paths tell a draft from a committed change`() {
        Files.createDirectories(dir.resolve("openspec/changes/draft"))
        Files.writeString(dir.resolve("openspec/changes/draft/tasks.md"), "- [ ] x\n")
        val tracked = Git.trackedUnder(root, "openspec/changes")
        assertTrue("openspec/changes/alpha/tasks.md" in tracked)
        assertTrue(tracked.none { it.contains("/draft/") })

        val board = buildBoard(Git.listWorktrees(root), "main")
        val main = board.single()
        assertEquals(mapOf("alpha" to false, "draft" to true), main.changes.associate { it.name to it.draft })
    }

    @Test
    fun `refs and probes answer without throwing`() {
        assertTrue("main" in Git.listRefs(root))
        assertNull(Git.resolveRef(root, "nope"))
        assertNull(Git.mergeBase(root, "nope"))
        assertNull(Git.aheadBehind(root, "nope"))
        assertTrue(Git.branchExists(root, "main"))
        assertTrue(Git.isAncestor(root, "main", "HEAD"))
        assertEquals(root, Git.repoRoot(root))
        assertEquals("$root/.git", Git.commonGitDir(root))
    }

    @Test
    fun `a linked worktree is created on a new branch`() {
        val target = Git.normalize(dir.resolveSibling(dir.fileName.toString() + "-wt").toString())
        try {
            Git.addWorktree(root, target, "feature/wt", "main")
            val worktrees = Git.listWorktrees(root)
            assertEquals(2, worktrees.size)
            assertEquals("feature/wt", worktrees[1].branch)
            assertTrue(worktrees[1].path.endsWith("-wt"))
        } finally {
            try {
                git("worktree", "remove", "--force", target)
            } catch (_: IllegalStateException) {
            }
        }
    }
}
