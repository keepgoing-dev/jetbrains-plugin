package com.keepgoing.plugin.model

/**
 * Mirrors ReEntryBriefing from packages/shared/src/types.ts.
 * Synthesized briefing shown when returning to a project.
 */
data class ReEntryBriefing(
    val lastWorked: String,
    val currentFocus: String,
    val recentActivity: String,
    val suggestedNext: String,
    val smallNextStep: String,
)
