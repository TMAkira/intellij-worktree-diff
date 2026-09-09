package fr.tristanmarie.worktreediff.core

import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneOffset
import kotlin.io.path.isDirectory
import kotlin.io.path.name

data class AriaTask(
    val done: Boolean,
    var label: String,
    /** 0-based line in tasks.md, so the view can jump straight to it. */
    val line: Int,
)

data class AriaSection(val title: String, val tasks: MutableList<AriaTask> = mutableListOf())

/** The classification table of an aria-meta.md. Absent keys stay null. */
data class AriaMeta(
    val impactLevel: String? = null,
    val execMode: String? = null,
    /** Just the EASY/MEDIUM/HARD token, when the exec mode sentence carries one. For badges. */
    val execModeLabel: String? = null,
    val baseline: String? = null,
    val simplify: String? = null,
    /**
     * `Spec session` from aria-meta.md: the Claude session that authored this change, so the
     * implementer spawned on it can address its observer by name.
     */
    val specSession: String? = null,
)

/**
 * What aria leaves on disk for one change. Every field beyond `name`, `dir` and the task
 * counts is optional: the parser reports what it finds and stays quiet about the rest rather
 * than inventing a state.
 */
data class AriaChange(
    val name: String,
    /** Absolute directory of the change, with forward slashes. */
    val dir: String,
    val sections: List<AriaSection>,
    val done: Int,
    val total: Int,
    /** File names present in the change directory, e.g. proposal.md, design.md, specs. */
    val artifacts: List<String>,
    val impactLevel: String? = null,
    val execMode: String? = null,
    val execModeLabel: String? = null,
    val specSession: String? = null,
    val baseline: String? = null,
    val simplify: String? = null,
    /** True when every task is checked but the change still sits outside archive/. */
    val readyToArchive: Boolean,
    /** `created:` from .openspec.yaml, when the file carries one (YYYY-MM-DD). */
    val created: String? = null,
    /** Last modification of tasks.md, as an ISO date. Always available, unlike `created`. */
    val updated: String? = null,
    /**
     * True when git tracks nothing under the change directory: it was written but never
     * committed. Only ever set for the main checkout.
     */
    val draft: Boolean = false,
)

data class AriaPlugin(
    val installed: Boolean,
    val version: String? = null,
    /** Skill names found under the plugin, e.g. design, plan, exec, opsx. */
    val skills: List<String> = emptyList(),
)

private val TASK_LINE = Regex("""^\s*-\s+\[([ xX])\]\s+(.*)$""")
private val SECTION_LINE = Regex("""^##\s+(.*)$""")
/** A `| **Key** | value |` row of the classification table in aria-meta.md. */
private val META_ROW = Regex("""^\|\s*\*{0,2}([^|*]+?)\*{0,2}\s*\|\s*(.+?)\s*\|\s*$""")
private val CONTINUATION = Regex("""^\s+\S""")
private val EXEC_MODE_TOKEN = Regex("""\b(EASY|MEDIUM|HARD)\b""")
private val CREATED = Regex("""^\s*created:\s*(\S+)""", RegexOption.MULTILINE)

/** Strips the markdown that would otherwise reach the UI as literal asterisks and backticks. */
fun plainMarkdown(text: String): String = text
    .replace(Regex("~~(.+?)~~"), "$1")
    .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    .replace(Regex("`(.+?)`"), "$1")
    .replace(Regex("""\[(.+?)]\(.+?\)"""), "$1")
    .trim()

/**
 * Reads the checkbox list of a tasks.md.
 *
 * Tasks that wrap onto indented continuation lines are joined back into one label: aria
 * wraps them at 80 columns, and a truncated first line reads as a different task.
 */
fun parseTasks(markdown: String): List<AriaSection> {
    val lines = markdown.lines()
    val sections = mutableListOf<AriaSection>()
    var current = AriaSection("Tasks")
    var openTask: AriaTask? = null

    fun commit() {
        if (current.tasks.isNotEmpty()) sections.add(current)
    }

    for ((i, line) in lines.withIndex()) {
        val section = SECTION_LINE.find(line)
        if (section != null) {
            commit()
            current = AriaSection(plainMarkdown(section.groupValues[1]))
            openTask = null
            continue
        }
        val task = TASK_LINE.find(line)
        if (task != null) {
            val created = AriaTask(task.groupValues[1].lowercase() == "x", plainMarkdown(task.groupValues[2]), i)
            current.tasks.add(created)
            openTask = created
            continue
        }
        // An indented, non-empty line right after a task continues its label.
        val open = openTask
        if (open != null && CONTINUATION.containsMatchIn(line)) {
            open.label = (open.label + " " + plainMarkdown(line)).trim()
            continue
        }
        if (line.isBlank()) openTask = null
    }
    commit()
    return sections
}

/** Where an address stops and the warning that follows it begins. */
private val ADDRESS_STOPS = listOf("—", "–", " - ", ":", ",", ";", "(")

/** A cell that opens with one of these is telling the reader not to use it as an address. */
private val NOT_AN_ADDRESS = listOf("⚠", "❗", "⁉")

/**
 * The address at the head of a `Spec session` cell, when the cell holds one at all. Only the
 * head is kept, and only when it still looks like something ListAgents could return.
 */
private fun addressOf(value: String): String? {
    var head = value
    for (stop in ADDRESS_STOPS) {
        val at = head.indexOf(stop)
        if (at > 0) head = head.substring(0, at)
    }
    head = head.trim()
    val disowned = NOT_AN_ADDRESS.any { head.contains(it) } || Regex("^(no|aucun)", RegexOption.IGNORE_CASE).containsMatchIn(head)
    return if (head.isNotEmpty() && head.length <= 60 && !disowned) head else null
}

/** Reads the classification table of an aria-meta.md. */
fun parseMeta(markdown: String): AriaMeta {
    var meta = AriaMeta()
    for (line in markdown.lines()) {
        val row = META_ROW.find(line) ?: continue
        val key = plainMarkdown(row.groupValues[1]).lowercase()
        val value = plainMarkdown(row.groupValues[2])
        if (value.isEmpty() || value.startsWith("---")) continue
        meta = when (key) {
            "impact level" -> meta.copy(impactLevel = value)
            // "MEDIUM — stop after each task for review" is a sentence; a badge wants the token.
            "exec mode" -> meta.copy(execMode = value, execModeLabel = EXEC_MODE_TOKEN.find(value)?.groupValues?.get(1))
            "spec session" -> meta.copy(specSession = addressOf(value))
            "baseline" -> meta.copy(baseline = value)
            "simplify" -> meta.copy(simplify = value)
            else -> meta
        }
    }
    return meta
}

internal fun readIfPresent(file: Path): String? =
    try {
        Files.readString(file)
    } catch (_: IOException) {
        null
    }

internal fun listDir(dir: Path): List<Path> =
    try {
        Files.list(dir).use { it.toList() }
    } catch (_: IOException) {
        emptyList()
    }

internal fun slashes(path: Path): String = path.toString().replace('\\', '/')

/**
 * Looks for the aria plugin in the Claude Code plugin cache
 * (`~/.claude/plugins/cache/<marketplace>/aria/<version>/`).
 *
 * The plugin works on plain OpenSpec. This detection only decides whether to name aria's
 * workflows in the prompts it writes, and whether to show the aria section.
 */
fun detectAriaPlugin(home: String): AriaPlugin {
    val cache = Paths.get(home, ".claude", "plugins", "cache")
    val marketplaces = listDir(cache).filter { it.isDirectory() }
    for (marketplace in marketplaces) {
        val ariaDir = marketplace.resolve("aria")
        val versions = listDir(ariaDir).filter { it.isDirectory() }.map { it.name }.sorted().reversed()
        // Several versions can sit side by side; the highest-sorting one is what runs.
        for (version in versions) {
            val manifest = readIfPresent(ariaDir.resolve(version).resolve(".claude-plugin").resolve("plugin.json")) ?: continue
            val skills = listDir(ariaDir.resolve(version).resolve("skills")).filter { it.isDirectory() }.map { it.name }.sorted()
            val declared = try {
                JsonParser.parseString(manifest).asJsonObject.get("version")?.asString
            } catch (_: Exception) {
                // A malformed manifest still proves the plugin is there; the folder names the version.
                null
            }
            return AriaPlugin(installed = true, version = declared ?: version, skills = skills)
        }
    }
    return AriaPlugin(installed = false)
}

/**
 * Scans `<root>/changes` for active changes. `archive/` is skipped: it holds the changes
 * that are done, and it outnumbers the active ones roughly three to one.
 */
fun scanChanges(openspecRoot: Path): List<AriaChange> {
    val changesDir = openspecRoot.resolve("changes")
    val entries = listDir(changesDir)
    val changes = mutableListOf<AriaChange>()

    for (entry in entries) {
        if (!entry.isDirectory() || entry.name == "archive") continue
        val tasksFile = entry.resolve("tasks.md")
        val sections = readIfPresent(tasksFile)?.let(::parseTasks) ?: emptyList()
        val all = sections.flatMap { it.tasks }
        val done = all.count { it.done }

        // .openspec.yaml is two or three flat keys; a full YAML parser would be overkill.
        val created = readIfPresent(entry.resolve(".openspec.yaml"))?.let { CREATED.find(it)?.groupValues?.get(1) }

        val updated = try {
            Files.getLastModifiedTime(tasksFile).toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString()
        } catch (_: IOException) {
            null
        }

        val meta = readIfPresent(entry.resolve("aria-meta.md"))?.let(::parseMeta) ?: AriaMeta()
        val artifacts = listDir(entry).map { it.name }.filter { !it.startsWith(".") }.sorted()

        changes.add(
            AriaChange(
                name = entry.name,
                dir = slashes(entry),
                sections = sections,
                done = done,
                total = all.size,
                artifacts = artifacts,
                impactLevel = meta.impactLevel,
                execMode = meta.execMode,
                execModeLabel = meta.execModeLabel,
                specSession = meta.specSession,
                baseline = meta.baseline,
                simplify = meta.simplify,
                readyToArchive = all.isNotEmpty() && done == all.size,
                created = created,
                updated = updated,
            ),
        )
    }

    // Most progressed first, so what is close to landing sits at the top.
    return changes.sortedWith(
        compareByDescending<AriaChange> { if (it.total == 0) 0.0 else it.done.toDouble() / it.total }
            .thenBy { it.name },
    )
}
