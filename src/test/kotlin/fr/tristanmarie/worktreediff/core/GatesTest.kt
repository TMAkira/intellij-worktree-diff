package fr.tristanmarie.worktreediff.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GatesTest {
    @Test
    fun `gates are read from the CI pipeline only, comments excluded`() {
        val root = Files.createTempDirectory("gates")
        Files.createDirectories(root.resolve(".github/workflows"))
        Files.writeString(
            root.resolve(".github/workflows/ci.yml"),
            """
            jobs:
              build:
                steps:
                  - run: npm ci
                  - run: npm run test:unit
                  # dotnet test used to take a project path
                  - run: dotnet test --no-build
            """.trimIndent(),
        )
        Files.writeString(root.resolve(".github/workflows/cd-backend.yml"), "run: dotnet publish\nrun: npm run deploy\n")
        val gates = discoverGates(root.toString())
        assertEquals(listOf("npm ci", "npm run test:unit", "dotnet test --no-build"), gates.map { it.command })
        assertTrue(gates.all { it.source == ".github/workflows/ci.yml" })
        root.toFile().deleteRecursively()
    }

    @Test
    fun `a fresh report older than its test source is flagged`() {
        val root = Files.createTempDirectory("stale")
        val project = root.resolve("Tests")
        Files.createDirectories(project.resolve("TestResults"))
        val report = project.resolve("TestResults/run.trx")
        Files.writeString(report, "<xml/>")
        Thread.sleep(20)
        val source = project.resolve("FooTests.cs")
        Files.writeString(source, "class FooTests {}")
        Files.setLastModifiedTime(source, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(report).toMillis() + 120_000))

        val stale = staleReports(root.toString())
        assertEquals(1, stale.size)
        assertEquals("Tests/TestResults/run.trx", stale[0].report)
        assertEquals("Tests/FooTests.cs", stale[0].source)
        assertEquals(2L, stale[0].behind)
        root.toFile().deleteRecursively()
    }
}
