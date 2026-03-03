package com.keepgoing.plugin.model

/**
 * Mirrors DecisionCategory from packages/shared/src/types.ts.
 */
enum class DecisionCategory {
    infra,
    auth,
    migration,
    deploy,
    unknown,
}

/**
 * Mirrors DecisionClassification from packages/shared/src/types.ts.
 */
data class DecisionClassification(
    val isDecisionCandidate: Boolean,
    val confidence: Double,
    val reasons: List<String> = emptyList(),
    val category: DecisionCategory = DecisionCategory.unknown,
)

/**
 * Mirrors DecisionRecord from packages/shared/src/types.ts.
 */
data class DecisionRecord(
    val id: String,
    val checkpointId: String? = null,
    val gitBranch: String? = null,
    val commitHash: String,
    val commitMessage: String,
    val filesChanged: List<String> = emptyList(),
    val timestamp: String,
    val classification: DecisionClassification,
    val rationale: String? = null,
    val finalized: Boolean? = null,
)
