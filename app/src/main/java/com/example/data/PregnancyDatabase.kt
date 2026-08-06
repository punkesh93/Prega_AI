package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
    val isPremium: Boolean = false,
    val premiumPurchaseDate: String = "",
    val freeQuestionsRemaining: Int = 5,
    val lmpDate: String = "",
    val testDate: String = "",
    val eddDate: String = "",
    val billingRegion: String = "GLOBAL"
)

@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val waterGlasses: Int = 0,
    val tookVitamins: Boolean = false,
    val sleptHours: Float = 8f,
    val weightKg: Float = 0f,
    val symptoms: String = "" // Comma-separated list
)

@Entity(tableName = "kick_logs")
data class KickLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // YYYY-MM-DD
    val timestamp: Long,
    val count: Int,
    val durationSeconds: Int
)

@Dao
interface PregnancyDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile WHERE id = 1")
    suspend fun deleteUserProfile()

    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    fun getDailyLog(date: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllDailyLogs(): Flow<List<DailyLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyLog(log: DailyLogEntity)

    @Query("SELECT * FROM kick_logs ORDER BY timestamp DESC")
    fun getAllKickLogs(): Flow<List<KickLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKickLog(kick: KickLogEntity)

    @Query("DELETE FROM kick_logs WHERE id = :id")
    suspend fun deleteKickLog(id: Int)

    @Query("DELETE FROM daily_logs")
    suspend fun deleteAllDailyLogs()

    @Query("DELETE FROM kick_logs")
    suspend fun deleteAllKickLogs()
}

@Database(
    entities = [UserProfileEntity::class, DailyLogEntity::class, KickLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class PregnancyDatabase : RoomDatabase() {
    abstract fun pregnancyDao(): PregnancyDao
}
