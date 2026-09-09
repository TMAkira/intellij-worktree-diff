package fr.tristanmarie.worktreediff.core

enum class ViewMode { TREE, LIST }

sealed interface TreeChild

class DirNode(
    val worktree: Worktree,
    /** Path relative to the worktree root. */
    val relPath: String,
    val label: String,
    val children: List<TreeChild>,
) : TreeChild

class FileNode(
    val worktree: Worktree,
    val file: ChangedFile,
    /** Full sha the left-hand side of the diff is read from, or "" for "no left side". */
    val leftRef: String,
    /** Shown after the name. Empty in tree mode, where the parent already says it. */
    val folderHint: String,
) : TreeChild

private class DirDraft {
    val dirs = LinkedHashMap<String, DirDraft>()
    val files = mutableListOf<ChangedFile>()
}

private fun baseName(path: String): String = path.substringAfterLast('/')

private fun dirName(path: String): String = if ('/' in path) path.substringBeforeLast('/') else ""

/**
 * Turns a flat list of changed paths into folder nodes.
 *
 * With `compact`, a folder whose only child is another folder is merged into it, so a change
 * buried under front-end/src/js/pages costs one row rather than four.
 */
fun buildFileNodes(
    worktree: Worktree,
    files: List<ChangedFile>,
    leftRef: String,
    mode: ViewMode,
    compact: Boolean,
): List<TreeChild> {
    if (mode == ViewMode.LIST) {
        return files.map { FileNode(worktree, it, leftRef, dirName(it.path)) }
    }

    val root = DirDraft()
    for (file in files) {
        val segments = file.path.split('/')
        if (segments.isEmpty()) continue
        var cursor = root
        for (segment in segments.dropLast(1)) {
            cursor = cursor.dirs.getOrPut(segment) { DirDraft() }
        }
        cursor.files.add(file)
    }

    fun convert(draft: DirDraft, prefix: String): List<TreeChild> {
        val dirNodes = mutableListOf<DirNode>()
        for ((name, child) in draft.dirs) {
            var label = name
            var cursor = child
            var relPath = if (prefix.isEmpty()) name else "$prefix/$name"
            // Merge single-child folder chains: front-end + src + js -> front-end/src/js.
            while (compact && cursor.files.isEmpty() && cursor.dirs.size == 1) {
                val (onlyName, onlyChild) = cursor.dirs.entries.first()
                label = "$label/$onlyName"
                relPath = "$relPath/$onlyName"
                cursor = onlyChild
            }
            dirNodes.add(DirNode(worktree, relPath, label, convert(cursor, relPath)))
        }
        dirNodes.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val fileNodes = draft.files
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { baseName(it.path) })
            .map { FileNode(worktree, it, leftRef, "") }

        return dirNodes + fileNodes
    }

    return convert(root, "")
}
