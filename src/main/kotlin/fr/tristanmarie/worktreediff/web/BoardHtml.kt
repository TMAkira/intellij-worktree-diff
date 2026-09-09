package fr.tristanmarie.worktreediff.web

/** The overview: one compact card per worktree, in the tool window. */
object BoardHtml {
    private const val CSS = """
body { padding: var(--gap); }
.card { margin-bottom: var(--gap); width: 100%; display: block; }
.card.clickable:hover { border-color: var(--wd-focus, #58a6ff); }
.card.idle { opacity: 0.6; }
.sub {
  font-size: 0.85em;
  opacity: 0.75;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}
.badges { margin-top: 6px; }
.controls { display: flex; flex-direction: column; gap: 4px; margin-bottom: var(--gap); }
.check { display: inline-flex; align-items: center; gap: 6px; font-size: 0.85em; opacity: 0.85; width: auto; }
.check .box { width: 13px; text-align: center; }
.check:hover { opacity: 1; }
.badge.aria {
  border-color: var(--wd-link, #58a6ff);
  color: var(--wd-link, #58a6ff);
}

/* The aria card is deliberately unlike a worktree card: it opens a different surface and
   describes the tooling, not the work. */
.ariacard {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid var(--wd-border, rgba(128,128,128,0.35));
  border-left: 3px solid var(--wd-link, #58a6ff);
  border-radius: var(--radius);
  padding: 9px 11px;
  background: var(--wd-widget-bg, rgba(128,128,128,0.06));
  background:
    linear-gradient(135deg,
      color-mix(in srgb, var(--wd-link, #58a6ff) 16%, transparent),
      transparent 68%),
    var(--wd-widget-bg, rgba(128,128,128,0.06));
}
.ariacard:hover { border-color: var(--wd-focus, #58a6ff); }
.ariacard .row { align-items: center; }
.ariacard .name {
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--wd-link, #58a6ff);
}
.ariacard .ver { font-size: 0.8em; opacity: 0.75; }
.ariacard .facts { font-size: 0.8em; opacity: 0.75; margin-top: 3px; }
.ariacard.off { opacity: 0.6; border-left-color: var(--wd-border, rgba(128,128,128,0.4)); }
.ariacard.off .name { color: inherit; }

/* The one action that starts something rather than opening it. */
.newwork {
  display: block;
  width: 100%;
  text-align: center;
  padding: 6px 10px;
  margin-bottom: var(--gap);
  border-radius: var(--radius);
  font-weight: 600;
  background: var(--wd-button-bg, #0a7);
  color: var(--wd-button-fg, #fff);
}
.newwork:hover { filter: brightness(1.1); }

/* A launch request waiting for an answer. Louder than a draft on purpose. */
.requests { margin-bottom: var(--gap); }
.requests .head {
  font-size: 0.75em;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 4px;
  color: var(--wd-warning, #c93);
}
.request {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 4px;
  border: 1px solid var(--wd-warning, #c93);
  border-radius: var(--radius);
}
.request .body { flex: 1; min-width: 0; }
.request .who { font-size: 0.9em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.request .sub { font-size: 0.8em; opacity: 0.75; }

/* A drafted change is not a worktree yet: it gets a row with the one action it can take. */
.drafts { margin-bottom: var(--gap); }
.drafts .head {
  font-size: 0.75em;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  opacity: 0.7;
  margin-bottom: 4px;
}
.draft {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 4px;
  border: 1px dashed var(--wd-border, rgba(128,128,128,0.5));
  border-radius: var(--radius);
}
.draft .name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.9em; }
.promote {
  width: auto;
  flex: 0 0 auto;
  font-size: 0.8em;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid var(--wd-border, rgba(128,128,128,0.4));
  color: var(--wd-fg);
  background: var(--wd-secondary-bg, transparent);
}
.promote:hover { filter: brightness(1.2); }

.divider {
  height: 2px;
  margin: 12px 0 14px;
  border-radius: 2px;
  background: var(--wd-border, rgba(128,128,128,0.4));
  background: linear-gradient(
    to right,
    var(--wd-link, #58a6ff),
    color-mix(in srgb, var(--wd-link, #58a6ff) 25%, transparent) 45%,
    transparent 90%);
  opacity: 0.55;
}
"""

    private const val SCRIPT = """
let state = { board: [], base: '', plugin: null, aria: null, requests: [] };
// Seeded from the workspace store on the first state message, so the toggles survive the
// page being disposed while hidden.
let hideDone = false;    // worktrees whose tasks are all checked
let hideEmpty = false;   // worktrees carrying no openspec change

function persist() { host.post({ type: 'setUi', ui: { hideDone, hideEmpty } }); }

function checkbox(action, on, label) {
  return '<button class="check" data-toggle="' + action + '"><span class="box">' +
    (on ? '☑' : '☐') + '</span>' + esc(label) + '</button>';
}

// Minutes are the unit the check works in, and the wrong unit to read.
function behind(minutes) {
  if (minutes < 90) { return minutes + ' min'; }
  if (minutes < 2880) { return Math.round(minutes / 60) + ' h'; }
  return Math.round(minutes / 1440) + ' days';
}

function ariaCard() {
  const a = state.aria;
  if (!a || !a.installed) {
    return '<button class="ariacard off" data-action="aria">' +
      '<div class="row"><span class="name">Aria</span>' +
      '<span class="ver">not installed</span></div>' +
      '<div class="facts">Open to see what is missing</div></button>';
  }
  const facts = [a.skills + ' skills'];
  if (a.projects) { facts.push(a.learnings + ' learning' + (a.learnings === 1 ? '' : 's')); }
  if (a.notes) { facts.push(a.notes + ' notes'); }
  return '<button class="ariacard" data-action="aria">' +
    '<div class="row"><span class="name">Aria</span>' +
    '<span class="ver">v' + esc(a.version || '?') + '</span></div>' +
    '<div class="facts">' + esc(facts.join(' · ')) + '</div></button>';
}

function requests() {
  const pending = state.requests || [];
  if (!pending.length) { return ''; }
  const rows = pending.map(r => {
    const who = esc(r.from || 'An agent');
    const what = r.broken
      ? '<div class="sub error">unreadable: ' + esc(r.broken) + '</div>'
      : '<div class="sub">' + esc(r.change || '(no change named)') + '</div>';
    const why = r.reason ? '<div class="sub">' + esc(r.reason) + '</div>' : '';
    const failed = r.failed ? '<div class="sub error">failed: ' + esc(r.failed) + '</div>' : '';
    return '<div class="request"><div class="body"><div class="who">' + who + '</div>' + what + why + failed + '</div>' +
      '<button class="promote" data-review="' + esc(r.file) + '">' + (r.failed ? 'Retry' : 'Review') + '</button></div>';
  }).join('');
  const broken = pending.filter(r => r.failed).length;
  const head = (pending.length === 1 ? '1 launch request waiting' : pending.length + ' launch requests waiting') +
    (broken ? ' &middot; ' + broken + ' failed' : '');
  return '<div class="requests"><div class="head">' + head + '</div>' + rows + '</div>';
}

function drafts() {
  const main = state.board.find(w => w.isMain);
  const pending = main ? main.changes.filter(c => c.draft) : [];
  if (!pending.length) { return ''; }
  const rows = pending.map(c =>
    '<div class="draft"><span class="name">' + esc(c.name) + '</span>' +
    '<button class="promote" data-promote="' + esc(c.name) + '">Create worktree</button></div>').join('');
  return '<div class="drafts"><div class="head">Drafts &middot; not committed anywhere</div>' + rows + '</div>';
}

function progress(worktree) {
  let done = 0, total = 0;
  for (const c of worktree.changes) { done += c.done; total += c.total; }
  return { done, total, ratio: total ? done / total : 0 };
}

function render() {
  const visible = state.board.filter(w => {
    if (hideEmpty && w.changes.length === 0) { return false; }
    if (hideDone && w.changes.length > 0) {
      const p = progress(w);
      if (p.total > 0 && p.done === p.total) { return false; }
    }
    return true;
  });
  const hidden = state.board.length - visible.length;
  const cards = visible.map(w => {
    const p = progress(w);
    const names = w.changes.slice(0, 3).map(c =>
      '<div class="sub">' + esc(c.name) + ' &middot; ' + c.done + '/' + c.total + '</div>').join('');
    const more = w.changes.length > 3 ? '<div class="sub">+' + (w.changes.length - 3) + ' more</div>' : '';
    const marks = [];
    if (w.changes.some(c => c.readyToArchive)) { marks.push('<span class="badge done">ready to archive</span>'); }
    if (w.hasAria) {
      marks.push('<span class="badge aria">aria' +
        (w.ariaModes.length === 1 ? ' &middot; ' + esc(w.ariaModes[0]) : '') + '</span>');
    }
    const gates = (w.gates || []).length
      ? '<div class="sub muted">gates &middot; ' + esc((w.gates || []).map(g => g.command).join(' · ')) + '</div>'
      : '';
    const stale = (w.stale || []).map(s =>
      '<div class="sub error">report older than its test &middot; ' + esc(s.report) +
      ' is ' + behind(s.behind) + ' behind ' + esc(s.source) + '</div>').join('');
    const ready = marks.length ? '<div class="badges">' + marks.join('') + '</div>' : '';
    return '<button class="card clickable' + (w.changes.length ? '' : ' idle') + '" data-path="' + esc(w.path) + '">' +
      '<div class="row"><span class="title">' + esc(w.name) + '</span>' +
      '<span class="counts">' + (w.isMain ? 'backlog' : '↑' + w.ahead + ' ↓' + w.behind) + '</span></div>' +
      '<div class="sub">' + esc(w.branch) + (w.filesChanged ? ' &middot; ' + w.filesChanged + ' files' : '') + '</div>' +
      (p.total ? bar(p.ratio, p.done === p.total) + '<div class="counts">' + p.done + '/' + p.total + ' tasks</div>' : '') +
      names + more + ready + gates + stale +
      (w.changes.length ? '' : '<div class="sub">no openspec change on this branch</div>') +
      (w.error ? '<div class="error">' + esc(w.error) + '</div>' : '') +
      '</button>';
  }).join('');

  document.getElementById('app').innerHTML =
    ariaCard() +
    '<div class="divider"></div>' +
    '<button class="newwork" data-action="newwork">+ Start new work</button>' +
    requests() +
    drafts() +
    '<div class="toolbar"><span class="muted">base: ' + esc(state.base) + '</span>' +
    '<button class="link" data-action="refresh">Refresh</button></div>' +
    '<div class="controls">' +
    checkbox('hideDone', hideDone, 'Hide finished') +
    checkbox('hideEmpty', hideEmpty, 'Hide worktrees without a change') +
    '</div>' +
    (cards || '<div class="empty">' +
      (state.board.length ? 'Everything is filtered out.' : 'No worktree found in this project.') + '</div>') +
    (hidden ? '<div class="muted">' + hidden + ' hidden</div>' : '');
}

document.addEventListener('click', event => {
  const toggle = event.target.closest('[data-toggle]');
  if (toggle) {
    const which = toggle.getAttribute('data-toggle');
    if (which === 'hideDone') { hideDone = !hideDone; }
    if (which === 'hideEmpty') { hideEmpty = !hideEmpty; }
    persist();
    render();
    return;
  }
  const promote = event.target.closest('[data-promote]');
  if (promote) {
    host.post({ type: 'createWorktree', change: promote.getAttribute('data-promote') });
    return;
  }
  const review = event.target.closest('[data-review]');
  if (review) {
    host.post({ type: 'reviewRequest', file: review.getAttribute('data-review') });
    return;
  }
  const action = event.target.closest('[data-action]');
  if (action) {
    const name = action.getAttribute('data-action');
    if (name === 'refresh') { host.post({ type: 'refresh' }); }
    if (name === 'aria') { host.post({ type: 'openAria' }); }
    if (name === 'newwork') { host.post({ type: 'startWork' }); }
    return;
  }
  const card = event.target.closest('[data-path]');
  if (card) {
    host.post({ type: 'openDetail', path: card.getAttribute('data-path') });
  }
});

window.__receive = function(data) {
  if (data.type === 'state') {
    state.board = data.board || [];
    state.base = data.base || '';
    state.plugin = data.plugin;
    state.aria = data.aria;
    state.requests = data.requests || [];
    if (data.ui) {
      hideDone = !!data.ui.hideDone;
      hideEmpty = !!data.ui.hideEmpty;
    }
    render();
  }
};

render();
host.post({ type: 'ready' });
"""

    fun html(): String = Html.page(CSS, SCRIPT)
}
