package fr.tristanmarie.worktreediff.ide

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.Alarm
import fr.tristanmarie.worktreediff.core.AriaPlugin
import fr.tristanmarie.worktreediff.core.BoardWorktree
import fr.tristanmarie.worktreediff.core.Git
import fr.tristanmarie.worktreediff.core.Worktree
import fr.tristanmarie.worktreediff.core.buildBoard
import fr.tristanmarie.worktreediff.core.collectAriaInfo
import fr.tristanmarie.worktreediff.core.detectAriaPlugin
import java.util.concurrent.CopyOnWriteArrayList

/** What a surface showing the board receives on every refresh. */
class BoardState(
    val board: List<BoardWorktree>,
    val base: String,
    val plugin: AriaPlugin,
    val aria: AriaSummary,
    val requests: List<PendingRequest>,
)

/** A compact summary for the aria card; the full picture lives in the Aria tab. */
class AriaSummary(
    val installed: Boolean,
    val version: String?,
    val skills: Int,
    val learnings: Int,
    val projects: Int,
    val notes: Int,
)

/** What the launch queue needs from the board to plan a launch. */
class LaunchContext(val board: List<BoardWorktree>, val base: String, val plugin: AriaPlugin)

fun interface BoardListener {
    fun boardChanged(state: BoardState)
}

/**
 * Owns the board: one entry per worktree with the OpenSpec changes it carries. Rebuilt off the
 * UI thread on a debounced refresh and pushed to every open surface: the tool window's
 * overview, and every detail tab.
 */
@Service(Service.Level.PROJECT)
class BoardService(private val project: Project) : Disposable {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val listeners = CopyOnWriteArrayList<BoardListener>()

    @Volatile
    private var plugin: AriaPlugin? = null

    @Volatile
    var lastBoard: List<BoardWorktree> = emptyList()
        private set

    @Volatile
    var lastState: BoardState? = null
        private set

    /** The tree of changed files, registered by the tool window when it opens. */
    @Volatile
    var worktreesPanel: WorktreesPanel? = null

    val gson: Gson = Gson()

    val base: String get() = WorktreeDiffSettings.getInstance(project).base

    fun addListener(listener: BoardListener, parent: Disposable) {
        listeners.add(listener)
        com.intellij.openapi.util.Disposer.register(parent) { listeners.remove(listener) }
        lastState?.let(listener::boardChanged)
    }

    /** Debounced: a burst of file events costs one rebuild. */
    fun scheduleRefresh() {
        alarm.cancelAllRequests()
        alarm.addRequest({ push() }, 250)
    }

    /** Both surfaces at once: the board and the diff tree. */
    fun refreshAll() {
        scheduleRefresh()
        worktreesPanel?.scheduleRefresh()
    }

    fun settingsChanged() {
        refreshAll()
    }

    /** The plugin cannot appear mid-session in practice; probe it once. */
    fun plugin(): AriaPlugin = plugin ?: detectAriaPlugin(System.getProperty("user.home")).also { plugin = it }

    /** The roots the board is read from: every content root, plus the project directory. */
    fun roots(): List<String> {
        val roots = LinkedHashSet<String>()
        project.basePath?.let { roots.add(Git.normalize(it)) }
        for (root in ProjectRootManager.getInstance(project).contentRoots) {
            roots.add(Git.normalize(root.path))
        }
        return roots.toList()
    }

    /**
     * Every worktree of every repository behind the roots. One repository can back several
     * roots (that is the whole point of worktrees), so deduplicate by the git directory they
     * share.
     */
    fun collectWorktrees(): List<Worktree> {
        val seen = HashSet<String>()
        val worktrees = mutableListOf<Worktree>()
        for (root in roots()) {
            val gitDir = Git.commonGitDir(root) ?: continue
            if (!seen.add(gitDir)) continue
            try {
                worktrees.addAll(Git.listWorktrees(root))
            } catch (_: Exception) {
                // Not a usable repository; contributes nothing.
            }
        }
        return worktrees.filter { !it.prunable }
    }

    /** The board the commands work from. Never call on the UI thread. */
    fun ensureBoard(): List<BoardWorktree> {
        if (lastBoard.isEmpty()) {
            lastBoard = buildBoard(collectWorktrees(), base)
        }
        return lastBoard
    }

    /**
     * What the launch queue needs to plan a worktree. The board is rebuilt rather than reused: a
     * request names a change the spec session finished writing moments ago, which a cached
     * board has never seen.
     */
    fun launchContext(): LaunchContext? {
        lastBoard = emptyList()
        val board = ensureBoard()
        return if (board.any { it.isMain }) LaunchContext(board, base, plugin()) else null
    }

    /** Rebuilds the board once and feeds every surface. Never call on the UI thread. */
    fun push() {
        val base = base
        val board = try {
            buildBoard(collectWorktrees(), base)
        } catch (e: Exception) {
            notifyError(project, "Could not read the worktrees: ${message(e)}")
            return
        }
        lastBoard = board

        val roots = roots()
        val info = collectAriaInfo(System.getProperty("user.home"), roots)
        val aria = AriaSummary(
            installed = info.plugin.installed,
            version = info.plugin.version,
            skills = info.plugin.skills.size,
            learnings = info.projects.sumOf { it.learnings.size },
            projects = info.projects.size,
            notes = info.memory.entries.size,
        )
        val requests = AgentRequests.pendingRequests(roots)
        val state = BoardState(board, base, plugin(), aria, requests)
        lastState = state
        ui { for (listener in listeners) listener.boardChanged(state) }
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        fun getInstance(project: Project): BoardService = project.service()
    }
}
