package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Prega AI — Gamification model.
 *
 * DESIGN PRINCIPLE, and the reason this file reads the way it does:
 * pregnancy gamification can go badly wrong. A streak that punishes her for a
 * day spent vomiting, or a "goal" that turns hydration into failure, actively
 * harms the user this app exists to support. So:
 *
 *   1. Streaks can be *frozen*, not just broken. Everyone gets grace days.
 *   2. Nothing is ever framed as failure. There are no negative points, no
 *      "you lost", no decay.
 *   3. Nothing health-critical is gamified. We never reward weight change,
 *      never set calorie targets, never score her symptoms.
 *   4. Rewards are for *showing up*, not for performing well.
 */

// ─── Progress ──────────────────────────────────────────────────────────────

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val id: Int = 1,

    /** "Bloom Points" — the single soft currency. Only ever goes up. */
    val points: Int = 0,

    /** Consecutive days with any activity at all. */
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    /** yyyy-MM-dd of the last day she did anything. */
    val lastActiveDate: String = "",

    /**
     * Grace days in hand. Spent automatically when a day is missed, so a rough
     * day doesn't erase weeks of progress. Replenishes weekly, caps at 3.
     */
    val streakFreezes: Int = 2,
    val lastFreezeGrantDate: String = "",

    /** Lifetime counters, used for badge thresholds. */
    val totalDaysLogged: Int = 0,
    val totalKickSessions: Int = 0,
    val totalCoachChats: Int = 0,
    val totalQuestsCompleted: Int = 0,
) {
    /** Levels are generous and never regress. Purely a sense of accumulation. */
    val level: Int get() = LEVEL_THRESHOLDS.count { points >= it }.coerceAtLeast(1)

    val levelTitle: String get() = LEVEL_TITLES.getOrElse(level - 1) { LEVEL_TITLES.last() }

    val pointsIntoLevel: Int
        get() = points - (LEVEL_THRESHOLDS.getOrNull(level - 1) ?: 0)

    val pointsToNextLevel: Int?
        get() = LEVEL_THRESHOLDS.getOrNull(level)?.let { it - points }

    /** 0f..1f progress through the current level, for the ring on the home screen. */
    val levelProgress: Float
        get() {
            val floor = LEVEL_THRESHOLDS.getOrNull(level - 1) ?: 0
            val ceil = LEVEL_THRESHOLDS.getOrNull(level) ?: return 1f
            return ((points - floor).toFloat() / (ceil - floor)).coerceIn(0f, 1f)
        }

    companion object {
        val LEVEL_THRESHOLDS = listOf(0, 150, 400, 800, 1400, 2200, 3200, 4500, 6000, 8000)
        val LEVEL_TITLES = listOf(
            "First Light", "Taking Root", "Early Bloom", "Steady Growth",
            "Full Bloom", "Golden Hour", "Nearly There", "Almost Home",
            "Ready", "Radiant",
        )
    }
}

/** Point values. Deliberately flat — no action is worth dramatically more. */
object Points {
    const val DAILY_OPEN = 5
    const val LOG_WATER = 2
    const val LOG_VITAMINS = 5
    const val LOG_SLEEP = 5
    const val LOG_SYMPTOM = 3
    const val LOG_MOOD = 3
    const val KICK_SESSION = 15
    const val ASK_COACH = 5
    const val COMPLETE_QUEST = 20
    const val WEEKLY_MILESTONE = 50
    const val EARN_BADGE = 25
    const val READ_ARTICLE = 5
    const val APPOINTMENT_LOGGED = 10
}

// ─── Badges ────────────────────────────────────────────────────────────────

@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val id: String,
    /** yyyy-MM-dd earned. */
    val earnedDate: String,
    /** Pregnancy week when earned — shown on the badge for keepsake value. */
    val earnedWeek: Int,
    /** AI-generated one-line celebration, cached so it's stable on re-view. */
    val celebration: String = "",
)

/**
 * The badge catalogue.
 *
 * Every badge is achievable by any user regardless of pregnancy difficulty.
 * None require a physical outcome, a weight, or a symptom-free day.
 */
data class BadgeDef(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val tier: BadgeTier,
)

enum class BadgeTier { Bronze, Silver, Gold, Keepsake }

object Badges {
    val ALL = listOf(
        // Showing up
        BadgeDef("first_day", "First Light", "You opened Prega for the first time", "🌅", BadgeTier.Bronze),
        BadgeDef("streak_3", "Three Days", "Three days in a row", "🌱", BadgeTier.Bronze),
        BadgeDef("streak_7", "One Week", "Seven days in a row", "🌿", BadgeTier.Silver),
        BadgeDef("streak_30", "One Month", "Thirty days in a row", "🌳", BadgeTier.Gold),
        BadgeDef("streak_100", "Hundred Days", "One hundred days in a row", "💫", BadgeTier.Gold),

        // Care habits
        BadgeDef("water_first", "First Sip", "Logged water for the first time", "💧", BadgeTier.Bronze),
        BadgeDef("water_7", "Well Watered", "Hit your water goal seven times", "🌊", BadgeTier.Silver),
        BadgeDef("vitamins_14", "Steady Hand", "Vitamins logged fourteen times", "🧡", BadgeTier.Silver),
        BadgeDef("rest_7", "Rested", "Logged sleep seven times", "🌙", BadgeTier.Silver),

        // Connection with baby
        BadgeDef("kick_first", "First Flutter", "Your first kick session", "🦋", BadgeTier.Bronze),
        BadgeDef("kick_10", "Listening In", "Ten kick sessions", "👣", BadgeTier.Silver),
        BadgeDef("kick_50", "Well Attuned", "Fifty kick sessions", "💗", BadgeTier.Gold),

        // Learning
        BadgeDef("coach_first", "First Question", "You asked your coach something", "💬", BadgeTier.Bronze),
        BadgeDef("coach_25", "Curious", "Twenty-five questions asked", "📖", BadgeTier.Silver),

        // Quests
        BadgeDef("quest_first", "Small Win", "Completed your first quest", "✨", BadgeTier.Bronze),
        BadgeDef("quest_50", "Fifty Wins", "Fifty quests completed", "🏅", BadgeTier.Gold),

        // Journey keepsakes — earned simply by reaching a point in pregnancy.
        // These are the emotional core: nobody can fail to earn them.
        BadgeDef("tri_1", "First Trimester", "You reached the end of trimester one", "🌸", BadgeTier.Keepsake),
        BadgeDef("tri_2", "Second Trimester", "You reached the end of trimester two", "🌻", BadgeTier.Keepsake),
        BadgeDef("week_20", "Halfway", "Week twenty — halfway there", "⭐", BadgeTier.Keepsake),
        BadgeDef("week_28", "Third Trimester", "Week twenty-eight — the home stretch", "🌷", BadgeTier.Keepsake),
        BadgeDef("week_37", "Full Term", "Week thirty-seven — full term", "🕊️", BadgeTier.Keepsake),
        BadgeDef("week_40", "Due", "Week forty. You did this.", "👑", BadgeTier.Keepsake),
    )

    fun byId(id: String): BadgeDef? = ALL.firstOrNull { it.id == id }
}

// ─── Daily quests ──────────────────────────────────────────────────────────

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    /** yyyy-MM-dd this quest belongs to. */
    val date: String,
    val title: String,
    /** The "why this matters" line — what turns a chore into a reason. */
    val rationale: String,
    val completed: Boolean = false,
    /** Set when the quest maps to a trackable action, e.g. "log_water". */
    val autoCompleteKey: String = "",
)

/**
 * Offline quest pool. Used when there's no network or no API key, so the
 * feature never simply disappears. The AI generates fresher, week-specific
 * quests when it can — these are the floor, not the ceiling.
 */
object FallbackQuests {
    val POOL = listOf(
        "Drink one full glass of water" to "Hydration eases fatigue and swelling",
        "Step outside for five minutes" to "Daylight helps settle your sleep rhythm",
        "Put your feet up for ten minutes" to "Reduces swelling in your legs and ankles",
        "Text someone who makes you laugh" to "Connection is as protective as any supplement",
        "Eat something with iron in it" to "Your blood volume is climbing right now",
        "Take three slow breaths" to "Steadies your heart rate in under a minute",
        "Stretch your lower back gently" to "Your centre of gravity is shifting",
        "Write down one thing you're looking forward to" to "You'll want to read this later",
        "Say no to one thing today" to "Your energy is a finite resource this month",
        "Have a snack before you get hungry" to "Steadier blood sugar, steadier nausea",
    )
}

// ─── Mood, appointments, contractions, kick-count goals ────────────────────

@Entity(tableName = "mood_logs")
data class MoodEntity(
    @PrimaryKey val date: String,
    /** 1..5 — never labelled "good" or "bad", only named. */
    val mood: Int,
    val note: String = "",
    /** Comma-separated tags: "anxious,excited,tired". */
    val tags: String = "",
)

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    /** yyyy-MM-dd */
    val date: String,
    /** HH:mm, blank if unknown. */
    val time: String = "",
    val location: String = "",
    val notes: String = "",
    /** Questions she wants to remember to ask — the highest-value feature here. */
    val questionsToAsk: String = "",
    val completed: Boolean = false,
)

@Entity(tableName = "contractions")
data class ContractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val durationSeconds: Int,
    /** Gap since the previous contraction started, in seconds. */
    val intervalSeconds: Int = 0,
    /** 1..3 — mild, moderate, strong. */
    val intensity: Int = 2,
)

@Entity(tableName = "weight_logs")
data class WeightEntity(
    @PrimaryKey val date: String,
    val weightKg: Float,
)

/**
 * Prega AI — journal entries.
 *
 * A memory: an optional photo (stored as a file in the app's private
 * storage — the picked image is COPIED in, because photo-picker URIs are
 * temporary grants that die with the process), her note, and a caption.
 * The caption is AI-written when she leaves hers blank — warm, short,
 * matched to the week — but her own words always win over generated ones.
 *
 * Like everything else: on-device only. Photos never leave the phone
 * except when SHE shares an entry.
 */
@Entity(tableName = "journal_entries")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** yyyy-MM-dd */
    val date: String,
    val week: Int,
    val trimester: Int,
    /** Filename inside filesDir/journal/, empty when text-only. */
    val photoFile: String = "",
    /** Her own words. */
    val note: String = "",
    /** Shown caption — hers if she wrote one, else AI-generated. */
    val caption: String = "",
    val mood: Int = 0,
)
