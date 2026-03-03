package com.keepgoing.plugin.data

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import com.keepgoing.plugin.model.*
import java.io.File

/**
 * Listener interface for data change notifications.
 */
interface KeepGoingDataListener {
    fun onDataChanged()

    companion object {
        val TOPIC = Topic.create("KeepGoing Data Changed", KeepGoingDataListener::class.java)
    }
}

/**
 * Project-level service that reads, caches, and watches .keepgoing/ data.
 * Publishes change events via MessageBus so UI components auto-refresh.
 */
@Service(Service.Level.PROJECT)
class KeepGoingDataService(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(KeepGoingDataService::class.java)
    private val gson = Gson()
    private val debounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val keepGoingDir: File?
        get() {
            val basePath = project.basePath ?: return null
            return File(basePath, ".keepgoing")
        }

    // Cached data
    @Volatile var meta: ProjectMeta? = null; private set
    @Volatile var state: ProjectState? = null; private set
    @Volatile var sessions: ProjectSessions? = null; private set
    @Volatile var decisions: ProjectDecisions? = null; private set
    @Volatile var briefing: ReEntryBriefing? = null; private set

    val hasData: Boolean get() = meta != null

    val lastSession: SessionCheckpoint?
        get() = sessions?.sessions?.lastOrNull()

    init {
        reload()
        startFileWatching()
        schedulePeriodicRefresh()
    }

    /**
     * Read all .keepgoing/ files from disk (off-EDT) and update cached data.
     */
    fun reload() {
        ApplicationManager.getApplication().executeOnPooledThread {
            doReload()
        }
    }

    private fun doReload() {
        val dir = keepGoingDir
        if (dir == null || !dir.exists()) {
            meta = null
            state = null
            sessions = null
            decisions = null
            briefing = null
            notifyChanged()
            return
        }

        meta = readJson("meta.json", ProjectMeta::class.java)
        state = readJson("state.json", ProjectState::class.java)
        sessions = readJson("sessions.json", ProjectSessions::class.java)
        decisions = readJson("decisions.json", ProjectDecisions::class.java)
        briefing = BriefingGenerator.generate(sessions, state)

        notifyChanged()
    }

    private fun <T> readJson(filename: String, clazz: Class<T>): T? {
        val file = File(keepGoingDir ?: return null, filename)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), clazz)
        } catch (e: Exception) {
            logger.warn("Failed to parse .keepgoing/$filename", e)
            null
        }
    }

    private fun notifyChanged() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(KeepGoingDataListener.TOPIC).onDataChanged()
            }
        }
    }

    /**
     * Watch for VFS changes to .keepgoing/ files with 500ms debounce.
     */
    private fun startFileWatching() {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val basePath = project.basePath ?: return
                    val keepGoingPath = "$basePath/.keepgoing/"
                    val relevant = events.any { event ->
                        event.path.startsWith(keepGoingPath)
                    }
                    if (relevant) {
                        debounceReload()
                    }
                }
            }
        )
    }

    private fun debounceReload() {
        debounceAlarm.cancelAllRequests()
        debounceAlarm.addRequest({ doReload() }, 500)
    }

    /**
     * Periodic refresh every 30s to catch external writes from MCP/CLI
     * that may not trigger VFS events.
     */
    private fun schedulePeriodicRefresh() {
        refreshAlarm.addRequest(object : Runnable {
            override fun run() {
                if (project.isDisposed) return
                val basePath = project.basePath ?: return
                val dir = File(basePath, ".keepgoing")
                if (dir.exists()) {
                    val vDir = VirtualFileManager.getInstance()
                        .findFileByUrl("file://$basePath/.keepgoing")
                    vDir?.refresh(true, true)
                }
                doReload()
                if (!project.isDisposed) {
                    refreshAlarm.addRequest(this, 30_000)
                }
            }
        }, 30_000)
    }

    override fun dispose() {
        // Alarms are disposed via the parent Disposable
    }

    companion object {
        fun getInstance(project: Project): KeepGoingDataService {
            return project.getService(KeepGoingDataService::class.java)
        }
    }
}
