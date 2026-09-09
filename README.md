# Worktree Diff for JetBrains IDEs

Shows what every git worktree **contributes relative to a base branch**, in one tree, in the
window you already have open. A port of the [VS Code extension](https://github.com/TMAkira/vscode-worktree-diff)
of the same name, for IntelliJ IDEA, Rider, WebStorm, PyCharm and the other IntelliJ-based IDEs.

The IDE's own Git tool window answers a different question: "what is not committed here?". When
your work lives in linked worktrees, that panel is empty most of the time: the changes are
committed, they are just not on `main` yet. This plugin answers the question you actually
have: *what does this branch add?*

## What you get

A **DevFlow** tool window with two tabs.

### Worktrees

One node per worktree, with its branch and its position against the base.

```
main                       main  ↑0 ↓0
  Uncommitted                        7
document-tabs              feature/document-tabs  ↑17 ↓0
  vs origin/main                    59
    .github/workflows
    docs/architecture-decisions
    front-end
    openspec/changes/in-app-document-tabs
    CLAUDE.md
```

- The `vs <base>` section is `git diff --name-status base...HEAD`: three dots, so it uses the
  **merge base**. You see what the branch adds, never what landed on `main` in the meantime.
  This is exactly what a pull request shows.
- The `Uncommitted` section is `git status` for that worktree.
- A worktree with a single non-empty section skips the section level entirely.
- Changed files are grouped under their folders, and chains of single-child folders collapse
  into one row. The toolbar toggle switches to a flat list.
- Double-click a file (or press Enter) for a diff. The left-hand side is read straight out of
  git, so nothing is written to disk.
- The context menu on a worktree opens it as a project in a new window, reveals it in the file
  manager, opens its OpenSpec detail, or opens a Claude Code session in it.

### OpenSpec

One compact card per worktree with its branch, ahead/behind, aggregate task progress and the
OpenSpec changes it works on, read from `openspec/changes/<name>/` (`tasks.md`, `proposal.md`,
`design.md`, `specs/`). Clicking a card opens the detail as an **editor tab**, with the room a
task list needs: every change with its badges, its tasks laid out in columns, and its markdown
artifacts. Clicking a task jumps to that line of `tasks.md`.

**Plain OpenSpec is enough.** The [aria](https://github.com/akira-sarl) plugin is optional and
contributes exactly one file, `aria-meta.md`; when it is present its impact level, exec mode,
baseline and simplify state appear in a separate *aria* block on the change card. The plugin
itself is detected in `~/.claude/plugins/cache/*/aria/*/`, and that detection only decides
whether the generated prompts may name aria's own workflows.

**Which changes belong to a worktree.** Every worktree carries a full copy of
`openspec/changes`, and a worktree left behind carries a stale one. So a linked worktree shows
only the changes its branch *modifies*, taken from `diff base...HEAD`. The main worktree has no
branch of its own to diff, so it shows every active change: the backlog. A change the main
checkout carries but git has never seen is a **draft**, listed with the one action it can take.

**Derived, never remembered.** Each card lists the gate commands its CI pipeline actually runs,
read from `.github/workflows/ci*.yml` or `.azuredevops/pipelines/ci*.yml` on every refresh, and
flags a fresh test report that is older than a test source it covers.

## Claude Code

Every "Continue with Claude", "Open Claude here" and "Start new work" button opens a Claude
Code session **in a terminal tab started in the right worktree**, with a prompt that says where
the work lives, what the branch already contains and which tasks remain. The prompt is written
in English or French (settings).

The prompt is handed to `claude` as its first argument through a temporary file, so it works
with bash, zsh, fish and PowerShell as the terminal shell. With `cmd.exe`, or if you prefer,
switch *Launch through* to *Clipboard* in the settings: the tab runs `claude` and the prompt is
copied for you to paste.

### Start new work

Two steps, on purpose. **Start new work** hands Claude the framing of a new piece of work: it
interviews you and writes an OpenSpec change in the main checkout, uncommitted. Once you have
read the proposal, **Create worktree** on the draft creates the branch, the worktree, moves the
change directory into it and makes the first commit. Nothing is branched before a human has
read the proposal.

### Agent launch requests

With *Agent launch requests* set to *Ask*, a Claude session may ask for the worktree that
implements the change it just specified, or for a fresh session in the worktree already
implementing one (its own context saturating, a night run picked up in the morning). It writes
one JSON file under `.worktree-diff/requests/` in the main checkout:

```json
{"change": "<change-name>", "branch": "<branch>", "from": "<its ListAgents name>",
 "prompt": "<the prompt the implementer will receive>", "reason": "<one line>"}
```

Only `change` is required. The plugin reads the repository to tell a creation from a resume,
opens the prompt in an editor tab with a header saying what would happen, and shows a
notification with **Launch** and **Reject**. Nothing is created until you answer; the tab is
what gets sent, edits included. The answer lands beside the request as `<name>.result.json`.
Requests nobody answered are listed in the OpenSpec tab and can be reviewed again from there.

The queue directory is added to `.git/info/exclude`, so it never shows up as untracked.

## Settings

*Settings | Tools | Worktree Diff*, per project.

| Setting | Default | What it does |
|---|---|---|
| Base ref | `origin/main` | Ref every worktree is compared against, through the merge base |
| Show main working tree | on | Also show the main checkout, not only the linked worktrees |
| Show uncommitted | on | Second section per worktree with `git status` |
| Auto refresh | on | Refresh when the IDE regains focus or a file is saved |
| Layout | tree | Group changed files under folders, or one flat row per file |
| Compact folders | on | Collapse chains of single-child folders into one row |
| Prompt language | en | Language of the generated Claude prompts |
| Launch through | terminal | Terminal tab with the prompt as argument, or clipboard |
| Claude command | `claude` | The executable, when it is not on the terminal's PATH |
| Agent launch requests | off | Whether an agent may ask for a worktree through the queue |
| Worktree pattern | `../${repo}-wt-${change}` | Where a new worktree goes, relative to the repository root |
| Branch pattern | `feature/${change}` | Branch name proposed for a new worktree |

## Building

Requires a JDK 21.

```
./gradlew buildPlugin        # build/distributions/intellij-worktree-diff-<version>.zip
./gradlew test               # unit tests; the git ones need git on the PATH
./gradlew runIde             # a sandboxed IntelliJ IDEA Community with the plugin installed
./gradlew verifyPlugin       # binary compatibility against the recommended IDE builds
```

Install the zip through *Settings | Plugins | ⚙ | Install Plugin from Disk…*. The plugin
needs an IDE of the 2024.3 line or newer, and the bundled Terminal plugin. The OpenSpec tab
renders through the IDE's embedded browser (JCEF), which every JetBrains runtime ships; the
Worktrees tab and every action work without it.

## Layout

```
src/main/kotlin/fr/tristanmarie/worktreediff/
  core/    git, OpenSpec and aria parsing, gates, board, prompts — no IDE dependency
  ide/     settings, services, tool window, tree, actions, launch queue, Claude launcher
  web/     the three HTML pages and the JCEF bridge that hosts them
```

## License

MIT
