package fr.tristanmarie.worktreediff.ide

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

/**
 * Opens a Claude Code session with a prompt, in a worktree.
 *
 * The JetBrains Claude Code plugin exposes no action that takes a prompt, so the session is
 * started the way it would be by hand: a terminal tab, opened in the worktree, running the
 * CLI. That is one thing the VS Code extension could not do: its `claude-vscode.editor.open`
 * command takes a prompt and never a working directory, so every prompt there has to say
 * where the work lives and hope. Here the tab starts in the right directory.
 *
 * The prompt is passed through a file rather than quoted inline: a prompt runs to several
 * kilobytes with quotes and newlines, and the shell the terminal runs is the user's, not ours.
 */
object ClaudeLauncher {
    private val log = logger<ClaudeLauncher>()

    fun launch(project: Project, prompt: String, workingDirectory: String) {
        val settings = WorktreeDiffSettings.getInstance(project)
        val cwd = if (exists(workingDirectory)) workingDirectory else project.basePath ?: workingDirectory
        val tabName = "claude · ${Paths.get(cwd).name}"

        when (settings.state.claudeLaunch) {
            ClaudeLaunch.CLIPBOARD -> {
                copy(prompt)
                runInTerminal(project, cwd, tabName, settings.claudeCommand)
                notify(project, "Claude Code is starting in $tabName. The prompt is on the clipboard: paste it into the session.")
            }
            ClaudeLaunch.TERMINAL -> {
                val file = writePromptFile(prompt)
                val command = commandFor(shellPath(project), settings.claudeCommand, file)
                if (command == null) {
                    copy(prompt)
                    runInTerminal(project, cwd, tabName, settings.claudeCommand)
                    notifyWarning(
                        project,
                        "This terminal shell cannot hand a file to claude as an argument, so the prompt was copied to the clipboard instead. Paste it into the session.",
                    )
                    return
                }
                runInTerminal(project, cwd, tabName, command)
            }
        }
    }

    private fun copy(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private fun runInTerminal(project: Project, cwd: String, tabName: String, command: String) {
        ui {
            try {
                val widget = TerminalToolWindowManager.getInstance(project).createShellWidget(cwd, tabName, true, true)
                widget.sendCommandToExecute(command)
            } catch (e: Exception) {
                log.warn("Could not open a terminal for Claude", e)
                notifyError(project, "Could not open a terminal tab: ${message(e)}")
            }
        }
    }

    private fun writePromptFile(prompt: String): Path {
        val dir = Files.createTempDirectory("worktree-diff")
        val file = dir.resolve("prompt.md")
        Files.writeString(file, prompt)
        return file
    }

    private fun shellPath(project: Project): String =
        try {
            TerminalProjectOptionsProvider.getInstance(project).shellPath
        } catch (e: Exception) {
            log.warn("Could not read the terminal shell path", e)
            ""
        }

    /**
     * The one-line command that runs claude with the file's contents as its argument, for the
     * shell the terminal will open. Null when the shell is one this cannot be written for.
     */
    internal fun commandFor(shellPath: String, claude: String, file: Path): String? {
        val shell = Paths.get(shellPath.trim().trim('"')).name.lowercase().removeSuffix(".exe")
        val native = file.toString()
        // Git Bash, MSYS and WSL all take forward slashes; PowerShell wants the native form.
        val posix = native.replace('\\', '/')
        return when {
            shell.startsWith("pwsh") || shell.startsWith("powershell") -> powershell(claude, native)
            shell == "fish" -> "$claude (cat '${posixQuote(posix)}' | string collect)"
            shell == "cmd" -> null
            shell.isEmpty() && SystemInfo.isWindows -> powershell(claude, native)
            // bash, zsh, sh, dash, and anything else POSIX-shaped.
            else -> "$claude \"\$(cat '${posixQuote(posix)}')\""
        }
    }

    /**
     * Before PowerShell 7.3 a native command's argument is handed over with its embedded double
     * quotes unescaped, so they have to be escaped by hand; from 7.3 on, escaping them again
     * would double the backslashes. The check runs in the shell, where the version is known.
     */
    private fun powershell(claude: String, path: String): String {
        val quoted = path.replace("'", "''")
        return "\$p = Get-Content -Raw -LiteralPath '$quoted'; " +
            "if (\$PSVersionTable.PSVersion -lt [version]'7.3' -or \$PSNativeCommandArgumentPassing -eq 'Legacy') { \$p = \$p -replace '\"','\\\"' }; " +
            "$claude \$p"
    }

    private fun posixQuote(path: String): String = path.replace("'", "'\\''")
}
