package com.keepgoing.plugin.model

/**
 * Mirrors ProjectState from packages/shared/src/types.ts.
 * Stored in .keepgoing/state.json.
 */
data class ProjectState(
    val lastSessionId: String? = null,
    val lastKnownBranch: String? = null,
    val lastActivityAt: String? = null,
    val derivedCurrentFocus: String? = null,
)
