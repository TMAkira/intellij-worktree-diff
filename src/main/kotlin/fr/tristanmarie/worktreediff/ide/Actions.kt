package fr.tristanmarie.worktreediff.ide

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import fr.tristanmarie.worktreediff.core.ViewMode
import java.io.File

private fun panel(project: Project?): WorktreesPanel? = project?.let { BoardService.getInstance(it).worktreesPanel }

private fun selected(e: AnActionEvent): Node? = panel(e.project)?.selectedNode()

abstract class DevFlowAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

class RefreshAction : DevFlowAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { BoardService.getInstance(it).refreshAll() }
    }
}

class ChangeBaseAction : DevFlowAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { Commands.changeBase(it) }
    }
}

class StartWorkAction : DevFlowAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { Commands.startWork(it) }
    }
}

class CreateWorktreeAction : DevFlowAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { Commands.createWorktree(it) }
    }
}

class ReviewRequestsAction : DevFlowAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { Commands.reviewRequests(it) }
    }
}

class ShowAriaAction : DevFlowAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { PanelEditorProvider.openAriaInfo(it) }
    }
}

class OpenSettingsAction : DevFlowAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ShowSettingsUtil.getInstance().showSettingsDialog(it, WorktreeDiffConfigurable::class.java) }
    }
}

/** Tree layout on, flat list off. */
class GroupByFoldersAction : ToggleAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean =
        e.project?.let { WorktreeDiffSettings.getInstance(it).state.viewMode == ViewMode.TREE } ?: true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        WorktreeDiffSettings.getInstance(project).state.viewMode = if (state) ViewMode.TREE else ViewMode.LIST
        panel(project)?.scheduleRefresh()
    }
}

class ShowDiffAction : DevFlowAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selected(e) is FileRow
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        (selected(e) as? FileRow)?.let { WorktreesPanel.openDiff(project, it.node) }
    }
}

class OpenFileAction : DevFlowAction() {
    override fun update(e: AnActionEvent) {
        val row = selected(e) as? FileRow
        e.presentation.isEnabledAndVisible = row != null && row.node.file.kind != fr.tristanmarie.worktreediff.core.ChangeKind.DELETED
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        (selected(e) as? FileRow)?.let { WorktreesPanel.openFile(project, it.node) }
    }
}

class RevealInExplorerAction : DevFlowAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selected(e) is WorktreeNode && RevealFileAction.isSupported()
    }

    override fun actionPerformed(e: AnActionEvent) {
        (selected(e) as? WorktreeNode)?.let { RevealFileAction.openDirectory(File(it.worktree.path)) }
    }
}

class OpenAsProjectAction : DevFlowAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selected(e) is WorktreeNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        (selected(e) as? WorktreeNode)?.let { NewWork.openAsProject(it.worktree.path) }
    }
}

class OpenClaudeHereAction : DevFlowAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selected(e) is WorktreeNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        (selected(e) as? WorktreeNode)?.let { Commands.askClaude(project, it.worktree.path, null, null) }
    }
}

class OpenDetailAction : DevFlowAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = selected(e) is WorktreeNode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        (selected(e) as? WorktreeNode)?.let { PanelEditorProvider.openDetail(project, it.worktree.path, it.name) }
    }
}
