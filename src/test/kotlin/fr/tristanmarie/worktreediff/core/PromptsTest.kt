package fr.tristanmarie.worktreediff.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    private val change = AriaChange(
        name = "alpha",
        dir = "C:/repo-wt-alpha/openspec/changes/alpha",
        sections = listOf(
            AriaSection("Setup", mutableListOf(AriaTask(true, "done one", 2), AriaTask(false, "todo two", 3))),
        ),
        done = 1,
        total = 2,
        artifacts = listOf("proposal.md", "tasks.md"),
        execMode = "MEDIUM — stop after each task",
        readyToArchive = false,
    )

    private val worktree = BoardWorktree(
        name = "repo-wt-alpha", path = "C:/repo-wt-alpha", branch = "feature/alpha", isMain = false, ahead = 3, behind = 1, filesChanged = 7,
        changes = listOf(change),
    )

    @Test
    fun `the continue prompt names the place, the task and the queue`() {
        val prompt = Prompts.continuePrompt(worktree, change, "origin/main", AriaPlugin(true, "1.0"), PromptLanguage.EN, "C:/repo/.worktree-diff/requests")
        assertTrue(prompt.startsWith("Continue the OpenSpec change \"alpha\"."))
        assertTrue(prompt.contains("Worktree: C:/repo-wt-alpha"))
        assertTrue(prompt.contains("Position: 3 ahead / 1 behind origin/main"))
        assertTrue(prompt.contains("- [Setup] todo two"))
        assertFalse(prompt.contains("done one"))
        assertTrue(prompt.contains("Exec mode: MEDIUM"))
        assertTrue(prompt.contains("C:/repo/.worktree-diff/requests"))
        assertTrue(prompt.endsWith("aria:opsx:apply workflow."))
        assertTrue(prompt.contains("No session is recorded as watching this work"))
    }

    @Test
    fun `a spec session switches the observability block`() {
        val prompt = Prompts.continuePrompt(worktree, change.copy(specSession = "claude-x [ref 4]"), "main", AriaPlugin(false), PromptLanguage.FR)
        assertTrue(prompt.startsWith("Reprends le change OpenSpec « alpha »."))
        assertTrue(prompt.contains("écris à claude-x [ref 4]"))
        assertFalse(prompt.contains("Aucune session n'est enregistrée"))
        assertTrue(prompt.endsWith("en cochant les tâches au fur et à mesure."))
    }

    @Test
    fun `the location frame goes above an agent-written prompt`() {
        val frame = Prompts.locationFrame(worktree, change.copy(specSession = "spec-1"), "main", PromptLanguage.EN)
        val lines = frame.lines()
        assertEquals("Worktree: C:/repo-wt-alpha", lines[0])
        assertTrue(frame.contains("Report to spec-1"))
        assertTrue(frame.endsWith("Work in that worktree, not in the main checkout.\n\n"))
    }

    @Test
    fun `the new work prompt lists the active changes and the button`() {
        val prompt = Prompts.newWorkPrompt("C:/repo", "main", listOf("alpha", "beta"), AriaPlugin(false), "Create worktree", PromptLanguage.EN)
        assertTrue(prompt.contains("Active changes, do not duplicate one of them: alpha, beta"))
        assertTrue(prompt.contains("\"Create worktree\" button"))
        assertTrue(prompt.contains("openspec new change"))
        assertFalse(prompt.contains(".worktree-diff/requests"))
    }

    @Test
    fun `the archive and worktree prompts are short`() {
        val archive = Prompts.archivePrompt(worktree, change, "main", AriaPlugin(true), PromptLanguage.EN)
        assertTrue(archive.endsWith("Then use the aria:opsx:archive workflow."))
        val plain = Prompts.worktreePrompt(worktree.copy(changes = emptyList()), "main", PromptLanguage.EN)
        assertTrue(plain.contains("No OpenSpec change on this branch."))
    }
}
