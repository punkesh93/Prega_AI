package com.example.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.ai.OpenRouterClient
import com.example.ai.PregaModel
import com.example.ai.PregaPrompts
import com.example.data.ALL_MIGRATIONS
import com.example.data.PregnancyDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/**
 * Generates and posts a single notification.
 *
 * Every notification in the app goes through here, which is what keeps the
 * voice consistent and the freshness guarantee enforceable in one place.
 *
 * Flow: check she still wants this → check quiet hours → ask the AI for copy
 * that doesn't repeat the last fortnight → fall back to curated copy if the AI
 * is unavailable → post → record what was sent.
 */
class PregaNotificationWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kindName = inputData.getString(KEY_KIND) ?: return Result.success()
        val kind = runCatching { PregaPrompts.NotificationKind.valueOf(kindName) }
            .getOrNull() ?: return Result.success()

        val db = Room.databaseBuilder(
            context.applicationContext,
            PregnancyDatabase::class.java,
            "pregnancy_db",
        ).addMigrations(*ALL_MIGRATIONS).build()

        val profile = db.pregnancyDao().getUserProfile().first()
            ?: return Result.success()

        // Respect her settings before doing any work at all.
        if (!profile.notificationsEnabled) return Result.success()
        if (inQuietHours(profile.quietHoursStart, profile.quietHoursEnd)) {
            // Not a failure — the schedule will come round again tomorrow.
            return Result.success()
        }
        if (!hasPermission()) return Result.success()

        val history = NotificationHistory(context)
        val today = LocalDate.now().toString()

        // Guard against duplicate sends if WorkManager re-runs the task.
        if (history.lastSentDate(kind.name) == today && kind.isOncePerDay) {
            return Result.success()
        }

        val week = profile.currentWeek
        val copy = generateCopy(kind, profile.name, profile.babyNamePlaceholder, week, history)

        post(kind, copy)
        history.record(kind.name, copy.title, copy.body)
        history.markSent(kind.name, today)

        return Result.success()
    }

    // ─── Copy generation ──────────────────────────────────────────────────

    private data class Copy(val title: String, val body: String)

    private suspend fun generateCopy(
        kind: PregaPrompts.NotificationKind,
        name: String,
        babyName: String,
        week: Int,
        history: NotificationHistory,
    ): Copy {
        val trimester = when {
            week <= 13 -> 1
            week <= 27 -> 2
            else -> 3
        }

        val raw = OpenRouterClient.completeOrNull(
            systemPrompt = PregaPrompts.notification(
                kind = kind,
                week = week,
                trimester = trimester,
                babyName = babyName,
                name = name,
                // The freshness guarantee: the model is shown what it already
                // said and told not to echo it.
                recentlySent = history.recent(kind.name),
            ),
            userPrompt = "Write today's notification.",
            model = PregaModel.Quick,
            // High temperature on purpose — variety matters more than polish
            // for a 110-character lock-screen line.
            temperature = 1.0,
            maxTokens = 120,
        )

        return raw?.let(::parse) ?: fallbackFor(kind, week)
    }

    /** Parses the `TITLE: … / BODY: …` shape the prompt specifies. */
    private fun parse(raw: String): Copy? {
        val title = raw.lineSequence()
            .firstOrNull { it.startsWith("TITLE:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
        val body = raw.lineSequence()
            .firstOrNull { it.startsWith("BODY:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()

        if (title.isNullOrBlank() || body.isNullOrBlank()) return null
        return Copy(title.take(40), body.take(110))
    }

    /**
     * Curated fallbacks. The feature must work with no network and no API key —
     * a notification system that silently stops is worse than a simple one.
     */
    private fun fallbackFor(kind: PregaPrompts.NotificationKind, week: Int): Copy = when (kind) {
        PregaPrompts.NotificationKind.MorningCheckIn ->
            Copy("Week $week", "Your three small things are ready when you are.")
        PregaPrompts.NotificationKind.WeekMilestone ->
            Copy("Week $week begins", "Something new is happening. Come and see.")
        PregaPrompts.NotificationKind.HydrationNudge ->
            Copy("A glass of water", "Whenever suits. No rush.")
        PregaPrompts.NotificationKind.KickCountReminder ->
            Copy("Feeling any movement?", "A few quiet minutes is all a kick count takes.")
        PregaPrompts.NotificationKind.StreakSafeguard ->
            Copy("Still here", "Open Prega whenever you like — your streak is safe.")
        PregaPrompts.NotificationKind.RestReminder ->
            Copy("Time to stop", "Whatever's left can wait until tomorrow.")
        PregaPrompts.NotificationKind.QuestAvailable ->
            Copy("Today's quests", "Three small things, none of them hard.")
        PregaPrompts.NotificationKind.Encouragement ->
            Copy("Week $week", "You're further along than you were. That's the whole job.")
    }

    // ─── Posting ──────────────────────────────────────────────────────────

    private fun post(kind: PregaPrompts.NotificationKind, copy: Copy) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_NOTIFICATION, kind.name)
        }
        val pending = PendingIntent.getActivity(
            context,
            kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, kind.channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            // Longer bodies stay readable when expanded rather than truncating.
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(kind.ordinal, notification)
        }
    }

    private fun hasPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val KEY_KIND = "kind"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"

        /**
         * True when `now` falls inside the user's quiet window. Handles the
         * normal case (21:30 → 08:00) where the window crosses midnight.
         */
        fun inQuietHours(
            start: String,
            end: String,
            now: LocalTime = LocalTime.now(),
        ): Boolean {
            val s = start.toTimeOrNull() ?: return false
            val e = end.toTimeOrNull() ?: return false
            return if (s <= e) now >= s && now < e     // same-day window
            else now >= s || now < e                   // wraps past midnight
        }

        private fun String.toTimeOrNull(): LocalTime? =
            runCatching { LocalTime.parse(this) }.getOrNull()
    }
}

/** Channel routing, kept next to the kind so the two can't drift apart. */
private val PregaPrompts.NotificationKind.channel: String
    get() = when (this) {
        PregaPrompts.NotificationKind.WeekMilestone -> NotificationChannels.MILESTONES
        PregaPrompts.NotificationKind.MorningCheckIn,
        PregaPrompts.NotificationKind.QuestAvailable,
        PregaPrompts.NotificationKind.Encouragement,
        PregaPrompts.NotificationKind.StreakSafeguard -> NotificationChannels.DAILY
        PregaPrompts.NotificationKind.HydrationNudge,
        PregaPrompts.NotificationKind.KickCountReminder,
        PregaPrompts.NotificationKind.RestReminder -> NotificationChannels.NUDGES
    }

private val PregaPrompts.NotificationKind.isOncePerDay: Boolean
    get() = this != PregaPrompts.NotificationKind.HydrationNudge
