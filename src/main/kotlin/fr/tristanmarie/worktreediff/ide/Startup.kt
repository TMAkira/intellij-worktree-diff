package fr.tristanmarie.worktreediff.ide

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

/**
 * Wires the refresh triggers once the project is open.
 *
 * Worktrees mostly live outside the project, where the IDE's file watcher does not reach, so
 * regaining focus and saving a file are the signals for the diff tree, like in the VS Code
 * extension. The launch queue is polled: an agent writes there from a terminal, while the
 * IDE window is focused and no file of the project is being saved.
 */
class Startup : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = BoardService.getInstance(project)
        val settings = WorktreeDiffSettings.getInstance(project)
        val connection = project.messageBus.connect(service)

        connection.subscribe(
            ApplicationActivationListener.TOPIC,
            object : ApplicationActivationListener {
                override fun applicationActivated(ideFrame: IdeFrame) {
                    if (ideFrame.project === project && settings.state.autoRefresh) service.refreshAll()
                }
            },
        )

        connection.subscribe(
            FileDocumentManagerListener.TOPIC,
            object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(document: com.intellij.openapi.editor.Document) {
                    if (settings.state.autoRefresh) service.worktreesPanel?.scheduleRefresh()
                }
            },
        )

        connection.subscribe(
            com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    // The OpenSpec board reads markdown; anything else is the diff tree's business.
                    if (events.any { it.path.contains("/openspec/changes/") && it.path.endsWith(".md") }) {
                        service.scheduleRefresh()
                    }
                }
            },
        )

        bg {
            for (root in service.roots()) AgentRequests.excludeQueue(root)
            for (root in service.roots()) AgentRequests.sweep(project, root)
        }

        val poller = RequestPoller(project)
        val future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({ poller.tick() }, 3, 3, TimeUnit.SECONDS)
        Disposer.register(service) { future.cancel(false) }

        if (System.getProperty("worktreeDiff.smoke") == "true") smoke(project)
    }

    /**
     * The walk-through behind `./gradlew runIde -PsmokeProject=...`: opens each surface in turn
     * so a screenshot, or the log, says whether it renders. Never runs in a real IDE.
     */
    private fun smoke(project: Project) {
        val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
        fun at(seconds: Long, work: () -> Unit) {
            scheduler.schedule({ if (!project.isDisposed) ui(work) }, seconds, TimeUnit.SECONDS)
        }
        at(5) { com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("DevFlow")?.activate(null) }
        at(25) {
            val window = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("DevFlow") ?: return@at
            window.contentManager.contents.getOrNull(1)?.let { window.contentManager.setSelectedContent(it) }
        }
        at(40) {
            bg {
                val main = BoardService.getInstance(project).ensureBoard().find { it.isMain } ?: return@bg
                PanelEditorProvider.openDetail(project, main.path, main.name)
            }
        }
        at(55) { PanelEditorProvider.openAriaInfo(project) }
    }
}

/**
 * Notices a request file the moment it appears or changes, without the VFS: one directory
 * listing every few seconds, which is cheaper than what a single git call costs elsewhere.
 */
private class RequestPoller(private val project: Project) {
    private val known = HashMap<String, Long>()
    private var lastListing: Set<String> = emptySet()

    fun tick() {
        if (project.isDisposed) return
        val service = BoardService.getInstance(project)
        val listing = HashSet<String>()
        for (root in service.roots()) {
            val dir = Paths.get(root, REQUEST_DIR)
            val files = try {
                Files.list(dir).use { it.toList() }
            } catch (_: IOException) {
                continue
            }
            for (file in files) {
                val name = file.name
                if (!name.endsWith(".json")) continue
                val stamp = try {
                    Files.getLastModifiedTime(file).toMillis()
                } catch (_: IOException) {
                    continue
                }
                val key = file.toString()
                listing.add(key)
                if (known[key] != stamp) {
                    known[key] = stamp
                    if (!name.endsWith(".result.json")) AgentRequests.take(project, file)
                }
            }
        }
        if (listing != lastListing) {
            lastListing = listing
            known.keys.retainAll(listing)
            service.scheduleRefresh()
        }
    }
}
