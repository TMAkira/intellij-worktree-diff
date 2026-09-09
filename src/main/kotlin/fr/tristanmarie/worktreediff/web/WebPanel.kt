package fr.tristanmarie.worktreediff.web

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * One HTML surface, rendered by the IDE's embedded Chromium, with a two-way message bridge.
 *
 * The pages are the same ones the VS Code extension renders: `host.post({...})` replaces
 * `vscodeApi.postMessage`, and `window.__receive(state)` replaces the `message` event. The
 * theme reaches the page as CSS variables computed from the current look and feel, and the
 * page is re-rendered when that changes.
 */
class WebPanel(
    html: String,
    parent: Disposable,
    private val onMessage: (JsonObject) -> Unit,
) : Disposable {
    private val log = logger<WebPanel>()
    private val template = html
    private val browser: JBCefBrowser?
    private val query: JBCefJSQuery?

    @Volatile
    private var loaded = false
    private val pending = ArrayList<String>()

    /** The last state pushed, replayed after a theme reload. */
    @Volatile
    private var lastSent: String? = null

    val component: JComponent

    init {
        Disposer.register(parent, this)
        if (JBCefApp.isSupported()) {
            val created = JBCefBrowser.createBuilder().setOffScreenRendering(false).build()
            browser = created
            Disposer.register(this, created)
            query = JBCefJSQuery.create(created as JBCefBrowserBase)
            Disposer.register(this, query)
            query.addHandler { raw ->
                try {
                    onMessage(JsonParser.parseString(raw).asJsonObject)
                } catch (e: Exception) {
                    log.warn("Bad message from the page: $raw", e)
                }
                null
            }
            created.jbCefClient.addLoadHandler(
                object : CefLoadHandlerAdapter() {
                    override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                        if (!frame.isMain) return
                        val bridge = "window.__postToHost = function(msg) { ${query.inject("msg")} };" +
                            "if (window.__hostQueue) { window.__hostQueue.forEach(function(m) { window.__postToHost(m); }); window.__hostQueue = []; }"
                        cefBrowser.executeJavaScript(bridge, cefBrowser.url, 0)
                        synchronized(pending) {
                            loaded = true
                            val replay = lastSent
                            if (replay != null && pending.isEmpty()) pending.add(replay)
                            for (js in pending) cefBrowser.executeJavaScript(js, cefBrowser.url, 0)
                            pending.clear()
                        }
                    }
                },
                created.cefBrowser,
            )
            component = created.component
            created.loadHTML(render())
            ApplicationManager.getApplication().messageBus.connect(this).subscribe(
                LafManagerListener.TOPIC,
                LafManagerListener {
                    loaded = false
                    created.loadHTML(render())
                },
            )
        } else {
            browser = null
            query = null
            component = JBLabel(
                "<html>This IDE build has no embedded browser (JCEF), so the OpenSpec board cannot render here.<br>" +
                    "The Worktrees tab and every action still work.</html>",
                SwingConstants.CENTER,
            ).also { it.border = JBUI.Borders.empty(16) }
        }
    }

    private fun render(): String = template.replace("/*THEME*/", Theme.cssVariables())

    /** Hands a JSON document to the page's `window.__receive`. Safe from any thread. */
    fun send(json: String) {
        val cef = browser?.cefBrowser ?: return
        val js = "if (window.__receive) { window.__receive($json); }"
        lastSent = json
        synchronized(pending) {
            if (!loaded) {
                pending.clear()
                pending.add(js)
                return
            }
        }
        cef.executeJavaScript(js, cef.url, 0)
    }

    override fun dispose() {
        synchronized(pending) { pending.clear() }
    }
}
