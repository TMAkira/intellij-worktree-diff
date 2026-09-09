package fr.tristanmarie.worktreediff.core

import java.nio.file.Paths
import kotlin.io.path.name

data class BoardWorktree(
    val name: String,
    val path: String,
    val branch: String,
    val isMain: Boolean,
    val ahead: Int = 0,
    val behind: Int = 0,
    /** Files this branch changes relative to the base, all paths included. */
    val filesChanged: Int = 0,
    val changes: List<AriaChange> = emptyList(),
    /** True when at least one change on this worktree carries an aria-meta.md. */
    val hasAria: Boolean = false,
    /** The distinct EASY/MEDIUM/HARD labels found, so a card can show one without opening. */
    val ariaModes: List<String> = emptyList(),
    /** The gate commands this worktree's CI pipeline runs, derived from the pipeline file. */
    val gates: List<Gate> = emptyList(),
    /** Test reports that predate a test source they cover, when the run is recent. */
    val stale: List<StaleReport> = emptyList(),
    /** Set when the base could not be resolved from this worktree. */
    val error: String? = null,
)

const val CHANGES_PREFIX = "openspec/changes/"

/**
 * Names of the openspec changes a branch actually touches. Every worktree carries a full copy
 * of `openspec/changes`; what the branch *modifies* is the honest answer to "what is being
 * worked on here".
 */
fun touchedChangeNames(changedPaths: List<String>): Set<String> {
    val names = LinkedHashSet<String>()
    for (file in changedPaths) {
        if (!file.startsWith(CHANGES_PREFIX)) continue
        val name = file.removePrefix(CHANGES_PREFIX).substringBefore('/')
        if (name.isNotEmpty() && name != "archive") names.add(name)
    }
    return names
}

data class Progress(val done: Int, val total: Int) {
    val ratio: Double get() = if (total == 0) 0.0 else done.toDouble() / total
}

/** Aggregate progress over a worktree's changes, for the summary card. */
fun progressOf(worktree: BoardWorktree): Progress =
    Progress(worktree.changes.sumOf { it.done }, worktree.changes.sumOf { it.total })

/**
 * Assembles what the board shows: one entry per worktree, carrying the openspec changes it
 * works on. The main worktree is the exception: it has no branch of its own to diff, so it
 * carries every active change, which is the backlog.
 */
fun buildBoard(worktrees: List<Worktree>, base: String): List<BoardWorktree> {
    val board = mutableListOf<BoardWorktree>()

    for (worktree in worktrees) {
        val openspecRoot = Paths.get(worktree.path, "openspec")
        var entry = BoardWorktree(
            name = Paths.get(worktree.path).name,
            path = worktree.path,
            branch = worktree.branch ?: "detached at ${worktree.head.take(7)}",
            isMain = worktree.isMain,
        )

        val counts = Git.aheadBehind(worktree.path, base)
        entry = if (counts != null) {
            entry.copy(ahead = counts.ahead, behind = counts.behind)
        } else {
            entry.copy(error = "Base \"$base\" is not reachable from this worktree")
        }

        val all = scanChanges(openspecRoot)

        entry = if (worktree.isMain) {
            entry.copy(changes = markDrafts(worktree.path, all))
        } else {
            try {
                val diff = Git.diffAgainstBase(worktree.path, base)
                val touched = touchedChangeNames(diff.map { it.path })
                entry.copy(filesChanged = diff.size, changes = all.filter { it.name in touched })
            } catch (_: GitError) {
                // Without a diff we cannot tell which change is being worked on. Showing every
                // change of a possibly stale copy would be worse than showing none.
                entry.copy(changes = emptyList())
            }
        }

        entry = entry.copy(
            gates = discoverGates(worktree.path),
            stale = staleReports(worktree.path),
            hasAria = entry.changes.any { "aria-meta.md" in it.artifacts },
            ariaModes = entry.changes.mapNotNull { it.execModeLabel }.distinct(),
        )
        board.add(entry)
    }

    // Worktrees actually carrying work first; the main worktree keeps its place at the top.
    return board.sortedWith(
        compareByDescending<BoardWorktree> { it.isMain }
            .thenByDescending { it.changes.size }
            .thenBy { it.name },
    )
}

/**
 * Flags the changes of the main checkout that git has never seen. A change written but not
 * committed sits outside every branch.
 */
private fun markDrafts(cwd: String, changes: List<AriaChange>): List<AriaChange> {
    val tracked = Git.trackedUnder(cwd, CHANGES_PREFIX.trimEnd('/'))
        .filter { it.startsWith(CHANGES_PREFIX) }
        .map { it.removePrefix(CHANGES_PREFIX).substringBefore('/') }
        .toSet()
    return changes.map { it.copy(draft = it.name !in tracked) }
}
