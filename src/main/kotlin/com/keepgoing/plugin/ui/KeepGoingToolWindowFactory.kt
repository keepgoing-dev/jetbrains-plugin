package com.keepgoing.plugin.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Factory that creates the KeepGoing tool window (sidebar panel).
 */
class KeepGoingToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KeepGoingToolWindowPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel.component, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
