package com.example.ui.labor

import com.example.data.ContractionEntity
import java.util.Locale

/**
 * Contraction math — pure functions, zero Android/UI imports, unit-tested.
 *
 * THE STATE MACHINE (defined here, before any UI):
 *   IDLE       no session row with endedAt == null
 *   TIMING     a contractions row exists with durationSeconds == -1
 *   COMPLETED  session active, no in-progress row (between contractions)
 * Backgrounding, process death, phone lock, and interruptions are NOT
 * states: the UI reconstructs the current state from persisted timestamps
 * every time it composes. No recovery code exists because none is needed.
 *
 * THE INTERVAL RULE (deletion/edit safety): intervals are DERIVED at read
 * time from consecutive startTimes of the rows that currently exist — the
 * stored intervalSeconds column is written for history compatibility but
 * never trusted for display or averages. Delete a middle contraction and
 * every number on screen stays internally consistent, because there is
 * exactly one source of truth: the surviving timestamps.
 */

/** One completed contraction with its read-time-derived interval. */
data class TimedContraction(
    val entry: ContractionEntity,
    /** Start-to-start seconds since the previous SURVIVING contraction; 0 for the first. */
    val intervalSec: Int,
)

data class SessionStats(
    val completed: List<TimedContraction>,
    val avgDurationSec: Int,
    val avgIntervalSec: Int,
    /** Seconds since the last completed contraction ended; null before the first. */
    val lastEndedAgoSec: Int?,
)

/** All display math for both the calm timer and Labor Mode. Same input, same numbers. */
fun computeStats(contractions: List<ContractionEntity>, nowMs: Long): SessionStats {
    val completed = contractions
        .filter { it.durationSeconds >= 0 }
        .sortedBy { it.startTime }
    val timed = completed.mapIndexed { i, c ->
        TimedContraction(
            entry = c,
            intervalSec = if (i == 0) 0
            else ((c.startTime - completed[i - 1].startTime) / 1000L).toInt().coerceAtLeast(0),
        )
    }
    val avgDur = completed.map { it.durationSeconds }.average()
        .takeIf { !it.isNaN() }?.toInt() ?: 0
    val intervals = timed.map { it.intervalSec }.filter { it > 0 }
    val avgInt = intervals.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    val lastEnd = completed.lastOrNull()?.let { it.startTime + it.durationSeconds * 1000L }
    return SessionStats(
        completed = timed,
        avgDurationSec = avgDur,
        avgIntervalSec = avgInt,
        lastEndedAgoSec = lastEnd?.let { ((nowMs - it) / 1000L).toInt().coerceAtLeast(0) },
    )
}

/** Elapsed seconds of an in-progress contraction, clamped non-negative. */
fun elapsedSec(active: ContractionEntity, nowMs: Long): Int =
    ((nowMs - active.startTime) / 1000L).toInt().coerceAtLeast(0)

fun fmtClock(totalSec: Int): String =
    "%d:%02d".format(Locale.US, totalSec / 60, totalSec % 60)

fun fmtSpan(totalSec: Int): String =
    if (totalSec < 60) "${totalSec}s"
    else "${totalSec / 60}m ${"%02d".format(Locale.US, totalSec % 60)}s"

/** The share-with-partner / show-the-midwife text. Pure; caller formats clock times. */
fun buildSummaryText(
    stats: SessionStats,
    sessionStartLabel: String,
    timeLabel: (Long) -> String,
): String = buildString {
    appendLine("Contraction timing (Prega AI)")
    appendLine("Session started $sessionStartLabel")
    appendLine("Contractions: ${stats.completed.size}")
    if (stats.avgDurationSec > 0) appendLine("Average duration: ${fmtSpan(stats.avgDurationSec)}")
    if (stats.avgIntervalSec > 0) appendLine("Average interval: ${fmtSpan(stats.avgIntervalSec)}")
    appendLine()
    stats.completed.takeLast(10).forEach { t ->
        append("${timeLabel(t.entry.startTime)} — ${fmtSpan(t.entry.durationSeconds)}")
        if (t.intervalSec > 0) append("  (${fmtSpan(t.intervalSec)} apart)")
        appendLine()
    }
    appendLine()
    append("Every pregnancy is different — this pattern is worth discussing with the care team.")
}
