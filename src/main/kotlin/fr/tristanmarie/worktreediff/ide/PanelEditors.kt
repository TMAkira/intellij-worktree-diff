package fr.tristanmarie.worktreediff.ide

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import fr.tristanmarie.worktreediff.core.collectAriaInfo
import fr.tristanmarie.worktreediff.web.AriaInfoHtml
import fr.tristanmarie.worktreediff.web.DetailHtml
import fr.tristanmarie.worktreediff.web.WebPanel
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent

object DevFlowIcons {
    val DEVFLOW: Icon = IconLoader.getIcon("/icons/devflow.svg", DevFlowIcons::class.java)
}

/** The file type behind the panel tabs: never on disk, never edited. */
object PanelFileType : FileType {
    override fun getName() = "WorktreeDiffPanel"
    override fun getDescription() = "Worktree Diff panel"
    override fun getDefaultExtension() = "wdpanel"
    override fun getIcon(): Icon = DevFlowIcons.DEVFLOW
    override fun isBinary() = false
    override fun isReadOnly() = true
}

enum class PanelKind { DETAIL, ARIA_INFO }

/**
 * A tab that is a page rather than a file. The tool window is 300 pixels wide on a good day,
 * where a task label wraps to ten lines; the same content in an editor tab has the room it
 * needs, so the overview stays in the tool window and the detail opens here.
 */
class PanelVirtualFile(name: String, val kind: PanelKind, val worktreePath: String?) : LightVirtualFile(name, PanelFileType, "") {
    init {
        isWritable = false
    }

    override fun getPath(): String = "worktree-diff://${kind.name.lowercase()}/${worktreePath ?: ""}"
}

class PanelEditorProvider : FileEditorProvider, com.intellij.openapi.project.DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is PanelVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val panel = file as PanelVirtualFile
        return when (panel.kind) {
            PanelKind.DETAIL -> DetailEditor(project, panel)
            PanelKind.ARIA_INFO -> AriaInfoEditor(project, panel)
        }
    }

    override fun getEditorTypeId() = "worktree-diff-panel"

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        /** Opens the detail of a worktree, or reveals the tab already open for it. */
        fun openDetail(project: Project, worktreePath: String, name: String) {
            ui {
                val manager = FileEditorManager.getInstance(project)
                val existing = manager.openFiles.filterIsInstance<PanelVirtualFile>()
                    .find { it.kind == PanelKind.DETAIL && it.worktreePath == worktreePath }
                manager.openFile(existing ?: PanelVirtualFile(name, PanelKind.DETAIL, worktreePath), true)
            }
        }

        fun openAriaInfo(project: Project) {
            ui {
                val manager = FileEditorManager.getInstance(project)
                val existing = manager.openFiles.filterIsInstance<PanelVirtualFile>().find { it.kind == PanelKind.ARIA_INFO }
                manager.openFile(existing ?: PanelVirtualFile("Aria", PanelKind.ARIA_INFO, null), true)
            }
        }
    }
}

private abstract class PanelEditor(protected val project: Project, private val file: PanelVirtualFile, private val title: String) :
    UserDataHolderBase(), FileEditor {
    protected lateinit var web: WebPanel

    override fun getComponent(): JComponent = web.component
    override fun getPreferredFocusedComponent(): JComponent = web.component
    override fun getName(): String = title
    override fun setState(state: FileEditorState) {}
    override fun isModified() = false
    override fun isValid() = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file
    override fun dispose() {}

    protected fun uiJson(key: String): com.google.gson.JsonElement =
        try {
            JsonParser.parseString(UiState.getInstance(project).get(key))
        } catch (_: Exception) {
            JsonObject()
        }
}

/** The detail of one worktree: every change with its tasks, artifacts and actions. */
private class DetailEditor(project: Project, file: PanelVirtualFile) : PanelEditor(project, file, file.name) {
    private val worktreePath = file.worktreePath ?: ""

    init {
        val service = BoardService.getInstance(project)
        web = WebPanel(DetailHtml.html(), this) { message ->
            val path = message.get("path")?.asString
            val change = message.get("change")?.asString
            when (message.get("type")?.asString) {
                "setUi" -> message.get("ui")?.let { UiState.getInstance(project).set(UiState.DETAIL, it.toString()) }
                "ready" -> service.lastState?.let(::boardChanged) ?: service.scheduleRefresh()
                "openFile" -> if (path != null) openAt(project, path, message.get("line")?.takeIf { it.isJsonPrimitive }?.asInt)
                "startWork" -> Commands.startWork(project)
                "createWorktree" -> if (change != null) Commands.createWorktree(project, change)
                "askClaude" -> Commands.askClaude(project, worktreePath, change, message.get("intent")?.asString)
            }
        }
        service.addListener(::boardChanged, this)
    }

    private fun boardChanged(state: BoardState) {
        val worktree = state.board.find { it.path == worktreePath } ?: return
        val service = BoardService.getInstance(project)
        val payload = JsonObject()
        payload.addProperty("type", "state")
        payload.add("worktree", service.gson.toJsonTree(worktree))
        payload.addProperty("base", state.base)
        payload.add("plugin", service.gson.toJsonTree(state.plugin))
        payload.add("ui", uiJson(UiState.DETAIL))
        web.send(payload.toString())
    }
}

/** Everything aria leaves on this machine. */
private class AriaInfoEditor(project: Project, file: PanelVirtualFile) : PanelEditor(project, file, "Aria") {
    init {
        web = WebPanel(AriaInfoHtml.html(), this) { message ->
            when (message.get("type")?.asString) {
                "setUi" -> message.get("ui")?.let { UiState.getInstance(project).set(UiState.ARIA_INFO, it.toString()) }
                "ready", "refresh" -> push()
                "openFile" -> message.get("path")?.asString?.let { openAt(project, it, message.get("line")?.takeIf { l -> l.isJsonPrimitive }?.asInt) }
            }
        }
    }

    private fun push() {
        val service = BoardService.getInstance(project)
        bg {
            val info = collectAriaInfo(System.getProperty("user.home"), service.roots())
            val payload = JsonObject()
            payload.addProperty("type", "state")
            payload.add("info", service.gson.toJsonTree(info))
            payload.add("ui", uiJson(UiState.ARIA_INFO))
            web.send(payload.toString())
        }
    }
}
