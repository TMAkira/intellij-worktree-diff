package fr.tristanmarie.worktreediff.ide

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import fr.tristanmarie.worktreediff.core.AriaChange
import fr.tristanmarie.worktreediff.core.BoardWorktree
import fr.tristanmarie.worktreediff.core.Git
import fr.tristanmarie.worktreediff.core.Prompts
import fr.tristanmarie.worktreediff.core.slashes
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.name

/**
 * The launch queue: how a session asks for the worktree that implements a spec, or for a
 * session in the worktree that is already implementing one.
 *
 * A file rather than a socket, on purpose. The session needs no synchronous answer, so the
 * only thing an HTTP endpoint would add is a listener every local process can reach. A JSON
 * file is the request, the audit trail and the thing that survives an IDE restart, and the
 * agent already knows how to write files.
 *
 * Nothing is created without a human. The prompt opens in a tab, editable, and the worktree
 * exists only after the notification is answered.
 */

/** A launch request, as an agent writes it. Only `change` is required. */
class LaunchRequest(
    val change: String? = null,
    val branch: String? = null,
    val prompt: String? = null,
    /** The requesting session's ListAgents name, so the review says who is asking. */
    val from: String? = null,
    /** One line: why this worktree, and what it owns when there are several. */
    val reason: String? = null,
    /** Which worktree to resume in: a full path or just its directory name. A tiebreaker. */
    val worktree: String? = null,
)

/** One request still waiting for an answer, as the tool window lists it. */
class PendingRequest(
    /** Absolute path of the request file, what [AgentRequests.reviewOne] is called back with. */
    val file: String,
    val name: String,
    val change: String? = null,
    val from: String? = null,
    val reason: String? = null,
    /** Set when the file is not readable as JSON; the row says so instead of hiding it. */
    val broken: String? = null,
    /** The detail of a launch that failed. Such a request stays listed. */
    val failed: String? = null,
)

/**
 * What an approved request would do. Two shapes, because the second one touches no git at all:
 * it opens a session in a worktree that exists, and creates nothing.
 */
private sealed class Launch(val change: AriaChange, val worktree: BoardWorktree, val branch: String, val target: String) {
    class Create(change: AriaChange, worktree: BoardWorktree, branch: String, target: String, val plan: WorktreePlan) :
        Launch(change, worktree, branch, target)

    class Resume(change: AriaChange, worktree: BoardWorktree, branch: String, target: String) :
        Launch(change, worktree, branch, target)
}

private class Candidate(val worktree: BoardWorktree, val change: AriaChange)

object AgentRequests {
    private const val HEADER_END = "-->"
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** Create and change both fire for a written file; a request is reviewed once. */
    private val seen: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * Only the git half is serialized. Reviews run concurrently on purpose: a review waits on a
     * notification the human may leave sitting for an hour. `git worktree add` twice at once is
     * what would actually race, on the same index.
     */
    private val gitLock = ReentrantLock()

    private fun isRequestName(name: String) = name.endsWith(".json") && !name.endsWith(".result.json")

    private fun resultName(name: String) = name.removeSuffix(".json") + ".result.json"

    private fun resultPath(file: Path): Path = file.resolveSibling(resultName(file.name))

    private fun key(file: Path) = file.toString().lowercase()

    /**
     * Picks up requests written while the IDE was closed, and the ones whose notification was
     * dismissed rather than answered. Also the command behind "Review agent launch requests".
     */
    fun sweep(project: Project, root: String): Int {
        val dir = Paths.get(root, REQUEST_DIR)
        val names = try {
            Files.list(dir).use { s -> s.map { it.name }.toList() }
        } catch (_: IOException) {
            return 0
        }
        val pending = names.filter { isRequestName(it) && resultName(it) !in names }
        for (name in pending) enqueue(project, dir.resolve(name))
        return pending.size
    }

    /** Called by the watcher for every file that appears or changes under the queue. */
    fun take(project: Project, file: Path) {
        BoardService.getInstance(project).scheduleRefresh()
        enqueue(project, file)
    }

    /**
     * The requests nobody has answered, for the tool window. A notification is the worst
     * possible place for the only copy of this list: it disappears on its own.
     */
    fun pendingRequests(roots: List<String>): List<PendingRequest> {
        val found = mutableListOf<PendingRequest>()
        val taken = HashSet<String>()
        for (root in roots) {
            val dir = Paths.get(root, REQUEST_DIR)
            val names = try {
                Files.list(dir).use { s -> s.map { it.name }.toList() }
            } catch (_: IOException) {
                continue
            }
            for (name in names.filter(::isRequestName)) {
                val file = dir.resolve(name)
                // A launched or refused request is answered and leaves the list. An error is not
                // an answer: the requester is told not to poll its result file, so a failed
                // launch that also leaves the list is a launch nobody hears about.
                val answer = if (resultName(name) in names) readResult(dir.resolve(resultName(name))) else null
                if (answer != null && answer.status != "error") continue
                if (!taken.add(key(file))) continue

                var entry = PendingRequest(file = slashes(file.toString()), name = name.removeSuffix(".json"), failed = answer?.detail)
                try {
                    val request = gson.fromJson(Files.readString(file), LaunchRequest::class.java)
                    entry = PendingRequest(entry.file, entry.name, request?.change, request?.from, request?.reason, failed = entry.failed)
                } catch (e: Exception) {
                    entry = PendingRequest(entry.file, entry.name, broken = message(e), failed = entry.failed)
                }
                found.add(entry)
            }
        }
        return found.sortedBy { it.name }
    }

    private class Result(val status: String?, val detail: String?)

    private fun readResult(file: Path): Result? =
        try {
            val obj = JsonParser.parseString(Files.readString(file)).asJsonObject
            Result(obj.get("status")?.asString, obj.get("detail")?.asString)
        } catch (_: Exception) {
            null
        }

    /**
     * Reopens one review from the tool window. Clears the once-only guard first: this is a
     * deliberate second look at a request whose notification was missed. A failed launch also
     * has its result removed, so the retry the row offers does something.
     */
    fun reviewOne(project: Project, file: String) {
        val path = Paths.get(file)
        seen.remove(key(path))
        bg {
            if (readResult(resultPath(path))?.status == "error") {
                try {
                    Files.deleteIfExists(resultPath(path))
                } catch (_: IOException) {
                }
            }
            enqueue(project, path)
        }
    }

    private fun enqueue(project: Project, file: Path) {
        if (!isRequestName(file.name) || !seen.add(key(file))) return
        bg {
            try {
                review(project, file)
            } catch (e: Exception) {
                notifyError(project, "Launch request failed: ${message(e)}")
            }
        }
    }

    private fun review(project: Project, file: Path) {
        // A request already answered: the sweep and the watcher can both reach the same file.
        if (Files.exists(resultPath(file))) return

        val request = try {
            gson.fromJson(Files.readString(file), LaunchRequest::class.java) ?: LaunchRequest()
        } catch (e: Exception) {
            settle(project, file, "error", "Not readable as JSON: ${message(e)}")
            return
        }

        // Answering "rejected" before offering the switch would strand the request: it would
        // carry a result, so no later sweep would ever pick it up again. Ask first, settle after.
        val settings = WorktreeDiffSettings.getInstance(project)
        if (!settings.launchEnabled && !offerToEnable(project, request)) {
            settle(
                project, file, "rejected",
                "Launch autonomy is off. The human turns the agent launch setting to \"ask\", or clicks Create worktree.",
            )
            return
        }

        val service = BoardService.getInstance(project)
        val context = service.launchContext()
        if (context == null) {
            settle(project, file, "error", "No main working tree in this project to launch from.")
            return
        }

        val launch = when (val resolved = resolveLaunch(project, request, context)) {
            is String -> {
                settle(project, file, "error", resolved)
                return
            }
            is Launch -> attributeTo(resolved, request.from)
            else -> return
        }

        // A prompt written by the agent gets the location frame put above it. The default one
        // already carries it; a hand-written one proved it will not.
        val written = (request.prompt ?: "").trim()
        val proposed = if (written.isNotEmpty()) {
            Prompts.locationFrame(launch.worktree, launch.change, context.base, NewWork.language(project)) + written
        } else {
            defaultPrompt(project, launch, context)
        }
        decide(project, file, request, launch, proposed)
    }

    /**
     * Which of the two launches a request asks for, read off the repository, never declared.
     * A change is an uncommitted draft in the main checkout and nowhere else, or it lives on a
     * branch that has a worktree, never both. Resume wins when both could somehow apply.
     */
    private fun resolveLaunch(project: Project, request: LaunchRequest, context: LaunchContext): Any {
        val name = (request.change ?: "").trim()
        if (name.isEmpty()) return "A request must name a change: {\"change\": \"<change-name>\"}."

        val carrying = context.board
            .filter { !it.isMain }
            .mapNotNull { worktree -> worktree.changes.find { it.name == name }?.let { Candidate(worktree, it) } }

        val wanted = (request.worktree ?: "").trim()
        val named = if (wanted.isNotEmpty()) carrying.filter { sameWorktree(it.worktree, wanted) } else carrying

        if (wanted.isNotEmpty() && named.isEmpty()) {
            val known = carrying.joinToString(", ") { slashes(it.worktree.path) }.ifEmpty { "none" }
            return "No worktree matching \"$wanted\" carries \"$name\". Worktrees carrying it: $known."
        }

        val main = context.board.find { it.isMain }
        val matching = if (named.size > 1 && main != null) owners(named, main.path) else named
        if (matching.size > 1) {
            val paths = matching.joinToString(", ") { slashes(it.worktree.path) }
            return "\"$name\" is carried by several unrelated worktrees: $paths. Name the one you mean in \"worktree\"."
        }
        if (matching.size == 1) {
            val (worktree, change) = matching[0].worktree to matching[0].change
            return Launch.Resume(change, worktree, worktree.branch, worktree.path)
        }
        return planCreate(project, request, name, context)
    }

    /**
     * Narrows candidates to the ones that own a change rather than inherit it. A branch forked
     * from another carries everything the parent contributed, so the ancestor is the owner.
     * Run from the main checkout, never from a candidate: a board entry can outlive its
     * directory, and a git call from a path that no longer exists fails silently here.
     */
    private fun owners(candidates: List<Candidate>, root: String): List<Candidate> {
        val kept = candidates.filter { candidate ->
            candidates.none { other -> other !== candidate && Git.isAncestor(root, other.worktree.branch, candidate.worktree.branch) }
        }
        return kept.ifEmpty { candidates }
    }

    /**
     * Stamps the requesting session onto the change, so every prompt built from this launch
     * knows who to report to. The request is the only place that address can be fresh.
     */
    private fun attributeTo(launch: Launch, from: String?): Launch {
        val requester = (from ?: "").trim()
        if (requester.isEmpty()) return launch
        val change = launch.change.copy(specSession = requester)
        return when (launch) {
            is Launch.Create -> Launch.Create(
                change, launch.worktree, launch.branch, launch.target,
                WorktreePlan(launch.plan.root, launch.plan.target, launch.plan.branch, launch.plan.startPoint, change, launch.plan.worktree),
            )
            is Launch.Resume -> Launch.Resume(change, launch.worktree, launch.branch, launch.target)
        }
    }

    /** The original path: a drafted change in the main checkout gets a branch and a worktree. */
    private fun planCreate(project: Project, request: LaunchRequest, name: String, context: LaunchContext): Any {
        val main = context.board.find { it.isMain }
        val change = main?.changes?.find { it.name == name }
        if (main == null || change == null) {
            val known = main?.changes?.joinToString(", ") { it.name }?.ifEmpty { null } ?: "none"
            return "No change named \"$name\". No worktree of this repository carries it, so there is nothing " +
                "to resume, and the main checkout has: $known."
        }
        val plan = NewWork.planWorktree(project, main, change, context.base, request.branch)
        NewWork.branchProblem(plan.root, plan.branch)?.let { return it }
        if (exists(plan.target)) return "${plan.target} already exists."
        return Launch.Create(change, plan.worktree, plan.branch, plan.target, plan)
    }

    /** The prompt nobody wrote: the tool window button's, for whichever of the two launches this is. */
    private fun defaultPrompt(project: Project, launch: Launch, context: LaunchContext): String {
        if (launch is Launch.Create) return NewWork.plannedPrompt(project, launch.plan, context.base, context.plugin)
        val main = context.board.find { it.isMain }
        return Prompts.continuePrompt(
            launch.worktree, launch.change, context.base, context.plugin, NewWork.language(project),
            main?.let { NewWork.requestDirFor(project, it.path) },
        )
    }

    /** Opens the prompt for reading, asks, and creates nothing until the answer comes back. */
    private fun decide(project: Project, file: Path, request: LaunchRequest, launch: Launch, proposed: String) {
        val promptPath = file.resolveSibling(file.name.removeSuffix(".json") + ".prompt.md")

        // A review reopened after a dismissed notification keeps what the human had already
        // written into it. An open editor wins over the file: it may hold unsaved edits.
        val alreadyOpen = virtualFileOf(promptPath.toString())?.let { vf ->
            ApplicationManager.getApplication().runReadAction<Boolean> {
                FileEditorManager.getInstance(project).isFileOpen(vf) && FileDocumentManager.getInstance().getDocument(vf) != null
            }
        } ?: false
        if (!alreadyOpen) {
            val kept = stripHeader(readIfPresent(promptPath) ?: "").trim()
            Files.createDirectories(promptPath.parent)
            Files.writeString(promptPath, header(request, launch) + kept.ifEmpty { proposed } + "\n")
        }
        val vf = virtualFileOf(promptPath.toString())
        if (vf == null) {
            settle(project, file, "error", "Could not open ${promptPath.name} for review.")
            return
        }
        ui { FileEditorManager.getInstance(project).openFile(vf, true) }

        val who = if (request.from != null) "${request.from} asks" else "An agent asks"
        val why = if (request.reason != null) " — ${request.reason}" else ""
        val what = when (launch) {
            is Launch.Create -> "for a worktree on \"${launch.change.name}\", branch ${launch.branch}"
            is Launch.Resume -> "to resume \"${launch.change.name}\" in ${Paths.get(launch.target).name}, on the existing " +
                "branch ${launch.branch} — nothing will be created"
        }

        var answered = false
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("Launch request", "$who $what$why. The prompt is open in a tab: read it, edit it, then decide.", NotificationType.INFORMATION)
            .setImportant(true)
        notification.addAction(NotificationAction.createSimpleExpiring("Launch") {
            answered = true
            // The tab is the authority, not the file: it carries edits that were never saved.
            val text = FileDocumentManager.getInstance().getDocument(vf)?.text ?: readIfPresent(promptPath) ?: ""
            bg { approve(project, file, launch, proposed, stripHeader(text).trim()) }
        })
        notification.addAction(NotificationAction.createSimpleExpiring("Reject") {
            answered = true
            bg { settle(project, file, "rejected", "The human refused this launch.") }
        })
        notification.whenExpired {
            // Dismissed rather than refused: leave it pending so the sweep can offer it again.
            if (!answered) seen.remove(key(file))
        }
        ui { notification.notify(project) }
    }

    private fun approve(project: Project, file: Path, launch: Launch, proposed: String, final: String) {
        if (final.isEmpty()) {
            settle(project, file, "rejected", "The prompt was empty, or its header comment was left unclosed. Nothing was launched.")
            return
        }

        if (launch is Launch.Create) {
            // Re-checked inside the lock, not before it: another approved request may have taken
            // the directory or the branch while this one waited for an answer.
            val created = gitLock.withLock { NewWork.targetIsFree(project, launch.plan) && NewWork.runPlan(project, launch.plan) }
            if (!created) {
                settle(project, file, "error", "The worktree could not be created; see the IDE notification.")
                return
            }
        } else if (!exists(launch.target)) {
            settle(project, file, "error", "${slashes(launch.target)} no longer exists. Nothing was launched.")
            return
        }

        ClaudeLauncher.launch(project, final, launch.target)
        BoardService.getInstance(project).refreshAll()

        val edited = final != proposed.trim()
        val done = if (launch is Launch.Create) "Launched." else "Resumed in the existing worktree; nothing was created."
        val extra = JsonObject()
        extra.addProperty("mode", if (launch is Launch.Create) "create" else "resume")
        extra.addProperty("worktree", slashes(launch.target))
        extra.addProperty("branch", launch.branch)
        if (launch is Launch.Create) extra.addProperty("startPoint", launch.plan.startPoint)
        extra.addProperty("promptEdited", edited)
        if (edited) extra.addProperty("prompt", final)
        settle(project, file, "launched", if (edited) "$done The human edited the prompt." else done, extra)

        val name = Paths.get(launch.target).name
        val note = if (launch is Launch.Create) "$name created on ${launch.branch}, session opened." else "Session opened in $name, on ${launch.branch}."
        notify(project, note, NotificationType.INFORMATION, "Open as project" to { NewWork.openAsProject(launch.target) })
    }

    /**
     * The block above the prompt: what the launch would be, and the fact that it has not
     * happened. Stripped before the prompt is sent, so editing around it is safe.
     */
    private fun header(request: LaunchRequest, launch: Launch): String {
        val rows = mutableListOf(
            "Requested by" to (request.from ?: "(unnamed session)"),
            "Mode" to if (launch is Launch.Create) "create a worktree" else "resume an existing worktree",
            "Change" to launch.change.name,
            "Branch" to launch.branch,
            "Worktree" to slashes(launch.target),
        )
        if (launch is Launch.Create) rows.add("Forked from" to launch.plan.startPoint)
        if (request.reason != null) rows.add("Reason" to request.reason)

        val note = if (launch is Launch.Create) listOf(
            "  Nothing has been created yet. Everything below this comment is the prompt the new",
            "  session will receive: edit it freely, then answer the notification. Saving is not",
            "  required — the tab is what gets sent.",
        ) else listOf(
            "  The branch and the worktree above already exist. Approving creates no branch and no",
            "  worktree, and moves no change directory — it opens a session in that worktree with",
            "  the prompt below. Edit it freely, then answer the notification. Saving is not",
            "  required — the tab is what gets sent.",
        )
        val width = rows.maxOf { it.first.length }
        return (listOf("<!-- worktree-diff launch request") +
            rows.map { (k, v) -> "  ${k.padEnd(width)} : $v" } +
            listOf("") + note + listOf(HEADER_END, "")).joinToString("\n")
    }

    private fun stripHeader(text: String): String {
        val trimmed = text.removePrefix("﻿")
        if (!trimmed.trimStart().startsWith("<!--")) return trimmed
        // An unclosed header means the whole file is one comment. Returning it would send the
        // instructions-for-the-human to the implementer; empty stops the launch instead.
        val end = trimmed.indexOf(HEADER_END)
        return if (end == -1) "" else trimmed.substring(end + HEADER_END.length)
    }

    private fun sameFolder(a: String, b: String): Boolean {
        fun norm(p: String) = slashes(Paths.get(p).toAbsolutePath().normalize().toString()).trimEnd('/').lowercase()
        return norm(a) == norm(b)
    }

    /** A requester names a worktree by path or by directory name; both are accepted. */
    private fun sameWorktree(worktree: BoardWorktree, wanted: String): Boolean =
        sameFolder(worktree.path, wanted) || worktree.name.equals(wanted, ignoreCase = true)

    /** Writes the answer next to the request. The agent reads it; nobody has to poll it. */
    private fun settle(project: Project, file: Path, status: String, detail: String, extra: JsonObject = JsonObject()) {
        val body = JsonObject()
        body.addProperty("status", status)
        body.addProperty("detail", detail)
        body.addProperty("at", Instant.now().toString())
        for ((k, v) in extra.entrySet()) body.add(k, v)
        try {
            Files.writeString(resultPath(file), gson.toJson(body) + "\n")
        } catch (e: IOException) {
            notifyError(project, "Could not answer the launch request: ${message(e)}")
        }
        // The result file is the requester's channel, and the requester is told not to poll it.
        // A failure that only lands there is a failure nobody reads.
        if (status == "error") notifyError(project, "Launch request ${file.name} failed: $detail")
        BoardService.getInstance(project).scheduleRefresh()
        virtualFileOf(file.parent.toString())?.let { VfsUtil.markDirtyAndRefresh(true, true, true, it) }
    }

    /**
     * A request arriving while autonomy is off is worth one notification, not silence. Returns
     * true when the user turned the switch on, in which case this request goes through now.
     * Blocks the calling background thread until the notification is answered or expires.
     */
    private fun offerToEnable(project: Project, request: LaunchRequest): Boolean {
        val who = request.from ?: "A session"
        val latch = java.util.concurrent.CountDownLatch(1)
        var enabled = false
        val notification: Notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("$who asked for a Claude session on \"${request.change}\", but launch autonomy is off.", NotificationType.WARNING)
            .setImportant(true)
        notification.addAction(NotificationAction.createSimpleExpiring("Enable launch requests") {
            WorktreeDiffSettings.getInstance(project).state.agentLaunch = AgentLaunch.ASK
            enabled = true
            latch.countDown()
        })
        notification.whenExpired { latch.countDown() }
        ui { notification.notify(project) }
        latch.await()
        return enabled
    }

    /**
     * Keeps the queue out of git without touching a tracked .gitignore, which belongs to whoever
     * owns the repository and not to this plugin.
     */
    fun excludeQueue(root: String) {
        val gitDir = Git.commonGitDir(root) ?: return
        val file = Paths.get(gitDir, "info", "exclude")
        val line = ".worktree-diff/"
        try {
            val current = readIfPresent(file) ?: ""
            if (current.lines().any { it.trim() == line }) return
            Files.createDirectories(file.parent)
            val separator = if (current.isEmpty() || current.endsWith("\n")) "" else "\n"
            Files.writeString(file, "$current$separator$line\n")
        } catch (_: IOException) {
            // Not fatal: the queue still works, it just shows up as untracked.
        }
    }

    private fun readIfPresent(file: Path): String? =
        try {
            Files.readString(file)
        } catch (_: IOException) {
            null
        }
}
