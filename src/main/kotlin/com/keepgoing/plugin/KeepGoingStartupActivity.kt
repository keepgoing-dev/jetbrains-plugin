package com.keepgoing.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import com.keepgoing.plugin.data.KeepGoingDataService
import com.keepgoing.shared.TimeUtils
import java.time.Duration
import java.time.Instant

/**
 * Runs once after a project opens. Delegates to KeepGoingDataService
 * for reading .keepgoing/ data and shows a re-entry notification
 * when the last session is >= 3 days old.
 */
class KeepGoingStartupActivity : ProjectActivity {

    companion object {
        private const val INACTIVITY_THRESHOLD_DAYS = 3L
        private const val NOTIFICATION_GROUP_ID = "KeepGoing Notifications"
        private const val SETUP_DISMISSED_KEY = "keepgoing.setup.dismissed"
    }

    override suspend fun execute(project: Project) {
        val service = KeepGoingDataService.getInstance(project)

        if (!service.hasData) {
            showSetupNotificationOnce(project)
            return
        }

        val lastSession = service.lastSession
        val state = service.state
        val timestamp = state?.lastActivityAt ?: lastSession?.timestamp ?: return
        val lastInstant = TimeUtils.parseTimestamp(timestamp) ?: return
        val daysSince = Duration.between(lastInstant, Instant.now()).toDays()

        if (daysSince < INACTIVITY_THRESHOLD_DAYS) return

        val briefing = service.briefing ?: return
        showNotification(project, briefing, lastSession?.touchedFiles ?: emptyList())
    }

    private fun showSetupNotificationOnce(project: Project) {
        val properties = PropertiesComponent.getInstance(project)
        if (properties.getBoolean(SETUP_DISMISSED_KEY, false)) return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "KeepGoing",
                "No session data found for this project. " +
                    "Install the KeepGoing MCP server or CLI to start capturing checkpoints, " +
                    "then this plugin will show re-entry briefings when you return.",
                NotificationType.INFORMATION
            )

        notification.addAction(object : AnAction("Get Started") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowserUtil.browse("https://keepgoing.dev/setup/jetbrains")
                notification.expire()
            }
        })

        notification.addAction(object : AnAction("Don't show again") {
            override fun actionPerformed(e: AnActionEvent) {
                properties.setValue(SETUP_DISMISSED_KEY, true)
                notification.expire()
            }
        })

        notification.notify(project)
    }

    private fun showNotification(
        project: Project,
        briefing: com.keepgoing.shared.model.ReEntryBriefing,
        touchedFiles: List<String>
    ) {
        val content = buildString {
            append("<b>Focus:</b> ${briefing.currentFocus}<br/>")
            append("<b>Last worked:</b> ${briefing.lastWorked}<br/>")
            append("<b>Next step:</b> ${briefing.smallNextStep}")
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "Welcome back, KeepGoing",
                content,
                NotificationType.INFORMATION
            )

        notification.addAction(object : AnAction("Show KeepGoing Panel") {
            override fun actionPerformed(e: AnActionEvent) {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("KeepGoing")
                toolWindow?.show()
                notification.expire()
            }
        })

        if (touchedFiles.isNotEmpty()) {
            notification.addAction(object : AnAction("Open last touched files") {
                override fun actionPerformed(e: AnActionEvent) {
                    openTouchedFiles(project, touchedFiles)
                    notification.expire()
                }
            })
        }

        notification.notify(project)
    }

    private fun openTouchedFiles(project: Project, relativePaths: List<String>) {
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
}
