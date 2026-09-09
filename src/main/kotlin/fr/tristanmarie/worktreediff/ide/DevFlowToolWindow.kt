package fr.tristanmarie.worktreediff.ide

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import fr.tristanmarie.worktreediff.web.BoardHtml
import fr.tristanmarie.worktreediff.web.WebPanel
import java.nio.file.Paths
import kotlin.io.path.name

/** The DevFlow tool window: the OpenSpec overview and the worktree diff tree, as two tabs. */
class DevFlowToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val factory = ContentFactory.getInstance()
        val disposable = toolWindow.disposable

        val board = BoardPanel(project, disposable)
        val boardContent = factory.createContent(board.component, "OpenSpec", false)
        boardContent.isCloseable = false
        toolWindow.contentManager.addContent(boardContent)

        val worktrees = WorktreesPanel(project, disposable)
        val treeContent = factory.createContent(worktrees, "Worktrees", false)
        treeContent.isCloseable = false
        toolWindow.contentManager.addContent(treeContent)

        val titleActions = listOf("WorktreeDiff.StartWork", "WorktreeDiff.ReviewRequests", "WorktreeDiff.Refresh")
            .mapNotNull { ActionManager.getInstance().getAction(it) }
        toolWindow.setTitleActions(titleActions)
        ActionManager.getInstance().getAction("WorktreeDiff.Gear")?.let {
            toolWindow.setAdditionalGearActions(it as com.intellij.openapi.actionSystem.ActionGroup)
        }

        BoardService.getInstance(project).refreshAll()
    }
}

/** The overview page in the tool window, wired to the board service. */
class BoardPanel(private val project: Project, parent: Disposable) : Disposable {
    val web: WebPanel
    val component get() = web.component

    init {
        Disposer.register(parent, this)
        val service = BoardService.getInstance(project)
        web = WebPanel(BoardHtml.html(), this) { message ->
            val path = message.get("path")?.asString
            when (message.get("type")?.asString) {
                "setUi" -> message.get("ui")?.let { UiState.getInstance(project).set(UiState.SIDEBAR, it.toString()) }
                "ready", "refresh" -> service.refreshAll()
                "openDetail" -> if (path != null) PanelEditorProvider.openDetail(project, path, Paths.get(path).name)
                "openFile" -> if (path != null) openAt(project, path, message.get("line")?.takeIf { it.isJsonPrimitive }?.asInt)
                "openAria" -> PanelEditorProvider.openAriaInfo(project)
                "startWork" -> Commands.startWork(project)
                "createWorktree" -> message.get("change")?.asString?.let { Commands.createWorktree(project, it) }
                "reviewRequest" -> message.get("file")?.asString?.let { AgentRequests.reviewOne(project, it) }
            }
        }
        service.addListener(::boardChanged, this)
    }

    private fun boardChanged(state: BoardState) {
        val gson = BoardService.getInstance(project).gson
        val payload = JsonObject()
        payload.addProperty("type", "state")
        payload.add("board", gson.toJsonTree(state.board))
        payload.addProperty("base", state.base)
        payload.add("plugin", gson.toJsonTree(state.plugin))
        payload.add("aria", gson.toJsonTree(state.aria))
        payload.add("requests", gson.toJsonTree(state.requests))
        payload.add(
            "ui",
            try {
                JsonParser.parseString(UiState.getInstance(project).get(UiState.SIDEBAR))
            } catch (_: Exception) {
                JsonObject()
            },
        )
        web.send(payload.toString())
    }

    override fun dispose() {}
}
