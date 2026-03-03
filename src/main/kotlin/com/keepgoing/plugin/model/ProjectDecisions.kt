package com.keepgoing.plugin.model

/**
 * Mirrors ProjectDecisions from packages/shared/src/types.ts.
 * Stored in .keepgoing/decisions.json.
 */
data class ProjectDecisions(
    val version: Int,
    val project: String,
    val decisions: List<DecisionRecord> = emptyList(),
    val lastDecisionId: String? = null,
)
