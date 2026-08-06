package com.example.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ai.PregaPrompts
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Prega AI — notification scheduling.
 *
 * Volume is the design decision here. It would be easy to schedule ten of
 * these; the app would be uninstalled within a week. The default is **three per
 * day at most**, at times chosen around how she's likely to feel rather than
 * around engagement metrics.
 *
 * Nothing here uses urgency, streaks-at-risk panic, or fake scarcity to drive
 * opens. If a notification isn't useful on its own terms, it doesn't ship.
 */
object NotificationScheduler {

    private const val MORNING = "prega_morning"
    private const val MIDDAY = "prega_midday"
    private const val EVENING = "prega_evening"

    /** Network isn't required — fallback copy exists, so a nudge always lands. */
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()

    fun scheduleAll(context: Context) {
        NotificationChannels.registerAll(context)

        schedule(
            context,
            uniqueName = MORNING,
            kind = PregaPrompts.NotificationKind.MorningCheckIn,
            at = LocalTime.of(8, 30),
        )
        // Early afternoon: the common energy trough, and a natural moment for
        // a kick count because many babies are active after she's eaten.
        schedule(
            context,
            uniqueName = MIDDAY,
            kind = PregaPrompts.NotificationKind.KickCountReminder,
            at = LocalTime.of(14, 0),
        )
        // Before the quiet window opens, so it lands as permission to stop.
        schedule(
            context,
            uniqueName = EVENING,
            kind = PregaPrompts.NotificationKind.RestReminder,
            at = LocalTime.of(20, 30),
        )
    }

    /** Fires once when she crosses into a new pregnancy week. */
    fun scheduleWeekMilestone(context: Context) {
        schedule(
            context,
            uniqueName = "prega_milestone",
            kind = PregaPrompts.NotificationKind.WeekMilestone,
            at = LocalTime.of(9, 0),
        )
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        listOf(MORNING, MIDDAY, EVENING, "prega_milestone").forEach {
            wm.cancelUniqueWork(it)
        }
    }

    /** Re-applies the schedule after a settings change. */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) scheduleAll(context) else cancelAll(context)
    }

    private fun schedule(
        context: Context,
        uniqueName: String,
        kind: PregaPrompts.NotificationKind,
        at: LocalTime,
    ) {
        val request = PeriodicWorkRequestBuilder<PregaNotificationWorker>(
            Duration.ofDays(1)
        )
            .setInitialDelay(delayUntil(at))
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(PregaNotificationWorker.KEY_KIND, kind.name).build())
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            // KEEP, so re-opening the app doesn't reset the schedule and cause
            // a burst of notifications.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Duration from now until the next occurrence of [target]. */
    internal fun delayUntil(target: LocalTime, now: LocalDateTime = LocalDateTime.now()): Duration {
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    const val TAG = "prega_notifications"
}
