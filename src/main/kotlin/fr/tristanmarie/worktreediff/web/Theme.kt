package fr.tristanmarie.worktreediff.web

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.UIManager

/**
 * The look and feel, as CSS variables. Every colour the pages use is one of these, with a
 * fallback, so the views follow light, dark and high-contrast themes without a palette of
 * their own, the way the VS Code pages follow `--vscode-*`.
 */
object Theme {
    private fun hex(color: Color?): String? = color?.let { ColorUtil.toHtmlColor(it) }

    private fun ui(key: String): Color? = UIManager.getColor(key)

    fun cssVariables(): String {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val font = UIUtil.getLabelFont()
        val link = JBUI.CurrentTheme.Link.Foreground.ENABLED
        val vars = linkedMapOf(
            "--wd-font-family" to "'${font.family}', system-ui, sans-serif",
            "--wd-font-size" to "${font.size}px",
            "--wd-editor-font" to "'${scheme.editorFontName}', monospace",
            "--wd-fg" to hex(UIUtil.getLabelForeground()),
            "--wd-bg" to hex(UIUtil.getPanelBackground()),
            "--wd-widget-bg" to hex(scheme.defaultBackground),
            "--wd-border" to hex(JBColor.border()),
            "--wd-link" to hex(link),
            "--wd-link-active" to hex(JBUI.CurrentTheme.Link.Foreground.HOVERED),
            "--wd-focus" to hex(JBUI.CurrentTheme.Focus.focusColor()),
            "--wd-hover" to hex(UIUtil.getListSelectionBackground(false)),
            "--wd-button-bg" to hex(ui("Button.default.startBackground") ?: link),
            "--wd-button-fg" to hex(ui("Button.default.foreground") ?: Color.WHITE),
            "--wd-secondary-bg" to hex(ui("Button.startBackground") ?: UIUtil.getPanelBackground()),
            "--wd-badge-bg" to hex(ui("Counter.background") ?: JBColor.border()),
            "--wd-badge-fg" to hex(ui("Counter.foreground") ?: UIUtil.getLabelForeground()),
            "--wd-warning" to hex(JBUI.CurrentTheme.Validator.warningBorderColor()),
            "--wd-error" to hex(JBUI.CurrentTheme.Validator.errorBorderColor()),
            "--wd-success" to hex(JBColor(Color(0x1E8E3E), Color(0x49A85D))),
            "--wd-progress" to hex(link),
            "--wd-quote-bg" to hex(UIUtil.getListSelectionBackground(false)),
        )
        return ":root {\n" + vars.filterValues { it != null }.entries.joinToString("\n") { "  ${it.key}: ${it.value};" } + "\n}"
    }
}
