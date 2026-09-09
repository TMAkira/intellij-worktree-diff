package fr.tristanmarie.worktreediff.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class ClaudeLauncherTest {
    private val file = Paths.get("/tmp/it's here/prompt.md")

    /** The JDK spells the path with the host's separator; the POSIX command always gets slashes. */
    private val posixPath = file.toString().replace('\\', '/')

    @Test
    fun `posix shells read the file through command substitution`() {
        assertEquals("claude \"\$(cat '/tmp/it'\\''s here/prompt.md')\"", ClaudeLauncher.commandFor("/bin/zsh", "claude", file))
        assertEquals("claude \"\$(cat '${posixPath.replace("'", "'\\''")}')\"", ClaudeLauncher.commandFor("/usr/bin/bash", "claude", file))
    }

    @Test
    fun `fish has its own substitution`() {
        assertTrue(ClaudeLauncher.commandFor("/opt/homebrew/bin/fish", "claude", file)!!.startsWith("claude (cat "))
    }

    @Test
    fun `powershell escapes quotes only where the shell will not`() {
        val command = ClaudeLauncher.commandFor("C:\\Program Files\\PowerShell\\7\\pwsh.exe", "claude", file)!!
        assertTrue(command.startsWith("\$p = Get-Content -Raw -LiteralPath "))
        assertTrue(command.contains("PSVersion -lt [version]'7.3'"))
        assertTrue(command.endsWith("claude \$p"))
        assertTrue(ClaudeLauncher.commandFor("powershell.exe", "claude", file)!!.contains("Get-Content"))
    }

    @Test
    fun `cmd cannot do it`() {
        assertNull(ClaudeLauncher.commandFor("C:\\Windows\\system32\\cmd.exe", "claude", file))
    }
}
