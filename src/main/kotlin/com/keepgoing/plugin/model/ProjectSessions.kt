package com.keepgoing.plugin.model

/**
 * Mirrors SessionCheckpoint from packages/shared/src/types.ts.
 */
data class SessionCheckpoint(
    val id: String,
    val timestamp: String,
    val summary: String = "",
    val nextStep: String = "",
    val blocker: String? = null,
    val gitBranch: String? = null,
    val touchedFiles: List<String> = emptyList(),
    val workspaceRoot: String = "",
    val tags: List<String>? = null,
    val source: String? = null,
    val projectIntent: String? = null,
    val sessionDuration: Int? = null,
    val commitHashes: List<String>? = null,
)

/**
 * Mirrors ProjectSessions from packages/shared/src/types.ts.
 * Stored in .keepgoing/sessions.json.
 */
data class ProjectSessions(
    val version: Int,
    val project: String,
    val sessions: List<SessionCheckpoint> = emptyList(),
    val lastSessionId: String? = null,
)
