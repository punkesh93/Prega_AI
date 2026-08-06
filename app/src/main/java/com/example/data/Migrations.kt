package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations.
 *
 * These replace `fallbackToDestructiveMigration()`, which was silently erasing
 * every user's profile, daily logs and kick history on any schema change. For a
 * pregnancy app that is unacceptable — a kick log is a medical record, and the
 * journey data is a keepsake she can never recreate.
 *
 * Rule from here on: every schema change ships with a migration. No exceptions.
 */

/**
 * v3 → v4: gamification, mood, appointments, contractions, weight history,
 * plus onboarding/notification preferences on the profile.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── Profile: new preference columns ──────────────────────────────
        db.execSQL("ALTER TABLE user_profile ADD COLUMN dietaryPreferences TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN onboardingComplete INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN quietHoursStart TEXT NOT NULL DEFAULT '21:30'")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN quietHoursEnd TEXT NOT NULL DEFAULT '08:00'")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN waterGoalGlasses INTEGER NOT NULL DEFAULT 8")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN isFirstPregnancy INTEGER NOT NULL DEFAULT 1")

        // Existing users have already been through setup — don't show them
        // onboarding again just because the column is new.
        db.execSQL("UPDATE user_profile SET onboardingComplete = 1 WHERE name != ''")

        // ── Daily log: mood + notes ──────────────────────────────────────
        db.execSQL("ALTER TABLE daily_logs ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE daily_logs ADD COLUMN energyLevel INTEGER NOT NULL DEFAULT 0")

        // ── Progress ─────────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS progress (
                id INTEGER NOT NULL PRIMARY KEY,
                points INTEGER NOT NULL DEFAULT 0,
                currentStreak INTEGER NOT NULL DEFAULT 0,
                longestStreak INTEGER NOT NULL DEFAULT 0,
                lastActiveDate TEXT NOT NULL DEFAULT '',
                streakFreezes INTEGER NOT NULL DEFAULT 2,
                lastFreezeGrantDate TEXT NOT NULL DEFAULT '',
                totalDaysLogged INTEGER NOT NULL DEFAULT 0,
                totalKickSessions INTEGER NOT NULL DEFAULT 0,
                totalCoachChats INTEGER NOT NULL DEFAULT 0,
                totalQuestsCompleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // Seed progress, crediting existing users for history they already have
        // rather than resetting them to zero on upgrade.
        db.execSQL(
            """
            INSERT OR IGNORE INTO progress (id, totalDaysLogged, totalKickSessions)
            SELECT 1,
                   (SELECT COUNT(*) FROM daily_logs),
                   (SELECT COUNT(*) FROM kick_logs)
            """.trimIndent()
        )

        // ── Badges ───────────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS badges (
                id TEXT NOT NULL PRIMARY KEY,
                earnedDate TEXT NOT NULL,
                earnedWeek INTEGER NOT NULL,
                celebration TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        // ── Quests ───────────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quests (
                id TEXT NOT NULL PRIMARY KEY,
                date TEXT NOT NULL,
                title TEXT NOT NULL,
                rationale TEXT NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                autoCompleteKey TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quests_date ON quests(date)")

        // ── Mood ─────────────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mood_logs (
                date TEXT NOT NULL PRIMARY KEY,
                mood INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        // ── Appointments ─────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS appointments (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                date TEXT NOT NULL,
                time TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                questionsToAsk TEXT NOT NULL DEFAULT '',
                completed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_date ON appointments(date)")

        // ── Contractions ─────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contractions (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                startTime INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                intervalSeconds INTEGER NOT NULL DEFAULT 0,
                intensity INTEGER NOT NULL DEFAULT 2
            )
            """.trimIndent()
        )

        // ── Weight history ───────────────────────────────────────────────
        // Split out of daily_logs so she can chart it over time.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS weight_logs (
                date TEXT NOT NULL PRIMARY KEY,
                weightKg REAL NOT NULL
            )
            """.trimIndent()
        )
        // Carry across any weight already recorded in daily logs.
        db.execSQL(
            """
            INSERT OR IGNORE INTO weight_logs (date, weightKg)
            SELECT date, weightKg FROM daily_logs WHERE weightKg > 0
            """.trimIndent()
        )
    }
}

/** Every migration the app knows about. Pass to `addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4)
