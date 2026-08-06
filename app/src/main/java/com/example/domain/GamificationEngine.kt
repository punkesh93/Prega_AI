package com.example.domain

import com.example.data.BadgeDef
import com.example.data.BadgeEntity
import com.example.data.Badges
import com.example.data.PregnancyRepository
import com.example.data.Points
import com.example.data.ProgressEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Prega AI — Gamification engine.
 *
 * Pure-ish logic, deliberately kept out of the ViewModel so it can be unit
 * tested without Android. Every method takes the current [ProgressEntity] and
 * returns a new one; nothing mutates in place.
 */
class GamificationEngine(private val repository: PregnancyRepository) {

    companion object {
        private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        const val MAX_FREEZES = 3
        /** How many missed days a single freeze covers. */
        const val FREEZE_COVERS_DAYS = 1
    }

    /** Result of a state change, so the UI knows what to celebrate. */
    data class Outcome(
        val progress: ProgressEntity,
        val pointsAwarded: Int = 0,
        val newBadges: List<BadgeDef> = emptyList(),
        val streakIncreased: Boolean = false,
        val freezeUsed: Boolean = false,
        val levelledUp: Boolean = false,
    )

    // ─── Streaks ──────────────────────────────────────────────────────────

    /**
     * Call once per app open, before awarding any points.
     *
     * The freeze mechanic is the important part. A user who misses a day
     * because she spent it being sick — which is a normal week in the first
     * trimester — should not lose a 40-day streak. She spends a freeze instead
     * and the streak survives. Freezes replenish weekly and she never has to
     * ask for them.
     */
    fun resolveStreak(progress: ProgressEntity, today: LocalDate = LocalDate.now()): Outcome {
        val todayStr = today.format(FMT)
        val last = progress.lastActiveDate.toLocalDateOrNull()

        // Weekly freeze grant, independent of streak state.
        var p = grantWeeklyFreezes(progress, today)

        // Already counted today.
        if (progress.lastActiveDate == todayStr) return Outcome(p)

        // Very first day.
        if (last == null) {
            return Outcome(
                progress = p.copy(currentStreak = 1, longestStreak = maxOf(1, p.longestStreak), lastActiveDate = todayStr),
                streakIncreased = true,
            )
        }

        val gap = ChronoUnit.DAYS.between(last, today).toInt()

        return when {
            // Consecutive day — streak continues.
            gap == 1 -> {
                val streak = p.currentStreak + 1
                Outcome(
                    progress = p.copy(
                        currentStreak = streak,
                        longestStreak = maxOf(streak, p.longestStreak),
                        lastActiveDate = todayStr,
                    ),
                    streakIncreased = true,
                )
            }

            // Missed days, but she has freezes to cover them.
            gap > 1 -> {
                val missed = gap - 1
                val freezesNeeded = (missed + FREEZE_COVERS_DAYS - 1) / FREEZE_COVERS_DAYS
                if (freezesNeeded <= p.streakFreezes) {
                    val streak = p.currentStreak + 1
                    Outcome(
                        progress = p.copy(
                            currentStreak = streak,
                            longestStreak = maxOf(streak, p.longestStreak),
                            lastActiveDate = todayStr,
                            streakFreezes = p.streakFreezes - freezesNeeded,
                        ),
                        streakIncreased = true,
                        freezeUsed = true,
                    )
                } else {
                    // Streak resets to 1 — she is starting again today, not at
                    // zero. There is no such thing as a day worth zero here.
                    Outcome(
                        progress = p.copy(currentStreak = 1, lastActiveDate = todayStr),
                        streakIncreased = false,
                    )
                }
            }

            // Clock went backwards (timezone / manual change). Don't punish.
            else -> Outcome(p.copy(lastActiveDate = todayStr))
        }
    }

    private fun grantWeeklyFreezes(progress: ProgressEntity, today: LocalDate): ProgressEntity {
        val lastGrant = progress.lastFreezeGrantDate.toLocalDateOrNull()
        val weeksSince = if (lastGrant == null) 1
        else (ChronoUnit.DAYS.between(lastGrant, today) / 7).toInt()

        if (weeksSince < 1) return progress

        return progress.copy(
            streakFreezes = (progress.streakFreezes + weeksSince).coerceAtMost(MAX_FREEZES),
            lastFreezeGrantDate = today.format(FMT),
        )
    }

    // ─── Points ───────────────────────────────────────────────────────────

    /** Points only ever accumulate. There is no decay and no penalty. */
    fun awardPoints(progress: ProgressEntity, amount: Int): Outcome {
        if (amount <= 0) return Outcome(progress)
        val before = progress.level
        val updated = progress.copy(points = progress.points + amount)
        return Outcome(
            progress = updated,
            pointsAwarded = amount,
            levelledUp = updated.level > before,
        )
    }

    // ─── Badges ───────────────────────────────────────────────────────────

    /**
     * Evaluates every badge condition and returns those newly earned.
     * Cheap enough to run after any tracked action.
     */
    suspend fun evaluateBadges(
        progress: ProgressEntity,
        currentWeek: Int,
        waterGoalHits: Int,
        vitaminDays: Int,
        sleepDays: Int,
        today: LocalDate = LocalDate.now(),
    ): List<BadgeDef> {
        val earned = repository.getEarnedBadgeIds().toSet()

        val qualifies = buildList {
            // Showing up
            if (progress.totalDaysLogged >= 1) add("first_day")
            if (progress.currentStreak >= 3 || progress.longestStreak >= 3) add("streak_3")
            if (progress.currentStreak >= 7 || progress.longestStreak >= 7) add("streak_7")
            if (progress.currentStreak >= 30 || progress.longestStreak >= 30) add("streak_30")
            if (progress.currentStreak >= 100 || progress.longestStreak >= 100) add("streak_100")

            // Care habits
            if (waterGoalHits >= 1) add("water_first")
            if (waterGoalHits >= 7) add("water_7")
            if (vitaminDays >= 14) add("vitamins_14")
            if (sleepDays >= 7) add("rest_7")

            // Baby
            if (progress.totalKickSessions >= 1) add("kick_first")
            if (progress.totalKickSessions >= 10) add("kick_10")
            if (progress.totalKickSessions >= 50) add("kick_50")

            // Learning
            if (progress.totalCoachChats >= 1) add("coach_first")
            if (progress.totalCoachChats >= 25) add("coach_25")

            // Quests
            if (progress.totalQuestsCompleted >= 1) add("quest_first")
            if (progress.totalQuestsCompleted >= 50) add("quest_50")

            // Journey keepsakes — reaching a week is enough. Nobody fails these.
            if (currentWeek >= 14) add("tri_1")
            if (currentWeek >= 20) add("week_20")
            if (currentWeek >= 28) { add("tri_2"); add("week_28") }
            if (currentWeek >= 37) add("week_37")
            if (currentWeek >= 40) add("week_40")
        }

        val newlyEarned = qualifies.filterNot { it in earned }.mapNotNull { Badges.byId(it) }

        newlyEarned.forEach { badge ->
            repository.awardBadge(
                BadgeEntity(
                    id = badge.id,
                    earnedDate = today.format(FMT),
                    earnedWeek = currentWeek,
                )
            )
        }

        return newlyEarned
    }

    // ─── Convenience: the full "she did something" pipeline ───────────────

    /**
     * One call to handle a tracked action end to end: resolve the streak,
     * award points, bump the relevant lifetime counter, check badges, persist.
     */
    suspend fun recordAction(
        action: Action,
        currentWeek: Int,
        waterGoalHits: Int = 0,
        vitaminDays: Int = 0,
        sleepDays: Int = 0,
        today: LocalDate = LocalDate.now(),
    ): Outcome {
        val start = repository.getProgressOnce()

        val streakOutcome = resolveStreak(start, today)
        var p = streakOutcome.progress

        // Lifetime counters.
        p = when (action) {
            Action.KickSession -> p.copy(totalKickSessions = p.totalKickSessions + 1)
            Action.AskCoach -> p.copy(totalCoachChats = p.totalCoachChats + 1)
            Action.CompleteQuest -> p.copy(totalQuestsCompleted = p.totalQuestsCompleted + 1)
            else -> p
        }
        if (streakOutcome.streakIncreased) {
            p = p.copy(totalDaysLogged = p.totalDaysLogged + 1)
        }

        val pointsOutcome = awardPoints(p, action.points)
        p = pointsOutcome.progress

        val badges = evaluateBadges(p, currentWeek, waterGoalHits, vitaminDays, sleepDays, today)
        if (badges.isNotEmpty()) {
            p = p.copy(points = p.points + badges.size * Points.EARN_BADGE)
        }

        repository.saveProgress(p)

        return Outcome(
            progress = p,
            pointsAwarded = action.points + badges.size * Points.EARN_BADGE,
            newBadges = badges,
            streakIncreased = streakOutcome.streakIncreased,
            freezeUsed = streakOutcome.freezeUsed,
            levelledUp = pointsOutcome.levelledUp,
        )
    }

    enum class Action(val points: Int) {
        DailyOpen(Points.DAILY_OPEN),
        LogWater(Points.LOG_WATER),
        LogVitamins(Points.LOG_VITAMINS),
        LogSleep(Points.LOG_SLEEP),
        LogSymptom(Points.LOG_SYMPTOM),
        LogMood(Points.LOG_MOOD),
        KickSession(Points.KICK_SESSION),
        AskCoach(Points.ASK_COACH),
        CompleteQuest(Points.COMPLETE_QUEST),
        WeeklyMilestone(Points.WEEKLY_MILESTONE),
        LogAppointment(Points.APPOINTMENT_LOGGED),
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    if (isBlank()) null else runCatching { LocalDate.parse(this) }.getOrNull()
