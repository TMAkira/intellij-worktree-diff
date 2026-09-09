package fr.tristanmarie.worktreediff.web

/** The detail of one worktree, as an editor tab: every change, its tasks, its artifacts. */
object DetailHtml {
    private const val CSS = """
body { padding: 20px 24px 40px; max-width: 1100px; }
h1 { font-size: 1.35em; margin: 0 0 2px; font-weight: 600; }
.head { margin-bottom: 18px; }
.head .meta { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 6px; }
.head .meta span { font-size: 0.9em; opacity: 0.8; }

.change { margin-top: 22px; }
.change > .card { padding: 14px 16px; }
.change h2 { font-size: 1.08em; margin: 0; font-weight: 600; }
.change .summary { display: flex; align-items: center; gap: 14px; margin-top: 10px; }
.change .summary .bar { flex: 1; margin: 0; }
.facts { display: flex; flex-wrap: wrap; gap: 6px 18px; margin-top: 10px; }
.facts span { font-size: 0.85em; opacity: 0.8; }
.files { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
.file { font-size: 0.85em; width: auto; color: var(--wd-link, #58a6ff); }
.file:hover { text-decoration: underline; }

.sections { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 18px; margin-top: 18px; }
.section h3 {
  font-size: 0.85em;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.7;
  margin: 0 0 6px;
  font-weight: 600;
}
.task {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 8px;
  padding: 4px 6px;
  border-radius: 4px;
  line-height: 1.45;
}
.task:hover { background: var(--wd-hover, rgba(128,128,128,0.12)); }
.task .box { opacity: 0.9; }
.task.done { opacity: 0.5; }
.task.done .box { color: var(--wd-success, #3fb950); opacity: 1; }
.task .text { cursor: pointer; }
.task .text:hover { color: var(--wd-link-active, #79b8ff); }

.controls { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; margin: 14px 0 0; }
.check { display: inline-flex; align-items: center; gap: 6px; font-size: 0.85em; opacity: 0.85; width: auto; }
.check .box { width: 13px; text-align: center; }
.check:hover { opacity: 1; }
.chev { display: inline-block; width: 14px; opacity: 0.7; }
.change h2 { cursor: pointer; }
.change .card.collapsed .summary { margin-top: 8px; }
.actions { display: flex; gap: 8px; margin-top: 12px; }
.action {
  width: auto;
  font-size: 0.85em;
  padding: 3px 10px;
  border-radius: 4px;
  border: 1px solid var(--wd-border, rgba(128,128,128,0.4));
  background: var(--wd-secondary-bg, transparent);
  color: var(--wd-fg);
}
.action.primary {
  background: var(--wd-button-bg, #0a7);
  color: var(--wd-button-fg, #fff);
  border-color: transparent;
}
.action:hover { filter: brightness(1.1); }

.aria {
  margin-top: 12px;
  padding: 8px 10px;
  border-left: 2px solid var(--wd-link, #58a6ff);
  background: var(--wd-quote-bg, rgba(128,128,128,0.07));
  border-radius: 0 4px 4px 0;
}
.aria .head { font-size: 0.75em; text-transform: uppercase; letter-spacing: 0.05em; opacity: 0.7; margin: 0 0 6px; }
.aria .facts { margin-top: 6px; }
.head .actions { margin-top: 12px; }
"""

    private const val SCRIPT = """
let last = null;
let collapsed = new Set();
let hideDone = false;

function persist() {
  host.post({ type: 'setUi', ui: { collapsed: [...collapsed], hideDone } });
}

function dates(change) {
  const out = [];
  if (change.created) { out.push('created ' + esc(change.created)); }
  if (change.updated) { out.push('tasks.md updated ' + esc(change.updated)); }
  return out.length ? '<div class="muted" style="margin-top:6px">' + out.join(' &middot; ') + '</div>' : '';
}

function ariaBlock(change) {
  if (!change.artifacts.includes('aria-meta.md')) { return ''; }
  const out = [];
  if (change.impactLevel) { out.push('Impact: ' + esc(change.impactLevel)); }
  if (change.execMode) { out.push('Exec: ' + esc(change.execMode)); }
  if (change.baseline) { out.push('Baseline: ' + esc(change.baseline)); }
  if (change.simplify) { out.push('Simplify: ' + esc(change.simplify)); }
  if (!out.length) { return ''; }
  return '<div class="aria"><div class="head">aria</div>' +
    '<div class="facts">' + out.map(f => '<span>' + f + '</span>').join('') + '</div></div>';
}

function badges(change) {
  const list = [];
  if (change.execModeLabel) { list.push('<span class="badge">' + esc(change.execModeLabel) + '</span>'); }
  if (change.draft) { list.push('<span class="badge warn">draft &middot; not committed</span>'); }
  if (change.readyToArchive) { list.push('<span class="badge done">ready to archive</span>'); }
  if (change.total === 0) { list.push('<span class="badge warn">no tasks.md</span>'); }
  return list.length ? '<div class="badges">' + list.join('') + '</div>' : '';
}

function sections(change) {
  if (!change.sections.length || collapsed.has(change.name)) { return ''; }
  const blocks = change.sections.map(section => {
    const visible = hideDone ? section.tasks.filter(t => !t.done) : section.tasks;
    if (!visible.length) { return ''; }
    const tasks = visible.map(task =>
      '<div class="task' + (task.done ? ' done' : '') + '">' +
      '<span class="box">' + (task.done ? '✓' : '○') + '</span>' +
      '<span class="text" data-open="' + esc(change.dir) + '/tasks.md" data-line="' + task.line + '">' +
      esc(task.label) + '</span></div>').join('');
    return '<div class="section"><h3>' + esc(section.title) + '</h3>' + tasks + '</div>';
  }).join('');
  return blocks ? '<div class="sections">' + blocks + '</div>' : '';
}

function actions(change) {
  const buttons = change.draft
    ? ['<button class="action primary" data-promote="' + esc(change.name) + '">Create worktree</button>',
       '<button class="action" data-ask="' + esc(change.name) + '" data-intent="continue">Continue with Claude</button>']
    : ['<button class="action primary" data-ask="' + esc(change.name) + '" data-intent="continue">' +
       (change.readyToArchive ? 'Ask Claude' : 'Continue with Claude') + '</button>'];
  if (change.readyToArchive) {
    buttons.push('<button class="action" data-ask="' + esc(change.name) + '" data-intent="archive">Archive…</button>');
  }
  return '<div class="actions">' + buttons.join('') + '</div>';
}

function checkbox(action, on, label) {
  return '<button class="check" data-toggle="' + action + '"><span class="box">' +
    (on ? '☑' : '☐') + '</span>' + esc(label) + '</button>';
}

function render(state) {
  last = state;
  const w = state.worktree;
  const changes = w.changes.map(change => {
    const isCollapsed = collapsed.has(change.name);
    const md = change.artifacts.filter(f => f.endsWith('.md')).map(f =>
      '<button class="file" data-open="' + esc(change.dir) + '/' + esc(f) + '">' + esc(f) + '</button>').join('');
    const ratio = change.total ? change.done / change.total : 0;
    return '<div class="change"><div class="card' + (isCollapsed ? ' collapsed' : '') + '">' +
      '<div class="row"><h2 data-collapse="' + esc(change.name) + '">' +
      '<span class="chev">' + (isCollapsed ? '▸' : '▾') + '</span>' + esc(change.name) + '</h2>' +
      '<span class="counts">' + change.done + ' / ' + change.total + ' tasks</span></div>' +
      '<div class="summary">' + bar(ratio, change.total > 0 && change.done === change.total) + '</div>' +
      badges(change) + dates(change) +
      (isCollapsed ? '' : ariaBlock(change) + (md ? '<div class="files">' + md + '</div>' : '') + actions(change)) +
      sections(change) +
      '</div></div>';
  }).join('');

  document.getElementById('app').innerHTML =
    '<div class="head"><h1>' + esc(w.name) + '</h1>' +
    '<div class="muted">' + esc(w.branch) + '</div>' +
    '<div class="meta">' +
    '<span>' + (w.isMain ? 'main working tree' : '↑' + w.ahead + ' ahead · ↓' + w.behind + ' behind ' + esc(state.base)) + '</span>' +
    (w.filesChanged ? '<span>' + w.filesChanged + ' files changed</span>' : '') +
    '<span>' + w.changes.length + ' change' + (w.changes.length === 1 ? '' : 's') + '</span>' +
    '</div>' +
    (w.error ? '<div class="error">' + esc(w.error) + '</div>' : '') +
    '<div class="actions">' +
    '<button class="action' + (w.changes.length ? '' : ' primary') + '" data-ask-worktree="1">' +
    'Open Claude here</button>' +
    (w.isMain ? '<button class="action" data-action="newwork">Start new work…</button>' : '') +
    '</div>' +
    (w.changes.length ? '<div class="controls">' +
      checkbox('hideDone', hideDone, 'Hide completed tasks') +
      checkbox('collapseAll', collapsed.size >= w.changes.length, 'Collapse all') +
      '</div>' : '') +
    '</div>' +
    (changes || '<div class="empty">No OpenSpec change on this branch — the button above still ' +
      'opens Claude on this worktree.</div>');
}

document.addEventListener('click', event => {
  const promote = event.target.closest('[data-promote]');
  if (promote) {
    host.post({ type: 'createWorktree', change: promote.getAttribute('data-promote') });
    return;
  }
  if (event.target.closest('[data-action="newwork"]')) {
    host.post({ type: 'startWork' });
    return;
  }
  if (event.target.closest('[data-ask-worktree]')) {
    host.post({ type: 'askClaude' });
    return;
  }
  const ask = event.target.closest('[data-ask]');
  if (ask) {
    host.post({
      type: 'askClaude',
      change: ask.getAttribute('data-ask'),
      intent: ask.getAttribute('data-intent')
    });
    return;
  }
  const toggle = event.target.closest('[data-toggle]');
  if (toggle) {
    const which = toggle.getAttribute('data-toggle');
    if (which === 'hideDone') { hideDone = !hideDone; }
    if (which === 'collapseAll' && last) {
      const names = last.worktree.changes.map(c => c.name);
      collapsed = collapsed.size >= names.length ? new Set() : new Set(names);
    }
    persist();
    if (last) { render(last); }
    return;
  }
  const head = event.target.closest('[data-collapse]');
  if (head) {
    const name = head.getAttribute('data-collapse');
    if (collapsed.has(name)) { collapsed.delete(name); } else { collapsed.add(name); }
    persist();
    if (last) { render(last); }
    return;
  }
  const open = event.target.closest('[data-open]');
  if (!open) { return; }
  host.post({
    type: 'openFile',
    path: open.getAttribute('data-open'),
    line: open.hasAttribute('data-line') ? Number(open.getAttribute('data-line')) : undefined
  });
});

window.__receive = function(data) {
  if (data.type !== 'state') { return; }
  if (data.ui) {
    collapsed = new Set(data.ui.collapsed || []);
    hideDone = !!data.ui.hideDone;
  }
  render(data);
};
host.post({ type: 'ready' });
"""

    fun html(): String = Html.page(CSS, SCRIPT)
}
