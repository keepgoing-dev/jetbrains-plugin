package com.keepgoing.plugin.data

import com.keepgoing.plugin.model.ProjectSessions
import com.keepgoing.plugin.model.ProjectState
import com.keepgoing.plugin.model.ReEntryBriefing
import com.keepgoing.plugin.model.SessionCheckpoint
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Builds a ReEntryBriefing from session data.
 * Port of packages/shared/src/reentry.ts generateBriefing().
 */
object BriefingGenerator {

    private const val RECENT_SESSION_COUNT = 5

    fun generate(
        sessions: ProjectSessions?,
        state: ProjectState?,
    ): ReEntryBriefing? {
        if (sessions == null) return null
        val allSessions = sessions.sessions
        val lastSession = allSessions.lastOrNull() ?: return null
        val recentSessions = allSessions.takeLast(RECENT_SESSION_COUNT).reversed()
        val gitBranch = state?.lastKnownBranch

        return ReEntryBriefing(
            lastWorked = formatRelativeTime(lastSession.timestamp),
            currentFocus = buildCurrentFocus(lastSession, state, gitBranch),
            recentActivity = buildRecentActivity(lastSession, recentSessions),
            suggestedNext = buildSuggestedNext(lastSession, gitBranch),
            smallNextStep = buildSmallNextStep(lastSession, gitBranch),
        )
    }

    private fun buildCurrentFocus(
        lastSession: SessionCheckpoint,
        state: ProjectState?,
        gitBranch: String?,
    ): String {
        state?.derivedCurrentFocus?.let { return it }
        inferFocusFromBranch(gitBranch)?.let { return it }
        if (lastSession.summary.isNotBlank()) return lastSession.summary
        if (lastSession.touchedFiles.isNotEmpty()) return inferFocusFromFiles(lastSession.touchedFiles)
        return "Unknown, save a checkpoint to set context"
    }

    private fun buildRecentActivity(
        lastSession: SessionCheckpoint,
        recentSessions: List<SessionCheckpoint>,
    ): String {
        val parts = mutableListOf<String>()

        val count = recentSessions.size
        if (count > 1) parts.add("$count recent sessions")
        else if (count == 1) parts.add("1 recent session")

        if (lastSession.summary.isNotBlank()) parts.add("Last: ${lastSession.summary}")
        if (lastSession.touchedFiles.isNotEmpty()) parts.add("${lastSession.touchedFiles.size} files touched")

        return if (parts.isNotEmpty()) parts.joinToString(". ") else "No recent activity recorded"
    }

    private fun buildSuggestedNext(
        lastSession: SessionCheckpoint,
        gitBranch: String?,
    ): String {
        if (lastSession.nextStep.isNotBlank()) return lastSession.nextStep
        inferFocusFromBranch(gitBranch)?.let { return "Continue working on $it" }
        if (lastSession.touchedFiles.isNotEmpty()) {
            return "Continue working on ${inferFocusFromFiles(lastSession.touchedFiles)}"
        }
        return "Save a checkpoint to track your next step"
    }

    private fun buildSmallNextStep(
        lastSession: SessionCheckpoint,
        gitBranch: String?,
    ): String {
        if (lastSession.nextStep.isNotBlank()) {
            val words = lastSession.nextStep.trim().split("\\s+".toRegex())
            val step = if (words.size <= 12) lastSession.nextStep.trim()
            else words.take(12).joinToString(" ")

            if (lastSession.touchedFiles.isNotEmpty() && !mentionsFile(step)) {
                val primaryFile = getPrimaryFileName(lastSession.touchedFiles)
                val enhanced = "$step in $primaryFile"
                if (enhanced.split("\\s+".toRegex()).size <= 12) return enhanced
            }
            return step
        }

        if (lastSession.touchedFiles.isNotEmpty()) {
            val primaryFile = getPrimaryFileName(lastSession.touchedFiles)
            return if (lastSession.touchedFiles.size > 1) {
                "Open $primaryFile and review ${lastSession.touchedFiles.size} changed files"
            } else {
                "Open $primaryFile and pick up where you left off"
            }
        }

        inferFocusFromBranch(gitBranch)?.let { return "Check git status for $it" }
        return "Review last changed files to resume flow"
    }

    private fun inferFocusFromBranch(branch: String?): String? {
        if (branch == null || branch in listOf("main", "master", "develop", "HEAD")) return null
        val prefixPattern = "^(?:feature|feat|fix|bugfix|hotfix|chore|refactor|docs|test|ci)/".toRegex(RegexOption.IGNORE_CASE)
        val isFix = "^(?:fix|bugfix|hotfix)/".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(branch)
        val stripped = branch.replace(prefixPattern, "")
        val cleaned = stripped.replace("[-_/]".toRegex(), " ").replace("^\\d+\\s*".toRegex(), "").trim()
        if (cleaned.isEmpty()) return null
        return if (isFix) "$cleaned fix" else cleaned
    }

    private fun inferFocusFromFiles(files: List<String>): String {
        if (files.isEmpty()) return "unknown files"

        val dirs = files.mapNotNull { f ->
            val parts = f.replace("\\", "/").split("/")
            if (parts.size > 1) parts.dropLast(1).joinToString("/") else null
        }

        if (dirs.isNotEmpty()) {
            val counts = dirs.groupingBy { it }.eachCount()
            val topDir = counts.maxByOrNull { it.value }?.key
            if (topDir != null) return "files in $topDir"
        }

        val names = files.take(3).map { f ->
            f.replace("\\", "/").split("/").last()
        }
        return names.joinToString(", ")
    }

    private fun getPrimaryFileName(files: List<String>): String {
        val sourceFiles = files.filter { f ->
            val lower = f.lowercase()
            !lower.contains("test") && !lower.contains("spec") &&
                !lower.contains(".config") && !lower.contains("package.json") &&
                !lower.contains("tsconfig")
        }
        val target = if (sourceFiles.isNotEmpty()) sourceFiles[0] else files[0]
        return target.replace("\\", "/").split("/").last()
    }

    private fun mentionsFile(text: String): Boolean {
        return "\\w+\\.(?:ts|tsx|js|jsx|py|go|rs|java|rb|css|scss|html|json|yaml|yml|md|sql|sh|kt)\\b"
            .toRegex(RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    }

    fun formatRelativeTime(timestamp: String): String {
        val instant = parseTimestamp(timestamp) ?: return "unknown time"
        val diffMs = System.currentTimeMillis() - instant.toEpochMilli()
        if (diffMs < 0) return "in the future"

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            seconds < 10 -> "just now"
            seconds < 60 -> "$seconds seconds ago"
            minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days < 7 -> if (days == 1L) "1 day ago" else "$days days ago"
            weeks < 4 -> if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
            months < 12 -> if (months == 1L) "1 month ago" else "$months months ago"
            else -> if (years == 1L) "1 year ago" else "$years years ago"
        }
    }

    fun parseTimestamp(value: String): Instant? {
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
