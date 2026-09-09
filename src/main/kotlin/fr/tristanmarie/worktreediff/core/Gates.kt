package fr.tristanmarie.worktreediff.core

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Two mechanical checks the repository can answer on its own, and that a session's own report
 * cannot be trusted for: the gate list a CI pipeline actually runs, and a test report older
 * than the test source it claims to cover.
 */

/** One command a CI pipeline actually runs, as read from the pipeline file. */
data class Gate(val command: String, /** The pipeline file it was read from, relative to the worktree. */ val source: String)

/** A test report older than a test source it is supposed to cover. */
data class StaleReport(
    /** The report file, relative to the worktree. */
    val report: String,
    /** The test source that is newer than it, relative to the worktree. */
    val source: String,
    /** How far behind the report is, in minutes. */
    val behind: Long,
)

/** Where a pipeline that gates a pull request lives, by convention of the two hosts in use. */
private val PIPELINE_DIRS = listOf(".github/workflows", ".azuredevops/pipelines")

/** A pipeline file is read only when its name says it is the CI one. An allowlist, on purpose. */
private val CI_NAME = Regex("""^ci[.\-_]?.*\.ya?ml$""", RegexOption.IGNORE_CASE)

/** The shapes a gate command takes, across the toolchains in use. */
private val GATE_COMMANDS = Regex(
    """(?:npm|pnpm|yarn|bun)\s+(?:run\s+[\w:.-]+|test|ci)|dotnet\s+(?:test|build|format)(?:\s+[^\r\n|&;]*)?|(?:\./)?mvnw(?:\.cmd)?\s+(?:test|verify)(?:\s+[^\r\n|&;]*)?|(?:python\s+-m\s+)?pytest(?:\s+[^\r\n|&;]*)?|cargo\s+(?:test|clippy)(?:\s+[^\r\n|&;]*)?|make\s+[\w:.-]+""",
)

/** More than this and the list stops being readable. */
private const val MAX_GATES = 12

/** The gate commands the CI pipeline runs, derived from the repository every time. */
fun discoverGates(root: String): List<Gate> {
    val found = LinkedHashMap<String, Gate>()
    for (dir in PIPELINE_DIRS) {
        val pipelineDir = Paths.get(root, dir)
        for (file in listDir(pipelineDir)) {
            if (!CI_NAME.matches(file.name)) continue
            val source = "$dir/${file.name}"
            val text = readIfPresent(file) ?: continue
            for (match in GATE_COMMANDS.findAll(uncommented(text))) {
                val command = match.value.replace(Regex("\\s+"), " ").replace(Regex("""[\\>|]+$"""), "").trim()
                if (command.isNotEmpty() && command !in found) {
                    found[command] = Gate(command, source)
                }
            }
        }
    }
    return found.values.take(MAX_GATES)
}

/** Strips YAML comments, so a command discussed in prose is not read as a command that runs. */
private fun uncommented(text: String): String =
    text.lines().joinToString("\n") { it.replace(Regex("""(^|\s)#.*$"""), "$1") }

/** Directories a walk never enters: build output, dependencies, git internals. */
private val SKIP = setOf("node_modules", "bin", "obj", ".git", ".vs", "dist", "out", "target", ".next")

/** Where reports land, by convention of the runners in use. */
private val REPORT_DIRS = setOf("testresults", "test-results", "audits")

private val REPORT_FILE = Regex("""\.(trx|xml|json)$""", RegexOption.IGNORE_CASE)

/** What a test source file is named, across the toolchains in use. */
private val TEST_SOURCE = Regex(
    """(tests?\.(cs|java|kt)|\.(spec|test)\.(ts|tsx|js|jsx|mjs)|_test\.py|test_.*\.py|test\.(cs|java))$""",
    RegexOption.IGNORE_CASE,
)

/** A repository is not walked to its leaves on a side-bar refresh. */
private const val MAX_DEPTH = 6

/** Past this age, a report is an artefact and not a run, and its staleness says nothing. */
private const val FRESH_REPORT_DAYS = 7L

private data class Newest(val file: Path, val at: Long)

/**
 * Test reports that predate a test source they cover, scoped to the directory that owns the
 * report: the parent of `TestResults/` is the test project.
 */
fun staleReports(root: String): List<StaleReport> {
    val rootPath = Paths.get(root)
    val stale = mutableListOf<StaleReport>()
    val now = System.currentTimeMillis()
    for (dir in reportDirs(rootPath, 0)) {
        val report = newest(dir) { REPORT_FILE.containsMatchIn(it) } ?: continue
        if (now - report.at > FRESH_REPORT_DAYS * 86_400_000L) continue
        val project = dir.parent ?: continue
        val source = newest(project) { TEST_SOURCE.containsMatchIn(it) } ?: continue
        if (source.at <= report.at) continue
        stale.add(
            StaleReport(
                report = slashes(rootPath.relativize(report.file)),
                source = slashes(rootPath.relativize(source.file)),
                behind = Math.round((source.at - report.at) / 60_000.0),
            ),
        )
    }
    return stale
}

/** Directories that hold reports, found by name, without entering build output. */
private fun reportDirs(dir: Path, depth: Int): List<Path> {
    if (depth > MAX_DEPTH) return emptyList()
    val found = mutableListOf<Path>()
    for (entry in listDir(dir)) {
        if (!entry.isDirectory() || entry.name in SKIP) continue
        if (entry.name.lowercase() in REPORT_DIRS) {
            found.add(entry)
            // A report directory holds reports, not projects; nothing below it is a test source.
            continue
        }
        found.addAll(reportDirs(entry, depth + 1))
    }
    return found
}

/** The most recently modified file under `dir` whose name matches, or null. */
private fun newest(dir: Path, matches: (String) -> Boolean): Newest? {
    var best: Newest? = null
    fun walk(current: Path, depth: Int) {
        if (depth > MAX_DEPTH) return
        for (entry in listDir(current)) {
            if (entry.isDirectory()) {
                if (entry.name !in SKIP) walk(entry, depth + 1)
                continue
            }
            if (!matches(entry.name)) continue
            val at = try {
                Files.getLastModifiedTime(entry).toMillis()
            } catch (_: IOException) {
                continue
            }
            val current = best
            if (current == null || at > current.at) best = Newest(entry, at)
        }
    }
    walk(dir, 0)
    return best
}
