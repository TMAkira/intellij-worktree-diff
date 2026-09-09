package fr.tristanmarie.worktreediff.ide

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Persistent UI state for the web panels: which worktrees are folded, which filter is on.
 *
 * Kept in the workspace file rather than in the page, because a page dies with its browser:
 * closing a detail tab, or the tool window being hidden long enough, would otherwise lose
 * the toggles the user set deliberately. Workspace scope, not application: which worktrees
 * are folded is a property of this project.
 */
@Service(Service.Level.PROJECT)
@State(name = "WorktreeDiffUiState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class UiState : PersistentStateComponent<UiState.Model> {
    class Model {
        /** JSON blobs keyed by panel, exactly as the page handed them over. */
        var panels: MutableMap<String, String> = HashMap()
    }

    private var model = Model()

    override fun getState(): Model = model

    override fun loadState(state: Model) {
        model = state
    }

    fun get(key: String): String = model.panels[key] ?: "{}"

    fun set(key: String, json: String) {
        model.panels[key] = json
    }

    companion object {
        const val SIDEBAR = "sidebar"
        const val DETAIL = "detail"
        const val ARIA_INFO = "ariaInfo"

        fun getInstance(project: Project): UiState = project.service()
    }
}
