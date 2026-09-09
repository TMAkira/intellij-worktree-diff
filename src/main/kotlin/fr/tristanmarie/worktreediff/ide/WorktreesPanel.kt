package fr.tristanmarie.worktreediff.ide

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.FileStatus
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.ui.tree.TreeUtil
import fr.tristanmarie.worktreediff.core.ChangeKind
import fr.tristanmarie.worktreediff.core.ChangedFile
import fr.tristanmarie.worktreediff.core.DirNode
import fr.tristanmarie.worktreediff.core.FileNode
import fr.tristanmarie.worktreediff.core.Git
import fr.tristanmarie.worktreediff.core.TreeChild
import fr.tristanmarie.worktreediff.core.ViewMode
import fr.tristanmarie.worktreediff.core.Worktree
import fr.tristanmarie.worktreediff.core.buildFileNodes
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.io.path.name

/** What a row of the tree stands for. */
sealed interface Node

class WorktreeNode(val worktree: Worktree, val base: String, val ahead: Int?, val behind: Int?) : Node {
    val name: String get() = Paths.get(worktree.path).name
    val branch: String get() = worktree.branch ?: "detached at ${worktree.head.take(7)}"
}

class SectionNode(val worktree: Worktree, val kind: SectionKind, val label: String, val files: List<ChangedFile>, val leftRef: String) : Node

enum class SectionKind { BASE, UNCOMMITTED }

class MessageNode(val label: String, val icon: Icon? = null) : Node

class DirRow(val node: DirNode) : Node

class FileRow(val node: FileNode) : Node

/** Everything one worktree row needs, computed off the UI thread. */
private class WorktreeData(val node: WorktreeNode, val children: List<Node>)

/**
 * The tree of changed files: one node per worktree, then what the branch adds against the
 * base and what is not committed yet, grouped by folder.
 */
class WorktreesPanel(private val project: Project, parent: Disposable) : SimpleToolWindowPanel(true, true), Disposable {
    private val root = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(root)
    val tree = Tree(model)
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    init {
        com.intellij.openapi.util.Disposer.register(parent, this)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = Renderer()
        tree.emptyText.text = "Refreshing…"
        TreeSpeedSearch.installOn(tree, true) { path -> labelOf(path) }

        val toolbarGroup = ActionManager.getInstance().getAction("WorktreeDiff.Toolbar") as DefaultActionGroup
        val toolbar = ActionManager.getInstance().createActionToolbar("WorktreeDiff", toolbarGroup, true)
        toolbar.targetComponent = tree
        this.toolbar = toolbar.component
        setContent(ScrollPaneFactory.createScrollPane(tree))

        val popupGroup = ActionManager.getInstance().getAction("WorktreeDiff.TreePopup") as DefaultActionGroup
        PopupHandler.installPopupMenu(tree, popupGroup, ActionPlaces.POPUP)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) openSelected()
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) openSelected()
            }
        })
        BoardService.getInstance(project).worktreesPanel = this
        scheduleRefresh()
    }

    private fun labelOf(path: TreePath): String = when (val node = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject) {
        is WorktreeNode -> node.name
        is SectionNode -> node.label
        is DirRow -> node.node.label
        is FileRow -> node.node.file.path.substringAfterLast('/')
        is MessageNode -> node.label
        else -> ""
    }

    fun selectedNode(): Node? = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? Node

    /** Debounced: a burst of saves costs one rebuild. */
    fun scheduleRefresh() {
        alarm.cancelAllRequests()
        alarm.addRequest({ refresh() }, 300)
    }

    /** Rebuilds the whole model off the UI thread, then swaps it in keeping what was expanded. */
    private fun refresh() {
        val service = BoardService.getInstance(project)
        val settings = WorktreeDiffSettings.getInstance(project)
        val base = settings.base
        val rows: List<Any> = try {
            val worktrees = service.collectWorktrees()
            val visible = worktrees.filter { settings.state.includeMainWorktree || !it.isMain }
            when {
                worktrees.isEmpty() -> listOf(MessageNode("No git repository in this project", AllIcons.General.Information))
                visible.isEmpty() -> listOf(MessageNode("No linked worktree", AllIcons.General.Information))
                else -> visible.map { load(it, base, settings) }
            }
        } catch (e: Exception) {
            listOf(MessageNode(message(e), AllIcons.General.Warning))
        }
        ui { render(rows) }
    }

    private fun load(worktree: Worktree, base: String, settings: WorktreeDiffSettings): WorktreeData {
        val cwd = worktree.path
        val counts = Git.aheadBehind(cwd, base)
        val node = WorktreeNode(worktree, base, counts?.ahead, counts?.behind)

        val baseSha = Git.mergeBase(cwd, base)
            ?: return WorktreeData(node, listOf(MessageNode("Base \"$base\" not found in this worktree", AllIcons.General.Warning)))

        val sections = mutableListOf<SectionNode>()
        try {
            sections.add(SectionNode(worktree, SectionKind.BASE, "vs $base", Git.diffAgainstBase(cwd, base), baseSha))
        } catch (e: Exception) {
            return WorktreeData(node, listOf(MessageNode(message(e), AllIcons.General.Warning)))
        }
        if (settings.state.showUncommitted) {
            val headSha = Git.resolveRef(cwd, "HEAD") ?: ""
            try {
                sections.add(SectionNode(worktree, SectionKind.UNCOMMITTED, "Uncommitted", Git.uncommittedChanges(cwd), headSha))
            } catch (_: Exception) {
                // Leave the section out rather than failing the whole worktree.
            }
        }

        // With a single section there is nothing to disambiguate, so skip the extra level.
        val nonEmpty = sections.filter { it.files.isNotEmpty() }
        val children: List<Node> = when (nonEmpty.size) {
            0 -> listOf(MessageNode("No difference", AllIcons.General.InspectionsOK))
            1 -> fileRows(nonEmpty[0], settings)
            else -> nonEmpty
        }
        return WorktreeData(node, children)
    }

    private fun fileRows(section: SectionNode, settings: WorktreeDiffSettings): List<Node> =
        buildFileNodes(section.worktree, section.files, section.leftRef, settings.state.viewMode, settings.state.compactFolders)
            .map(::rowOf)

    private fun rowOf(child: TreeChild): Node = when (child) {
        is DirNode -> DirRow(child)
        is FileNode -> FileRow(child)
    }

    private fun render(rows: List<Any>) {
        val expanded = HashSet<String>()
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as DefaultMutableTreeNode
            val node = child.userObject as? WorktreeNode ?: continue
            if (tree.isExpanded(TreePath(child.path))) expanded.add(node.worktree.path)
        }

        root.removeAllChildren()
        val settings = WorktreeDiffSettings.getInstance(project)
        val toExpand = mutableListOf<DefaultMutableTreeNode>()
        for (row in rows) {
            when (row) {
                is MessageNode -> root.add(DefaultMutableTreeNode(row))
                is WorktreeData -> {
                    val worktreeNode = DefaultMutableTreeNode(row.node)
                    root.add(worktreeNode)
                    for (child in row.children) {
                        worktreeNode.add(build(child, settings, toExpand))
                    }
                    if (row.node.worktree.path in expanded) toExpand.add(0, worktreeNode)
                }
            }
        }
        model.reload()
        tree.emptyText.text = "No worktree"
        // Sections and folders open by default; worktrees only reopen if they were open before.
        for (node in toExpand) tree.expandPath(TreePath(node.path))
    }

    private fun build(node: Node, settings: WorktreeDiffSettings, toExpand: MutableList<DefaultMutableTreeNode>): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(node)
        when (node) {
            is SectionNode -> {
                for (child in fileRows(node, settings)) treeNode.add(build(child, settings, toExpand))
                toExpand.add(treeNode)
            }
            is DirRow -> {
                for (child in node.node.children) treeNode.add(build(rowOf(child), settings, toExpand))
                toExpand.add(treeNode)
            }
            else -> {}
        }
        return treeNode
    }

    private fun openSelected() {
        when (val node = selectedNode()) {
            is FileRow -> openDiff(project, node.node)
            is WorktreeNode, is DirRow, is SectionNode -> {
                val path = tree.selectionPath ?: return
                if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
            }
            else -> {}
        }
    }

    override fun dispose() {
        if (BoardService.getInstance(project).worktreesPanel === this) BoardService.getInstance(project).worktreesPanel = null
    }

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
            when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
                is WorktreeNode -> {
                    icon = if (node.worktree.isMain) AllIcons.Nodes.HomeFolder else AllIcons.Vcs.Branch
                    append(node.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    val counts = if (node.ahead != null && node.behind != null) "  ↑${node.ahead} ↓${node.behind}" else ""
                    val locked = if (node.worktree.locked) "  (locked)" else ""
                    append("  ${node.branch}$counts$locked", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    toolTipText = "${node.worktree.path}\nbase: ${node.base}"
                }
                is SectionNode -> {
                    icon = if (node.kind == SectionKind.BASE) AllIcons.Actions.Diff else AllIcons.Actions.Edit
                    append(node.label)
                    append("  ${node.files.size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is DirRow -> {
                    icon = AllIcons.Nodes.Folder
                    append(node.node.label)
                    toolTipText = node.node.relPath
                }
                is FileRow -> {
                    val file = node.node.file
                    val name = file.path.substringAfterLast('/')
                    icon = FileTypeManager.getInstance().getFileTypeByFileName(name).icon
                    append(name, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, statusOf(file.kind).color))
                    if (node.node.folderHint.isNotEmpty()) append("  ${node.node.folderHint}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    toolTipText = if (file.oldPath != null) "${file.oldPath} → ${file.path}" else file.path
                }
                is MessageNode -> {
                    icon = node.icon
                    append(node.label, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                else -> {}
            }
        }

        private fun statusOf(kind: ChangeKind): FileStatus = when (kind) {
            ChangeKind.ADDED -> FileStatus.ADDED
            ChangeKind.UNTRACKED -> FileStatus.UNKNOWN
            ChangeKind.DELETED -> FileStatus.DELETED
            ChangeKind.CONFLICTED -> FileStatus.MERGED_WITH_CONFLICTS
            ChangeKind.MODIFIED, ChangeKind.RENAMED, ChangeKind.COPIED, ChangeKind.TYPE_CHANGED -> FileStatus.MODIFIED
        }
    }

    companion object {
        /** Shows the change as a diff: the left side read straight out of git, nothing written to disk. */
        fun openDiff(project: Project, node: FileNode) {
            val cwd = node.worktree.path
            val rel = node.file.path
            val name = rel.substringAfterLast('/')
            val worktreeName = Paths.get(cwd).name
            val kind = node.file.kind
            bg {
                // An added or untracked file has no left-hand side; a deleted one has no right-hand side.
                val leftText = if (kind == ChangeKind.ADDED || kind == ChangeKind.UNTRACKED) "" else Git.showFile(cwd, node.leftRef, node.file.oldPath ?: rel)
                ui {
                    val factory = DiffContentFactory.getInstance()
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(name)
                    val left = factory.create(project, leftText, fileType)
                    val right = if (kind == ChangeKind.DELETED) {
                        factory.createEmpty()
                    } else {
                        virtualFileOf("$cwd/$rel")?.let { factory.create(project, it) } ?: factory.createEmpty()
                    }
                    val leftTitle = if (node.leftRef.isEmpty()) "(none)" else node.leftRef.take(7)
                    DiffManager.getInstance().showDiff(project, SimpleDiffRequest("$name ($worktreeName)", left, right, leftTitle, worktreeName))
                }
            }
        }

        fun openFile(project: Project, node: FileNode) {
            val file = virtualFileOf("${node.worktree.path}/${node.file.path}") ?: return
            ui { OpenFileDescriptor(project, file).navigate(true) }
        }

        fun expandAll(tree: Tree) = TreeUtil.expandAll(tree)
    }
}

@Suppress("unused")
private val viewModes = ViewMode.entries
