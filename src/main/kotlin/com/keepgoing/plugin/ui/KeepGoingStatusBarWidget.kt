package com.keepgoing.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import com.keepgoing.plugin.data.BriefingGenerator
import com.keepgoing.plugin.data.KeepGoingDataListener
import com.keepgoing.plugin.data.KeepGoingDataService
import java.awt.event.MouseEvent

/**
 * Status bar widget showing relative time since last activity.
 * Click opens the KeepGoing tool window.
 */
class KeepGoingStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    companion object {
        const val ID = "KeepGoingStatusBar"
        private const val REFRESH_INTERVAL_MS = 60_000
    }

    private var statusBar: StatusBar? = null
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        project.messageBus.connect(this).subscribe(
            KeepGoingDataListener.TOPIC,
            object : KeepGoingDataListener {
                override fun onDataChanged() {
                    statusBar.updateWidget(ID)
                }
            }
        )

        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        refreshAlarm.addRequest(object : Runnable {
            override fun run() {
                if (project.isDisposed) return
                statusBar?.updateWidget(ID)
                refreshAlarm.addRequest(this, REFRESH_INTERVAL_MS)
            }
        }, REFRESH_INTERVAL_MS)
    }

    override fun getText(): String {
        val service = KeepGoingDataService.getInstance(project)
        if (!service.hasData) return ""
        val state = service.state
        val lastSession = service.lastSession
        val timestamp = state?.lastActivityAt ?: lastSession?.timestamp ?: return "KG"
        val relative = BriefingGenerator.formatRelativeTime(timestamp)
        return "KG: $relative"
    }

    override fun getTooltipText(): String {
        val service = KeepGoingDataService.getInstance(project)
        val briefing = service.briefing ?: return "KeepGoing: No session data"
        return buildString {
            append("Focus: ${briefing.currentFocus}\n")
            append("Next: ${briefing.smallNextStep}")
        }
    }

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("KeepGoing")
        toolWindow?.show()
    }

    override fun dispose() {
        statusBar = null
    }
}

/**
 * Factory that creates KeepGoingStatusBarWidget instances.
 */
class KeepGoingStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = KeepGoingStatusBarWidget.ID

    override fun getDisplayName(): String = "KeepGoing"

    override fun isAvailable(project: Project): Boolean {
        return KeepGoingDataService.getInstance(project).hasData
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return KeepGoingStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
