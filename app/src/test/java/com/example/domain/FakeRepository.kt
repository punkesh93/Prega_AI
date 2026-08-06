package com.example.domain

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [PregnancyDao] so the gamification engine can be tested on the JVM
 * without Room, Android, or a device.
 *
 * Only the members the engine actually touches carry real behaviour; the rest
 * return empty so the fake stays readable.
 */
class FakeDao : PregnancyDao {

    private var progress: ProgressEntity? = null
    private val badges = mutableMapOf<String, BadgeEntity>()

    override fun getProgress(): Flow<ProgressEntity?> = flowOf(progress)
    override suspend fun getProgressOnce(): ProgressEntity? = progress
    override suspend fun upsertProgress(progress: ProgressEntity) { this.progress = progress }
    override suspend fun deleteProgress() { progress = null }

    override fun getBadges(): Flow<List<BadgeEntity>> = flowOf(badges.values.toList())
    override suspend fun getEarnedBadgeIds(): List<String> = badges.keys.toList()
    override suspend fun insertBadge(badge: BadgeEntity) { badges.putIfAbsent(badge.id, badge) }
    override suspend fun deleteAllBadges() { badges.clear() }

    // ── Unused by the engine ─────────────────────────────────────────────
    override fun getUserProfile(): Flow<UserProfileEntity?> = flowOf(null)
    override suspend fun insertUserProfile(profile: UserProfileEntity) = Unit
    override suspend fun deleteUserProfile() = Unit
    override fun getDailyLog(date: String): Flow<DailyLogEntity?> = flowOf(null)
    override suspend fun getDailyLogOnce(date: String): DailyLogEntity? = null
    override fun getAllDailyLogs(): Flow<List<DailyLogEntity>> = flowOf(emptyList())
    override fun getDailyLogsSince(since: String): Flow<List<DailyLogEntity>> = flowOf(emptyList())
    override suspend fun insertDailyLog(log: DailyLogEntity) = Unit
    override suspend fun deleteAllDailyLogs() = Unit
    override fun getAllKickLogs(): Flow<List<KickLogEntity>> = flowOf(emptyList())
    override suspend fun insertKickLog(kick: KickLogEntity) = Unit
    override suspend fun deleteKickLog(id: Int) = Unit
    override suspend fun deleteAllKickLogs() = Unit
    override fun getQuestsForDate(date: String): Flow<List<QuestEntity>> = flowOf(emptyList())
    override suspend fun getQuestsForDateOnce(date: String): List<QuestEntity> = emptyList()
    override suspend fun insertQuests(quests: List<QuestEntity>) = Unit
    override suspend fun updateQuest(quest: QuestEntity) = Unit
    override suspend fun pruneQuestsBefore(before: String) = Unit
    override suspend fun deleteAllQuests() = Unit
    override fun getMood(date: String): Flow<MoodEntity?> = flowOf(null)
    override fun getRecentMoods(limit: Int): Flow<List<MoodEntity>> = flowOf(emptyList())
    override suspend fun insertMood(mood: MoodEntity) = Unit
    override suspend fun deleteAllMoods() = Unit
    override fun getAppointments(): Flow<List<AppointmentEntity>> = flowOf(emptyList())
    override fun getNextAppointment(today: String): Flow<AppointmentEntity?> = flowOf(null)
    override suspend fun insertAppointment(appointment: AppointmentEntity) = Unit
    override suspend fun updateAppointment(appointment: AppointmentEntity) = Unit
    override suspend fun deleteAppointment(id: Int) = Unit
    override suspend fun deleteAllAppointments() = Unit
    override fun getContractions(): Flow<List<ContractionEntity>> = flowOf(emptyList())
    override suspend fun insertContraction(contraction: ContractionEntity) = Unit
    override suspend fun deleteAllContractions() = Unit
    override fun getWeights(): Flow<List<WeightEntity>> = flowOf(emptyList())
    override suspend fun insertWeight(weight: WeightEntity) = Unit
    override suspend fun deleteAllWeights() = Unit
}

/** Convenience factory used by the engine tests. */
@Suppress("FunctionName")
fun FakeRepository(): PregnancyRepository = PregnancyRepository(FakeDao())
