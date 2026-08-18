package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Core entities ─────────────────────────────────────────────────────────

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val currentWeek: Int = 12,
    val babyNamePlaceholder: String = "Little One",
    val dueDate: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val isGoogleSignedIn: Boolean = false,

    /**
     * Entitlement state. Source of truth is Google Play Billing — this field is
     * a local cache of it, refreshed on launch. Never write `true` here from UI
     * code; only the billing client may grant premium.
     */
    val isPremium: Boolean = false,
    val premiumPurchaseDate: String = "",
    val freeQuestionsRemaining: Int = 5,
    /**
     * yyyy-MM-dd of the last day freeQuestionsRemaining was topped back up
     * to 5. Without this, the counter only ever counted down and a free
     * user who used her 5 questions once was locked out of Prega AI
     * permanently (see MIGRATION_5_6, PregnancyViewModel.askCoach).
     */
    val lastQuestionResetDate: String = "",

    val lmpDate: String = "",
    val testDate: String = "",
    val eddDate: String = "",
    val billingRegion: String = "GLOBAL",

    // ── v4 additions ──
    /** Free text: allergies, vegetarian, halal, aversions. Fed to meal planning. */
    val dietaryPreferences: String = "",
    val onboardingComplete: Boolean = false,
    val notificationsEnabled: Boolean = true,
    /** Notifications never fire inside this window. HH:mm. */
    val quietHoursStart: String = "21:30",
    val quietHoursEnd: String = "08:00",
    val waterGoalGlasses: Int = 8,
    val isFirstPregnancy: Boolean = true,
)

@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val waterGlasses: Int = 0,
    val tookVitamins: Boolean = false,
    val sleptHours: Float = 8f,
    val weightKg: Float = 0f,
    /** Comma-separated. */
    val symptoms: String = "",
    // ── v4 additions ──
    val notes: String = "",
    /** 0 = not logged, otherwise 1..5. */
    val energyLevel: Int = 0,
)

@Entity(tableName = "kick_logs")
data class KickLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // yyyy-MM-dd
    val timestamp: Long,
    val count: Int,
    val durationSeconds: Int,
)

// ─── DAO ───────────────────────────────────────────────────────────────────

@Dao
interface PregnancyDao {

    // ── Journal ───────────────────────────────────────────────────────────
    @Insert
    suspend fun insertJournalEntry(entry: JournalEntity): Long

    @Query("SELECT * FROM journal_entries ORDER BY date DESC, id DESC")
    fun getJournalEntries(): Flow<List<JournalEntity>>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE date = :date")
    suspend fun journalCountForDate(date: String): Int

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteJournalEntry(id: Long)

    @Query("DELETE FROM journal_entries")
    suspend fun clearJournal()


    // Profile
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile WHERE id = 1")
    suspend fun deleteUserProfile()

    // Daily logs
    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    fun getDailyLog(date: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    suspend fun getDailyLogOnce(date: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllDailyLogs(): Flow<List<DailyLogEntity>>

    /**
     * Distinct dates with ANY sign of life, unioned across every daily touch
     * point (tiles, mood check-in, journal). Garden Visitors reads this —
     * see PregnancyViewModel.daysActive. A user who only ever does the daily
     * mood + journal check-in (the most common path) never writes a
     * daily_logs row, so daily_logs alone undercounts her badly.
     */
    @Query(
        "SELECT date FROM daily_logs " +
        "UNION SELECT date FROM mood_logs " +
        "UNION SELECT date FROM journal_entries"
    )
    fun getAllActiveDates(): Flow<List<String>>

    @Query("SELECT * FROM daily_logs WHERE date >= :since ORDER BY date ASC")
    fun getDailyLogsSince(since: String): Flow<List<DailyLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyLog(log: DailyLogEntity)

    @Query("DELETE FROM daily_logs")
    suspend fun deleteAllDailyLogs()

    // Kicks
    @Query("SELECT * FROM kick_logs ORDER BY timestamp DESC")
    fun getAllKickLogs(): Flow<List<KickLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKickLog(kick: KickLogEntity)

    @Query("DELETE FROM kick_logs WHERE id = :id")
    suspend fun deleteKickLog(id: Int)

    @Query("DELETE FROM kick_logs")
    suspend fun deleteAllKickLogs()

    // Progress
    @Query("SELECT * FROM progress WHERE id = 1 LIMIT 1")
    fun getProgress(): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress WHERE id = 1 LIMIT 1")
    suspend fun getProgressOnce(): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ProgressEntity)

    @Query("DELETE FROM progress")
    suspend fun deleteProgress()

    // Badges
    @Query("SELECT * FROM badges ORDER BY earnedDate DESC")
    fun getBadges(): Flow<List<BadgeEntity>>

    @Query("SELECT id FROM badges")
    suspend fun getEarnedBadgeIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadge(badge: BadgeEntity)

    @Query("DELETE FROM badges")
    suspend fun deleteAllBadges()

    // Quests
    @Query("SELECT * FROM quests WHERE date = :date")
    fun getQuestsForDate(date: String): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE date = :date")
    suspend fun getQuestsForDateOnce(date: String): List<QuestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuests(quests: List<QuestEntity>)

    @Update
    suspend fun updateQuest(quest: QuestEntity)

    @Query("DELETE FROM quests WHERE date < :before")
    suspend fun pruneQuestsBefore(before: String)

    @Query("DELETE FROM quests")
    suspend fun deleteAllQuests()

    // Mood
    @Query("SELECT * FROM mood_logs WHERE date = :date LIMIT 1")
    fun getMood(date: String): Flow<MoodEntity?>

    @Query("SELECT * FROM mood_logs ORDER BY date DESC LIMIT :limit")
    fun getRecentMoods(limit: Int = 30): Flow<List<MoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMood(mood: MoodEntity)

    @Query("DELETE FROM mood_logs")
    suspend fun deleteAllMoods()

    // Appointments
    @Query("SELECT * FROM appointments ORDER BY date ASC, time ASC")
    fun getAppointments(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE completed = 0 AND date >= :today ORDER BY date ASC LIMIT 1")
    fun getNextAppointment(today: String): Flow<AppointmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity)

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity)

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointment(id: Int)

    @Query("DELETE FROM appointments")
    suspend fun deleteAllAppointments()

    // Contractions
    @Query("SELECT * FROM contractions ORDER BY startTime DESC")
    fun getContractions(): Flow<List<ContractionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContraction(contraction: ContractionEntity)

    @Query("DELETE FROM contractions")
    suspend fun deleteAllContractions()

    // Weight
    @Query("SELECT * FROM weight_logs ORDER BY date ASC")
    fun getWeights(): Flow<List<WeightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(weight: WeightEntity)

    @Query("DELETE FROM weight_logs")
    suspend fun deleteAllWeights()
}

// ─── Database ──────────────────────────────────────────────────────────────

@Database(
    entities = [
        UserProfileEntity::class,
        DailyLogEntity::class,
        KickLogEntity::class,
        ProgressEntity::class,
        BadgeEntity::class,
        QuestEntity::class,
        MoodEntity::class,
        AppointmentEntity::class,
        ContractionEntity::class,
        WeightEntity::class,
        JournalEntity::class,
    ],
    version = 6,
    // Schemas are exported to app/schemas so migrations can be tested against
    // real historical schemas rather than written blind.
    exportSchema = true,
)
abstract class PregnancyDatabase : RoomDatabase() {
    abstract fun pregnancyDao(): PregnancyDao
}
