package fr.tristanmarie.worktreediff.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import fr.tristanmarie.worktreediff.core.Git
import fr.tristanmarie.worktreediff.core.Prompts

/**
 * The commands behind the toolbar buttons and the page buttons alike, so that a page and an
 * action reach the same code. Every entry point is safe to call from the UI thread: the git
 * work moves to a pooled thread and comes back for dialogs.
 */
object Commands {
    /** Hands the framing of a new piece of work to Claude, against the main checkout. */
    fun startWork(project: Project) {
        val service = BoardService.getInstance(project)
        bg {
            val main = service.ensureBoard().find { it.isMain }
            if (main == null) {
                notifyWarning(project, "No main working tree in this project to frame the work in.")
                return@bg
            }
            NewWork.startNewWork(project, main, service.base, service.plugin())
        }
    }

    /**
     * Gives a drafted change its branch, its worktree and its first commit. Without a name it
     * asks which draft, because that list is short by nature.
     */
    fun createWorktree(project: Project, name: String? = null) {
        val service = BoardService.getInstance(project)
        bg {
            val main = service.ensureBoard().find { it.isMain } ?: return@bg
            if (name == null) {
                val drafts = main.changes.filter { it.draft }
                if (drafts.isEmpty()) {
                    notify(project, "No drafted change waiting for a worktree.")
                    return@bg
                }
                ui {
                    JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(drafts.map { it.name })
                        .setTitle("Create a Worktree For...")
                        .setItemChosenCallback { createWorktree(project, it) }
                        .createPopup()
                        .showCenteredInCurrentWindow(project)
                }
                return@bg
            }
            val change = main.changes.find { it.name == name } ?: return@bg
            NewWork.createWorktreeForChange(project, main, change, service.base, service.plugin())
        }
    }

    /**
     * Reopens the launch requests nobody answered. Says so when there are none, because silence
     * from a command the user just ran reads as a failure.
     */
    fun reviewRequests(project: Project) {
        val service = BoardService.getInstance(project)
        bg {
            val found = service.roots().sumOf { AgentRequests.sweep(project, it) }
            if (found == 0) notify(project, "No launch request is waiting for an answer.")
        }
    }

    /** Opens a Claude session on a worktree: on one of its changes, or on the branch itself. */
    fun askClaude(project: Project, worktreePath: String, change: String?, intent: String?) {
        val service = BoardService.getInstance(project)
        bg {
            val worktree = service.ensureBoard().find { it.path == worktreePath } ?: return@bg
            val base = service.base
            val plugin = service.plugin()
            val language = NewWork.language(project)
            // No change name means the button was the worktree-level one.
            if (change == null) {
                ClaudeLauncher.launch(project, Prompts.worktreePrompt(worktree, base, language), worktree.path)
                return@bg
            }
            val found = worktree.changes.find { it.name == change } ?: return@bg
            val main = service.lastBoard.find { it.isMain }
            val prompt = if (intent == "archive") {
                Prompts.archivePrompt(worktree, found, base, plugin, language)
            } else {
                Prompts.continuePrompt(worktree, found, base, plugin, language, main?.let { NewWork.requestDirFor(project, it.path) })
            }
            ClaudeLauncher.launch(project, prompt, worktree.path)
        }
    }

    /** Lets the user pick the ref every worktree is compared against. */
    fun changeBase(project: Project) {
        val service = BoardService.getInstance(project)
        val settings = WorktreeDiffSettings.getInstance(project)
        bg {
            val root = service.roots().firstOrNull() ?: return@bg
            val current = settings.base
            val refs = Git.listRefs(root).ifEmpty { listOf(current) }
            ui {
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(refs)
                    .setTitle("Compare Every Worktree Against...")
                    .setRenderer(com.intellij.ui.SimpleListCellRenderer.create { label, value, _ ->
                        label.text = if (value == current) "$value  (current)" else value
                    })
                    .setItemChosenCallback { picked ->
                        settings.state.base = picked
                        service.refreshAll()
                    }
                    .createPopup()
                    .showCenteredInCurrentWindow(project)
            }
        }
    }
}
