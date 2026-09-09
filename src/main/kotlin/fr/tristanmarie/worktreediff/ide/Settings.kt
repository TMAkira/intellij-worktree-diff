package fr.tristanmarie.worktreediff.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import fr.tristanmarie.worktreediff.core.PromptLanguage
import fr.tristanmarie.worktreediff.core.ViewMode

enum class AgentLaunch { OFF, ASK }

enum class ClaudeLaunch {
    /** Open a terminal tab in the worktree and run `claude` with the prompt as its argument. */
    TERMINAL,
    /** Open a terminal tab in the worktree, run `claude`, and put the prompt on the clipboard. */
    CLIPBOARD,
}

/**
 * The plugin's settings, per project: which base every worktree is compared against is a
 * property of the repository, not of the machine.
 */
@Service(Service.Level.PROJECT)
@State(name = "WorktreeDiffSettings", storages = [Storage("worktreeDiff.xml")])
class WorktreeDiffSettings : SimplePersistentStateComponent<WorktreeDiffSettings.State>(State()) {

    class State : BaseState() {
        /** Ref every worktree is compared against. The diff uses the merge base (base...HEAD). */
        var base by string("origin/main")
        var includeMainWorktree by property(true)
        var showUncommitted by property(true)
        var autoRefresh by property(true)
        var viewMode by enum(ViewMode.TREE)
        var compactFolders by property(true)
        var promptLanguage by enum(PromptLanguage.EN)
        /** Where `Start New Work` puts the worktree it creates, relative to the repository root. */
        var worktreePattern by string("../\${repo}-wt-\${change}")
        /** Branch name proposed for a new worktree, editable each time. */
        var branchPattern by string("feature/\${change}")
        var agentLaunch by enum(AgentLaunch.OFF)
        var claudeLaunch by enum(ClaudeLaunch.TERMINAL)
        /** The Claude Code executable, when it is not on the PATH the terminal sees. */
        var claudeCommand by string("claude")
    }

    val base: String get() = state.base?.takeIf { it.isNotBlank() } ?: "origin/main"
    val worktreePattern: String get() = state.worktreePattern?.takeIf { it.isNotBlank() } ?: "../\${repo}-wt-\${change}"
    val branchPattern: String get() = state.branchPattern?.takeIf { it.isNotBlank() } ?: "feature/\${change}"
    val claudeCommand: String get() = state.claudeCommand?.takeIf { it.isNotBlank() } ?: "claude"
    val launchEnabled: Boolean get() = state.agentLaunch == AgentLaunch.ASK

    companion object {
        fun getInstance(project: Project): WorktreeDiffSettings = project.service()
    }
}

class WorktreeDiffConfigurable(private val project: Project) : BoundConfigurable("Worktree Diff") {
    override fun createPanel() = panel {
        val settings = WorktreeDiffSettings.getInstance(project)
        val state = settings.state
        group("Worktrees") {
            row("Base ref:") {
                textField()
                    .columns(30)
                    .bindText({ state.base ?: "" }, { state.base = it })
                    .comment("Every worktree is compared against this ref, using the merge base (base...HEAD), like a pull request.")
            }
            row { checkBox("Show the main working tree, not only the linked worktrees").bindSelected(state::includeMainWorktree) }
            row { checkBox("Show a second section per worktree with its uncommitted changes").bindSelected(state::showUncommitted) }
            row { checkBox("Refresh when the IDE regains focus or a file is saved").bindSelected(state::autoRefresh) }
            row("Layout:") {
                comboBox(ViewMode.entries)
                    .bindItem({ state.viewMode }, { state.viewMode = it ?: ViewMode.TREE })
                    .comment("Tree groups changed files under their folders; list shows one flat row per file.")
            }
            row { checkBox("Collapse chains of single-child folders into one row").bindSelected(state::compactFolders) }
        }
        group("Claude Code") {
            row("Prompt language:") {
                comboBox(PromptLanguage.entries).bindItem({ state.promptLanguage }, { state.promptLanguage = it ?: PromptLanguage.EN })
            }
            row("Launch through:") {
                comboBox(ClaudeLaunch.entries)
                    .bindItem({ state.claudeLaunch }, { state.claudeLaunch = it ?: ClaudeLaunch.TERMINAL })
                    .comment(
                        "Terminal: a terminal tab opens in the worktree and runs claude with the prompt. " +
                            "Clipboard: the tab runs claude and the prompt is copied for you to paste.",
                    )
            }
            row("Claude command:") {
                textField().columns(30).bindText({ state.claudeCommand ?: "claude" }, { state.claudeCommand = it })
            }
            row("Agent launch requests:") {
                comboBox(AgentLaunch.entries)
                    .bindItem({ state.agentLaunch }, { state.agentLaunch = it ?: AgentLaunch.OFF })
                    .comment(
                        "Off: only the tool window buttons open Claude sessions; a request written by an agent is refused. " +
                            "Ask: a session may request a worktree through a JSON file under .worktree-diff/requests/; " +
                            "its prompt opens in a tab and nothing happens until you approve.",
                    )
            }
        }
        group("New work") {
            row("Worktree pattern:") {
                textField()
                    .columns(30)
                    .bindText({ state.worktreePattern ?: "" }, { state.worktreePattern = it })
                    .comment("Relative to the repository root. \${repo} is the repository directory name, \${change} the OpenSpec change name.")
            }
            row("Branch pattern:") {
                textField().columns(30).bindText({ state.branchPattern ?: "" }, { state.branchPattern = it })
            }
        }
    }

    override fun apply() {
        super.apply()
        BoardService.getInstance(project).settingsChanged()
    }
}
