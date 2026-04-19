package com.keepgoing.plugin.data

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import com.keepgoing.shared.BriefingGenerator
import com.keepgoing.shared.db.ProjectDbReader
import com.keepgoing.shared.model.DecisionClassification
import com.keepgoing.shared.model.DecisionRecord
import com.keepgoing.shared.model.ProjectMeta
import com.keepgoing.shared.model.ProjectState
import com.keepgoing.shared.model.ReEntryBriefing
import com.keepgoing.shared.model.SessionCheckpoint
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
 * Tries SQLite DB first via ProjectDbReader, falls back to Gson JSON reads.
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

    @Volatile var meta: ProjectMeta? = null; private set
    @Volatile var state: ProjectState? = null; private set
    @Volatile var checkpoints: List<SessionCheckpoint> = emptyList(); private set
    @Volatile var decisions: List<DecisionRecord> = emptyList(); private set
    @Volatile var briefing: ReEntryBriefing? = null; private set

    val hasData: Boolean get() = meta != null || checkpoints.isNotEmpty()

    val lastSession: SessionCheckpoint?
        get() = checkpoints.firstOrNull()

    init {
        reload()
        startFileWatching()
        schedulePeriodicRefresh()
    }

    /**
     * Read all .keepgoing/ data from disk (off-EDT) and update cached data.
     */
    fun reload() {
        ApplicationManager.getApplication().executeOnPooledThread {
            doReload()
        }
    }

    private fun doReload() {
        val basePath = project.basePath
        val keepGoingDirSnapshot = keepGoingDir
        if (basePath == null || keepGoingDirSnapshot == null || !keepGoingDirSnapshot.exists()) {
            meta = null
            state = null
            checkpoints = emptyList()
            decisions = emptyList()
            briefing = null
            notifyChanged()
            return
        }

        val conn = ProjectDbReader.getConnection(basePath)
        if (conn != null) {
            try {
                meta = ProjectDbReader.readProjectMeta(conn)
                state = ProjectDbReader.readProjectState(conn)
                checkpoints = ProjectDbReader.getRecentCheckpoints(conn, limit = 50)
                decisions = ProjectDbReader.getRecentDecisions(conn, limit = 50)
            } catch (e: Exception) {
                logger.warn("Failed to read from SQLite DB, falling back to JSON", e)
                loadFromJson(keepGoingDirSnapshot)
            } finally {
                try { conn.close() } catch (_: Exception) {}
            }
        } else {
            loadFromJson(keepGoingDirSnapshot)
        }

        briefing = BriefingGenerator.generate(checkpoints, state)
        notifyChanged()
    }

    private fun loadFromJson(dir: File) {
        meta = readJsonFallback(dir, "meta.json") { GsonProjectMeta.fromJson(it, gson) }?.toShared()
        state = readJsonFallback(dir, "state.json") { GsonProjectState.fromJson(it, gson) }?.toShared()

        val sessions = readJsonFallback(dir, "sessions.json") { GsonProjectSessions.fromJson(it, gson) }
        checkpoints = sessions?.sessions?.map { it.toShared() } ?: emptyList()

        val decisionsFile = readJsonFallback(dir, "decisions.json") { GsonProjectDecisions.fromJson(it, gson) }
        decisions = decisionsFile?.decisions?.map { it.toShared() } ?: emptyList()
    }

    private fun <T> readJsonFallback(dir: File, filename: String, parse: (String) -> T?): T? {
        val file = File(dir, filename)
        if (!file.exists()) return null
        return try {
            parse(file.readText())
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

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): KeepGoingDataService {
            return project.getService(KeepGoingDataService::class.java)
        }
    }

    // ---------------------------------------------------------------------------
    // Gson fallback types for reading legacy JSON files
    // ---------------------------------------------------------------------------

    private data class GsonProjectMeta(
        val projectId: String = "",
        val createdAt: String = "",
        val lastUpdated: String = "",
    ) {
        fun toShared() = ProjectMeta(
            projectId = projectId,
            createdAt = createdAt,
            lastUpdated = lastUpdated,
        )

        companion object {
            fun fromJson(json: String, gson: Gson): GsonProjectMeta? =
                try { gson.fromJson(json, GsonProjectMeta::class.java) } catch (_: Exception) { null }
        }
    }

    private data class GsonProjectState(
        val lastSessionId: String? = null,
        val lastKnownBranch: String? = null,
        val lastActivityAt: String? = null,
        val derivedCurrentFocus: String? = null,
    ) {
        fun toShared() = ProjectState(
            lastSessionId = lastSessionId,
            lastKnownBranch = lastKnownBranch,
            lastActivityAt = lastActivityAt,
            derivedCurrentFocus = derivedCurrentFocus,
        )

        companion object {
            fun fromJson(json: String, gson: Gson): GsonProjectState? =
                try { gson.fromJson(json, GsonProjectState::class.java) } catch (_: Exception) { null }
        }
    }

    private data class GsonSessionCheckpoint(
        val id: String = "",
        val sessionId: String = "",
        val timestamp: String = "",
        val summary: String = "",
        val nextStep: String = "",
        val blocker: String? = null,
        val gitBranch: String? = null,
        val branch: String? = null,
        val touchedFiles: List<String> = emptyList(),
        val workspaceRoot: String = "",
        val tags: List<String> = emptyList(),
        val commitHashes: List<String> = emptyList(),
        val sessionPhase: String? = null,
        val logCount: Int? = null,
        val sessionStart: String? = null,
        val sessionEnd: String? = null,
    ) {
        fun toShared() = SessionCheckpoint(
            id = id,
            sessionId = sessionId,
            timestamp = timestamp,
            summary = summary,
            nextStep = nextStep,
            blocker = blocker,
            branch = branch ?: gitBranch,
            touchedFiles = touchedFiles,
            workspaceRoot = workspaceRoot,
            tags = tags,
            commitHashes = commitHashes,
            sessionPhase = sessionPhase,
            logCount = logCount,
            sessionStart = sessionStart,
            sessionEnd = sessionEnd,
        )
    }

    private data class GsonProjectSessions(
        val version: Int = 0,
        val project: String = "",
        val sessions: List<GsonSessionCheckpoint> = emptyList(),
        val lastSessionId: String? = null,
    ) {
        companion object {
            fun fromJson(json: String, gson: Gson): GsonProjectSessions? =
                try { gson.fromJson(json, GsonProjectSessions::class.java) } catch (_: Exception) { null }
        }
    }

    private data class GsonDecisionClassification(
        val isDecisionCandidate: Boolean = false,
        val confidence: Double = 0.0,
        val reasons: List<String> = emptyList(),
        val category: String = "unknown",
    ) {
        fun toShared() = DecisionClassification(
            isDecisionCandidate = isDecisionCandidate,
            confidence = confidence,
            reasons = reasons,
            category = category,
        )
    }

    private data class GsonDecisionRecord(
        val id: String = "",
        val checkpointId: String? = null,
        val gitBranch: String? = null,
        val branch: String? = null,
        val commitHash: String = "",
        val commitMessage: String = "",
        val filesChanged: List<String> = emptyList(),
        val timestamp: String = "",
        val classification: GsonDecisionClassification = GsonDecisionClassification(),
        val rationale: String? = null,
        val finalized: Boolean = false,
        val refinementStatus: String = "pending",
        val refinementProvider: String? = null,
    ) {
        fun toShared() = DecisionRecord(
            id = id,
            checkpointId = checkpointId,
            branch = branch ?: gitBranch,
            commitHash = commitHash,
            commitMessage = commitMessage,
            filesChanged = filesChanged,
            timestamp = timestamp,
            classification = classification.toShared(),
            rationale = rationale,
            finalized = finalized,
            refinementStatus = refinementStatus,
            refinementProvider = refinementProvider,
        )
    }

    private data class GsonProjectDecisions(
        val version: Int = 0,
        val decisions: List<GsonDecisionRecord> = emptyList(),
    ) {
        companion object {
            fun fromJson(json: String, gson: Gson): GsonProjectDecisions? =
                try { gson.fromJson(json, GsonProjectDecisions::class.java) } catch (_: Exception) { null }
        }
    }
}
