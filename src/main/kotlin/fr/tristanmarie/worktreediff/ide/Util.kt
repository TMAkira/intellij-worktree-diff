package fr.tristanmarie.worktreediff.ide

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.nio.file.Paths

const val NOTIFICATION_GROUP = "Worktree Diff"

/** Runs `work` off the UI thread. */
fun bg(work: () -> Unit) {
    ApplicationManager.getApplication().executeOnPooledThread(work)
}

/** Runs `work` on the UI thread, later. */
fun ui(work: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(work, ModalityState.any())
}

fun notify(project: Project?, content: String, type: NotificationType = NotificationType.INFORMATION, vararg actions: Pair<String, () -> Unit>) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(content, type)
    for ((label, run) in actions) {
        notification.addAction(NotificationAction.createSimpleExpiring(label) { run() })
    }
    notification.notify(project)
}

fun notifyError(project: Project?, content: String) = notify(project, content, NotificationType.ERROR)

fun notifyWarning(project: Project?, content: String, vararg actions: Pair<String, () -> Unit>) =
    notify(project, content, NotificationType.WARNING, *actions)

fun virtualFileOf(path: String): VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(path))

/** Opens a file in an editor, optionally scrolled to a 0-based line. */
fun openAt(project: Project, path: String, line: Int? = null) {
    val file = virtualFileOf(path) ?: run {
        notifyWarning(project, "File not found: $path")
        return
    }
    ui {
        if (line != null) OpenFileDescriptor(project, file, line, 0).navigate(true)
        else OpenFileDescriptor(project, file).navigate(true)
    }
}

fun message(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: e.toString()

fun exists(path: String): Boolean = java.nio.file.Files.exists(Paths.get(path))

fun nioPath(path: String): Path = Paths.get(path)
