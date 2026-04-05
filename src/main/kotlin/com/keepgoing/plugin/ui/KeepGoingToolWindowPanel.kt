package com.keepgoing.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.keepgoing.plugin.data.BriefingGenerator
import com.keepgoing.plugin.data.KeepGoingDataListener
import com.keepgoing.plugin.data.KeepGoingDataService
import com.keepgoing.plugin.model.DecisionCategory
import com.keepgoing.plugin.model.DecisionRecord
import com.keepgoing.plugin.model.ReEntryBriefing
import com.keepgoing.plugin.model.SessionCheckpoint
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.event.HyperlinkEvent

/**
 * Tool window panel showing briefing, session history, and decisions.
 */
class KeepGoingToolWindowPanel(private val project: Project) : Disposable {

    private val rootPanel = JPanel(BorderLayout())

    val component: JComponent get() = rootPanel

    init {
        Disposer.register(project, this)
        rebuildContent()

        project.messageBus.connect(this).subscribe(
            KeepGoingDataListener.TOPIC,
            object : KeepGoingDataListener {
                override fun onDataChanged() {
                    rebuildContent()
                }
            }
        )
    }

    private fun rebuildContent() {
        rootPanel.removeAll()

        val service = KeepGoingDataService.getInstance(project)

        val content = if (!service.hasData) {
            buildEmptyState()
        } else {
            buildDataContent(service)
        }

        // Scrollable wrapper forces content to track viewport width
        val scrollable = object : JPanel(BorderLayout()), Scrollable {
            init {
                add(content, BorderLayout.NORTH)
            }
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
            override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
            override fun getScrollableTracksViewportWidth() = true
            override fun getScrollableTracksViewportHeight() = false
        }

        rootPanel.add(
            JBScrollPane(scrollable, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
            BorderLayout.CENTER
        )
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    private fun buildEmptyState(): JComponent {
        return panel {
            row {
                label("No KeepGoing data found").bold()
            }
            row {
                text(
                    "Install the KeepGoing MCP server or CLI to start " +
                        "capturing session checkpoints."
                )
            }
            row {
                link("Get Started") {
                    BrowserUtil.browse("https://keepgoing.dev/setup/jetbrains")
                }
            }
        }.apply {
            border = JBUI.Borders.empty(12)
        }
    }

    private fun buildDataContent(service: KeepGoingDataService): JComponent {
        val briefing = service.briefing
        val sessions = service.sessions?.sessions ?: emptyList()
        val decisions = service.decisions?.decisions ?: emptyList()
        val lastSession = service.lastSession

        val wrapper = JPanel(BorderLayout())

        // Top: briefing as a single JEditorPane with width-aware height.
        // This avoids MigLayout height-calculation bugs that cause overlap.
        wrapper.add(createBriefingPane(briefing, lastSession), BorderLayout.NORTH)

        // Bottom: collapsible groups via DSL (text() handles wrapping)
        val recentSessions = sessions.takeLast(10).reversed()
        val recentDecisions = decisions.takeLast(10).reversed()

        if (recentSessions.isNotEmpty() || recentDecisions.isNotEmpty()) {
            wrapper.add(panel {
                separator()
                if (recentSessions.isNotEmpty()) {
                    collapsibleGroup("Session History (${recentSessions.size})") {
                        for ((i, session) in recentSessions.withIndex()) {
                            buildSessionRow(session)
                            if (i < recentSessions.size - 1) separator()
                        }
                    }.expanded = true
                }
                if (recentDecisions.isNotEmpty()) {
                    collapsibleGroup("Decisions (${recentDecisions.size})") {
                        for ((i, decision) in recentDecisions.withIndex()) {
                            buildDecisionRow(decision)
                            if (i < recentDecisions.size - 1) separator()
                        }
                    }
                }
            }, BorderLayout.CENTER)
        }

        return wrapper
    }

    /**
     * Builds the briefing section as a single HTML pane. Overrides
     * getPreferredSize so the View hierarchy computes wrapped height
     * at the parent's actual width, preventing layout overlap.
     */
    private fun createBriefingPane(briefing: ReEntryBriefing?, lastSession: SessionCheckpoint?): JEditorPane {
        val isDark = !JBColor.isBright()
        val grayHex = if (isDark) "#999999" else "#777777"
        val cardBg = if (isDark) "#1e3a5f" else "#eaf2fc"
        val blockerBg = if (isDark) "#3d1c1c" else "#fceaea"

        val html = buildString {
            append("<html><body>")

            if (briefing != null) {
                append("<b>Welcome back</b><br>")
                append("<font color='$grayHex'>Here's where you left off</font>")
                append("<br><br>")
                append("\u23F0 ${escapeHtml(briefing.lastWorked)}<br>")
                append("\uD83C\uDFAF ${escapeHtml(briefing.currentFocus)}<br>")
                if (briefing.recentActivity.isNotBlank()) {
                    append("\uD83D\uDCCB ${escapeHtml(briefing.recentActivity)}<br>")
                }

                if (briefing.smallNextStep.isNotBlank()) {
                    append("<br>")
                    append("<table width='100%' cellpadding='8' cellspacing='0'>")
                    append("<tr><td bgcolor='$cardBg'>")
                    append("<font color='$grayHex'>Next small step:</font><br>")
                    append("\u2705 ${escapeHtml(briefing.smallNextStep)}")
                    append("</td></tr></table>")
                }
            }

            if (lastSession?.blocker != null) {
                append("<br>")
                append("<table width='100%' cellpadding='6' cellspacing='0'>")
                append("<tr><td bgcolor='$blockerBg'>")
                append("\u26A0\uFE0F Blocker: ${escapeHtml(lastSession.blocker)}")
                append("</td></tr></table>")
            }

            if (lastSession != null && lastSession.touchedFiles.isNotEmpty()) {
                val n = lastSession.touchedFiles.size
                val desc = if (n == 1) "1 file from last session" else "$n files from last session"
                append("<br>")
                append("<a href='action:openFiles'>Open last touched files</a> ")
                append("<font color='$grayHex' size='-1'>$desc</font>")
            }

            append("</body></html>")
        }

        return object : JEditorPane() {
            private var computing = false

            override fun getPreferredSize(): Dimension {
                // Guard against re-entrant calls from setSize -> invalidate -> getPreferredSize.
                if (computing) return super.getPreferredSize()
                // Temporarily set our width to the parent's actual width so the
                // HTML view hierarchy reflows text and returns the correct height.
                // Without this, preferred height is based on unwrapped text, causing overlap.
                val pw = parent?.width ?: return super.getPreferredSize()
                if (pw <= 0) return super.getPreferredSize()
                computing = true
                try {
                    setSize(pw, Short.MAX_VALUE.toInt())
                    return super.getPreferredSize()
                } finally {
                    computing = false
                }
            }
        }.apply {
            contentType = "text/html"
            text = html
            isEditable = false
            isOpaque = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = UIUtil.getLabelFont()
            border = JBUI.Borders.empty(8)
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && e.description == "action:openFiles") {
                    lastSession?.let { openTouchedFiles(it.touchedFiles) }
                }
            }
        }
    }

    private fun Panel.buildSessionRow(session: SessionCheckpoint) {
        row {
            val time = BriefingGenerator.formatRelativeTime(session.timestamp)
            val branch = session.gitBranch?.let { " &middot; ${escapeHtml(it)}" } ?: ""
            val summary = escapeHtml(session.summary.ifBlank { "No summary" })
            val nextHtml = if (session.nextStep.isNotBlank()) {
                "<br><font color='gray'>Next: ${escapeHtml(session.nextStep)}</font>"
            } else ""

            @Suppress("DialogTitleCapitalization")
            text(
                "<b>$time</b><font color='gray'>$branch</font>" +
                    "<br>$summary$nextHtml"
            ).applyToComponent {
                toolTipText = buildString {
                    append("Time: $time")
                    if (session.gitBranch != null) append("\nBranch: ${session.gitBranch}")
                    if (session.summary.isNotBlank()) append("\nSummary: ${session.summary}")
                    if (session.nextStep.isNotBlank()) append("\nNext: ${session.nextStep}")
                    if (session.blocker != null) append("\nBlocker: ${session.blocker}")
                    if (session.touchedFiles.isNotEmpty()) {
                        append("\nFiles: ${session.touchedFiles.joinToString(", ")}")
                    }
                }
            }
        }
    }

    private fun Panel.buildDecisionRow(decision: DecisionRecord) {
        row {
            val time = BriefingGenerator.formatRelativeTime(decision.timestamp)
            val category = decision.classification.category
            val categoryName = category.name.uppercase()
            val categoryColor = DECISION_CATEGORY_COLORS[category] ?: "#6b7280"
            val message = escapeHtml(decision.commitMessage.lines().first())
            val branch = decision.gitBranch?.let { " &middot; ${escapeHtml(it)}" } ?: ""

            @Suppress("DialogTitleCapitalization")
            text(
                "<font color='gray'>$time$branch</font> " +
                    "<font color='$categoryColor'><b>[$categoryName]</b></font>" +
                    "<br>$message"
            ).applyToComponent {
                toolTipText = buildString {
                    append("Commit: ${decision.commitHash.take(8)}")
                    append("\nCategory: ${category.name}")
                    append("\nConfidence: ${(decision.classification.confidence * 100).toInt()}%")
                    if (decision.classification.reasons.isNotEmpty()) {
                        append("\nReasons: ${decision.classification.reasons.joinToString("; ")}")
                    }
                    if (decision.gitBranch != null) append("\nBranch: ${decision.gitBranch}")
                    if (decision.rationale != null) append("\nRationale: ${decision.rationale}")
                }
            }
        }
    }

    private fun openTouchedFiles(relativePaths: List<String>) {
        val basePath = project.basePath ?: return
        val editorManager = FileEditorManager.getInstance(project)
        val fs = LocalFileSystem.getInstance()

        for (path in relativePaths) {
            val file = fs.findFileByPath("$basePath/$path")
            if (file != null && file.isValid) {
                editorManager.openFile(file, true)
            }
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun colorToHex(c: Color): String =
        String.format("#%02x%02x%02x", c.red, c.green, c.blue)

    override fun dispose() {}

    companion object {
        private val DECISION_CATEGORY_COLORS = mapOf(
            DecisionCategory.infra to "#f59e0b",
            DecisionCategory.auth to "#a855f7",
            DecisionCategory.migration to "#3b82f6",
            DecisionCategory.deploy to "#10b981",
            DecisionCategory.unknown to "#6b7280",
        )
    }
}
