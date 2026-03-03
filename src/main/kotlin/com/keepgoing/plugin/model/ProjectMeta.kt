package com.keepgoing.plugin.model

/**
 * Mirrors ProjectMeta from packages/shared/src/types.ts.
 * Stored in .keepgoing/meta.json.
 */
data class ProjectMeta(
    val projectId: String,
    val createdAt: String,
    val lastUpdated: String,
)
