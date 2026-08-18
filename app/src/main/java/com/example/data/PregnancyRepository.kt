package com.example.data

import kotlinx.coroutines.flow.Flow

class PregnancyRepository(private val dao: PregnancyDao) {

    // ── Journal ───────────────────────────────────────────────────────────
    suspend fun addJournalEntry(entry: JournalEntity): Long = dao.insertJournalEntry(entry)
    fun getJournalEntries() = dao.getJournalEntries()
    suspend fun hasJournalEntryFor(date: String) = dao.journalCountForDate(date) > 0
    suspend fun deleteJournalEntry(id: Long) = dao.deleteJournalEntry(id)


    // ── Profile ──────────────────────────────────────────────────────────
    fun getUserProfile(): Flow<UserProfileEntity?> = dao.getUserProfile()
    suspend fun saveUserProfile(profile: UserProfileEntity) = dao.insertUserProfile(profile)
    suspend fun deleteUserProfile() = dao.deleteUserProfile()

    // ── Daily logs ───────────────────────────────────────────────────────
    fun getDailyLog(date: String): Flow<DailyLogEntity?> = dao.getDailyLog(date)
    suspend fun getDailyLogOnce(date: String): DailyLogEntity? = dao.getDailyLogOnce(date)
    fun getAllDailyLogs(): Flow<List<DailyLogEntity>> = dao.getAllDailyLogs()
    fun getDailyLogsSince(since: String): Flow<List<DailyLogEntity>> = dao.getDailyLogsSince(since)
    suspend fun saveDailyLog(log: DailyLogEntity) = dao.insertDailyLog(log)
    suspend fun deleteAllDailyLogs() = dao.deleteAllDailyLogs()
    fun getAllActiveDates(): Flow<List<String>> = dao.getAllActiveDates()

    // ── Kicks ────────────────────────────────────────────────────────────
    fun getAllKickLogs(): Flow<List<KickLogEntity>> = dao.getAllKickLogs()
    suspend fun saveKickLog(kick: KickLogEntity) = dao.insertKickLog(kick)
    suspend fun deleteKickLog(id: Int) = dao.deleteKickLog(id)
    suspend fun deleteAllKickLogs() = dao.deleteAllKickLogs()

    // ── Progress & gamification ──────────────────────────────────────────
    fun getProgress(): Flow<ProgressEntity?> = dao.getProgress()
    suspend fun getProgressOnce(): ProgressEntity = dao.getProgressOnce() ?: ProgressEntity()
    suspend fun saveProgress(progress: ProgressEntity) = dao.upsertProgress(progress)

    fun getBadges(): Flow<List<BadgeEntity>> = dao.getBadges()
    suspend fun getEarnedBadgeIds(): List<String> = dao.getEarnedBadgeIds()
    suspend fun awardBadge(badge: BadgeEntity) = dao.insertBadge(badge)

    fun getQuestsForDate(date: String): Flow<List<QuestEntity>> = dao.getQuestsForDate(date)
    suspend fun getQuestsForDateOnce(date: String): List<QuestEntity> = dao.getQuestsForDateOnce(date)
    suspend fun saveQuests(quests: List<QuestEntity>) = dao.insertQuests(quests)
    suspend fun updateQuest(quest: QuestEntity) = dao.updateQuest(quest)
    suspend fun pruneQuestsBefore(date: String) = dao.pruneQuestsBefore(date)

    // ── Mood ─────────────────────────────────────────────────────────────
    fun getMood(date: String): Flow<MoodEntity?> = dao.getMood(date)
    fun getRecentMoods(limit: Int = 30): Flow<List<MoodEntity>> = dao.getRecentMoods(limit)
    suspend fun saveMood(mood: MoodEntity) = dao.insertMood(mood)

    // ── Appointments ─────────────────────────────────────────────────────
    fun getAppointments(): Flow<List<AppointmentEntity>> = dao.getAppointments()
    fun getNextAppointment(today: String): Flow<AppointmentEntity?> = dao.getNextAppointment(today)
    suspend fun saveAppointment(appointment: AppointmentEntity) = dao.insertAppointment(appointment)
    suspend fun updateAppointment(appointment: AppointmentEntity) = dao.updateAppointment(appointment)
    suspend fun deleteAppointment(id: Int) = dao.deleteAppointment(id)

    // ── Contractions ─────────────────────────────────────────────────────
    fun getContractions(): Flow<List<ContractionEntity>> = dao.getContractions()
    suspend fun saveContraction(contraction: ContractionEntity) = dao.insertContraction(contraction)
    suspend fun deleteAllContractions() = dao.deleteAllContractions()

    // ── Weight ───────────────────────────────────────────────────────────
    fun getWeights(): Flow<List<WeightEntity>> = dao.getWeights()
    suspend fun saveWeight(weight: WeightEntity) = dao.insertWeight(weight)

    /**
     * Full local erasure, for the GDPR "delete my data" path. Everything, in
     * one place, so nothing is quietly left behind.
     */
    suspend fun deleteEverything() {
        dao.clearJournal()
        dao.deleteUserProfile()
        dao.deleteAllDailyLogs()
        dao.deleteAllKickLogs()
        dao.deleteProgress()
        dao.deleteAllBadges()
        dao.deleteAllQuests()
        dao.deleteAllMoods()
        dao.deleteAllAppointments()
        dao.deleteAllContractions()
        dao.deleteAllWeights()
    }
}
