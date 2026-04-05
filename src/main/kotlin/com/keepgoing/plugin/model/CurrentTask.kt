package com.keepgoing.plugin.model

/**
 * Mirrors CurrentTask from packages/shared/src/types.ts.
 * Stored in .keepgoing/current-tasks.json (multi-session).
 */
data class CurrentTask(
    val sessionId: String? = null,
    val updatedAt: String = "",
    val branch: String? = null,
    val agentLabel: String? = null,
    val sessionLabel: String? = null,
    val taskSummary: String? = null,
    val lastFileEdited: String? = null,
    val nextStep: String? = null,
    val sessionActive: Boolean = false,
    val workspaceRoot: String? = null,
    val worktreePath: String? = null,
    val sessionPhase: String? = null, // "planning" or "active"
)

/**
 * Mirrors CurrentTasks from packages/shared/src/types.ts.
 * Container for multiple concurrent session tasks.
 */
data class CurrentTasks(
    val version: Int = 1,
    val tasks: List<CurrentTask> = emptyList(),
)
