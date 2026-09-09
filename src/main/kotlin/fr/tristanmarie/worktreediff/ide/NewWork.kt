package fr.tristanmarie.worktreediff.ide

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import fr.tristanmarie.worktreediff.core.AriaChange
import fr.tristanmarie.worktreediff.core.AriaPlugin
import fr.tristanmarie.worktreediff.core.BoardWorktree
import fr.tristanmarie.worktreediff.core.Git
import fr.tristanmarie.worktreediff.core.Prompts
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.name

/**
 * The label of the button that turns a drafted change into a worktree. It is quoted back to
 * Claude in the framing prompt, so the session can point at the same words the user sees.
 */
const val CREATE_WORKTREE_LABEL = "Create worktree"

private const val CHANGES = "openspec/changes"

/** Where an agent drops a launch request, relative to the main checkout. */
const val REQUEST_DIR = ".worktree-diff/requests"

/**
 * Everything about a future worktree that can be known before touching git: where it goes,
 * which branch it takes, what it forks from, and the board entry it will have once it exists.
 */
class WorktreePlan(
    val root: String,
    val target: String,
    val branch: String,
    val startPoint: String,
    val change: AriaChange,
    /** The board entry the worktree will have once [NewWork.runPlan] has run. */
    val worktree: BoardWorktree,
)

object NewWork {
    /** Expands the `${repo}` and `${change}` placeholders of the naming patterns. */
    private fun expand(pattern: String, repo: String, change: String): String =
        pattern.replace("\${repo}", repo).replace("\${change}", change)

    /**
     * The queue path to advertise in the framing prompt, or null when launch autonomy is off.
     * Advertising it unconditionally would be worse than not having it: a session would write
     * requests into a directory nobody watches and wait for an answer that never comes.
     */
    fun requestDirFor(project: Project, root: String): String? =
        if (WorktreeDiffSettings.getInstance(project).launchEnabled) "$root/$REQUEST_DIR" else null

    fun language(project: Project) = WorktreeDiffSettings.getInstance(project).state.promptLanguage

    /**
     * Step one: hand Claude the framing of a new piece of work. The session interviews the user
     * and writes an OpenSpec change in the main checkout, and is told not to branch and not to
     * commit; [createWorktreeForChange] does that, after a human has read the proposal.
     */
    fun startNewWork(project: Project, main: BoardWorktree, base: String, plugin: AriaPlugin) {
        val root = Git.repoRoot(main.path) ?: main.path
        val prompt = Prompts.newWorkPrompt(
            root,
            base,
            main.changes.map { it.name },
            plugin,
            CREATE_WORKTREE_LABEL,
            language(project),
            requestDirFor(project, root),
        )
        ClaudeLauncher.launch(project, prompt, root)
    }

    /** Where a change's worktree would go, and what its branch would be called by default. */
    fun planWorktree(project: Project, main: BoardWorktree, change: AriaChange, base: String, branch: String? = null): WorktreePlan {
        val settings = WorktreeDiffSettings.getInstance(project)
        val root = Git.repoRoot(main.path) ?: main.path
        val repo = Paths.get(root).name
        val target = Git.normalize(
            Paths.get(root).resolve(expand(settings.worktreePattern, repo, change.name)).normalize().toString(),
        )
        val name = (branch ?: expand(settings.branchPattern, repo, change.name)).trim()

        // Forking from the base rather than from whatever the main checkout currently has: the
        // board compares against the base, and a branch started elsewhere reads as behind from
        // its first commit.
        val startPoint = if (Git.resolveRef(root, base) != null) base else "HEAD"

        return WorktreePlan(
            root = root,
            target = target,
            branch = name,
            startPoint = startPoint,
            change = change,
            worktree = BoardWorktree(
                name = Paths.get(target).name,
                path = target,
                branch = name,
                isMain = false,
                ahead = if (change.draft) 1 else 0,
                hasAria = "aria-meta.md" in change.artifacts,
                ariaModes = listOfNotNull(change.execModeLabel),
            ),
        )
    }

    /** The prompt a session opened on a planned worktree gets when nobody wrote a better one. */
    fun plannedPrompt(project: Project, plan: WorktreePlan, base: String, plugin: AriaPlugin): String {
        val moved = plan.change.copy(dir = "${plan.target}/$CHANGES/${plan.change.name}", draft = false)
        return Prompts.continuePrompt(plan.worktree, moved, base, plugin, language(project), requestDirFor(project, plan.root))
    }

    /** True when nothing occupies the planned directory yet; complains to the user when it does. */
    fun targetIsFree(project: Project, plan: WorktreePlan): Boolean {
        if (!exists(plan.target)) return true
        notifyError(project, "${plan.target} already exists. Move it aside, or change the worktree pattern in the settings.")
        return false
    }

    /** Rejects a branch name before anything is created. Returns the complaint, or null. */
    fun branchProblem(root: String, name: String): String? {
        if (name.isEmpty()) return "A branch name is required"
        if (Regex("\\s").containsMatchIn(name)) return "A branch name cannot contain spaces"
        return if (Git.branchExists(root, name)) "Branch \"$name\" already exists" else null
    }

    /**
     * The git half, and the only half that touches the repository: worktree, directory move,
     * first commit. Synchronous; call off the UI thread. The commit is not incidental: the board
     * lists what a branch *contributes*, so a change that stayed uncommitted would leave the new
     * worktree looking empty.
     */
    fun runPlan(project: Project, plan: WorktreePlan): Boolean =
        try {
            Git.addWorktree(plan.root, plan.target, plan.branch, plan.startPoint)
            if (plan.change.draft) {
                moveChange(plan.root, plan.target, plan.change.name)
                Git.commitPaths(plan.target, listOf("$CHANGES/${plan.change.name}"), commitMessage(plan.target, plan.change.name))
            }
            true
        } catch (e: Exception) {
            notifyError(project, "Could not create the worktree: ${message(e)}")
            false
        }

    /**
     * Step two: give a drafted change a branch and a worktree of its own. This is the human
     * path, the tool window button. Runs on the UI thread; the git work goes to a background
     * task.
     */
    fun createWorktreeForChange(project: Project, main: BoardWorktree, change: AriaChange, base: String, plugin: AriaPlugin) {
        bg {
            val first = planWorktree(project, main, change, base)
            if (!targetIsFree(project, first)) return@bg
            val root = first.root
            ui {
                val branch = Messages.showInputDialog(
                    project,
                    "Directory: ${first.target}",
                    "New Worktree for \"${change.name}\"",
                    null,
                    first.branch,
                    object : InputValidatorEx {
                        override fun getErrorText(inputString: String?): String? = branchProblem(root, (inputString ?: "").trim())
                        override fun checkInput(inputString: String?): Boolean = getErrorText(inputString) == null
                        override fun canClose(inputString: String?): Boolean = checkInput(inputString)
                    },
                ) ?: return@ui
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Creating ${Paths.get(first.target).name}…", false) {
                    var created = false
                    lateinit var plan: WorktreePlan
                    override fun run(indicator: ProgressIndicator) {
                        plan = planWorktree(project, main, change, base, branch)
                        created = runPlan(project, plan)
                    }

                    override fun onSuccess() {
                        BoardService.getInstance(project).refreshAll()
                        if (created) announce(project, plan, base, plugin)
                    }
                })
            }
        }
    }

    /**
     * Moves the drafted change out of the main checkout and into the fresh worktree. It is
     * untracked there, so this is a plain directory move. A move fails across volumes, which is
     * not exotic when worktrees live on another disk, so a copy stands behind it.
     */
    private fun moveChange(root: String, target: String, name: String) {
        val from = Paths.get(root, CHANGES, name)
        val to = Paths.get(target, CHANGES, name)
        Files.createDirectories(to.parent)
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            FileUtil.copyDir(from.toFile(), to.toFile())
            FileUtil.delete(from.toFile())
        }
    }

    /** The first heading of proposal.md, when it has one: a better subject than the slug alone. */
    private fun commitMessage(target: String, name: String): String {
        val proposal = try {
            Files.readString(Paths.get(target, CHANGES, name, "proposal.md"))
        } catch (_: IOException) {
            ""
        }
        val title = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(proposal)?.groupValues?.get(1)?.trim() ?: ""
        return if (title.isNotEmpty()) "docs(openspec): propose $name\n\n$title" else "docs(openspec): propose $name"
    }

    /** Offers the two things anyone wants next: a session in the new worktree, or the folder. */
    private fun announce(project: Project, plan: WorktreePlan, base: String, plugin: AriaPlugin) {
        val note = if (plan.change.draft) "" else " The change was already committed on the base, so nothing was moved."
        notify(
            project,
            "${Paths.get(plan.target).name} created on ${plan.branch}, from ${plan.startPoint}.$note",
            com.intellij.notification.NotificationType.INFORMATION,
            "Open Claude here" to { bg { ClaudeLauncher.launch(project, plannedPrompt(project, plan, base, plugin), plan.target) } },
            "Open as project" to { openAsProject(plan.target) },
        )
    }

    /** IntelliJ has no multi-root workspace; the nearest thing is the worktree in its own window. */
    fun openAsProject(target: String) {
        ui {
            ProjectUtil.openOrImport(Path.of(target), OpenProjectTask { forceOpenInNewFrame = true })
        }
    }
}
