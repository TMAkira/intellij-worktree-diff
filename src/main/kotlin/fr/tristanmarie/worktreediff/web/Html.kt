package fr.tristanmarie.worktreediff.web

/** Bits shared by the overview, the detail tab and the aria tab. */
object Html {
    /**
     * Base styling for every surface. Every colour is a theme variable with a fallback; the
     * variables themselves are written by [Theme] into the `/*THEME*/` slot at render time.
     */
    const val SHARED_CSS = """
:root { --gap: 10px; --radius: 6px; }
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: var(--wd-font-family, system-ui, sans-serif);
  font-size: var(--wd-font-size, 13px);
  color: var(--wd-fg, #ccc);
  background: var(--wd-bg, #2b2d30);
}
button {
  font-family: inherit;
  font-size: inherit;
  color: inherit;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  text-align: left;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap);
  margin-bottom: var(--gap);
}
.link { color: var(--wd-link, #58a6ff); width: auto; flex: 0 0 auto; }
.link:hover { color: var(--wd-link-active, #79b8ff); text-decoration: underline; }
.muted { opacity: 0.75; font-size: 0.85em; }

.card {
  border: 1px solid var(--wd-border, rgba(128,128,128,0.35));
  border-radius: var(--radius);
  padding: var(--gap);
  background: var(--wd-widget-bg, rgba(128,128,128,0.06));
}
.row { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.title { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.counts { font-size: 0.85em; opacity: 0.8; white-space: nowrap; flex: 0 0 auto; }

.bar {
  height: 5px;
  border-radius: 3px;
  background: var(--wd-border, rgba(128,128,128,0.35));
  margin: 8px 0 6px;
  overflow: hidden;
}
.bar > i { display: block; height: 100%; background: var(--wd-progress, #0a7); }
.bar.full > i { background: var(--wd-success, #3fb950); }

.badges { display: flex; flex-wrap: wrap; gap: 4px; }
.badge {
  font-size: 0.75em;
  padding: 1px 6px;
  border-radius: 8px;
  border: 1px solid var(--wd-border, rgba(128,128,128,0.4));
  white-space: nowrap;
}
.badge.done {
  border-color: var(--wd-success, #3fb950);
  color: var(--wd-success, #3fb950);
}
.badge.warn {
  border-color: var(--wd-warning, #d29922);
  color: var(--wd-warning, #d29922);
}
.error { color: var(--wd-error, #f85149); font-size: 0.85em; margin-top: 4px; }
.empty { opacity: 0.6; font-size: 0.9em; padding: 6px 0; }
"""

    /** The helpers injected into every page script: escaping, the progress bar, and the bridge. */
    const val SCRIPT_PRELUDE = """
function esc(text) {
  return String(text).replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[c]);
}
function bar(ratio, full) {
  const pct = Math.round(ratio * 100);
  return '<div class="bar' + (full ? ' full' : '') + '"><i style="width:' + pct + '%"></i></div>';
}
const host = {
  post(obj) {
    const m = JSON.stringify(obj);
    if (window.__postToHost) { window.__postToHost(m); }
    else { (window.__hostQueue = window.__hostQueue || []).push(m); }
  }
};
"""

    fun page(css: String, script: String): String = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
/*THEME*/
$SHARED_CSS
$css
</style>
</head>
<body>
<div id="app"><div class="empty">Loading…</div></div>
<script>
$SCRIPT_PRELUDE
$script
</script>
</body>
</html>"""
}
