package com.example.data

import kotlinx.coroutines.flow.Flow

class PregnancyRepository(private val pregnancyDao: PregnancyDao) {
    fun getUserProfile(): Flow<UserProfileEntity?> = pregnancyDao.getUserProfile()
    suspend fun saveUserProfile(profile: UserProfileEntity) = pregnancyDao.insertUserProfile(profile)
    suspend fun deleteUserProfile() = pregnancyDao.deleteUserProfile()

    fun getDailyLog(date: String): Flow<DailyLogEntity?> = pregnancyDao.getDailyLog(date)
    fun getAllDailyLogs(): Flow<List<DailyLogEntity>> = pregnancyDao.getAllDailyLogs()
    suspend fun saveDailyLog(log: DailyLogEntity) = pregnancyDao.insertDailyLog(log)

    fun getAllKickLogs(): Flow<List<KickLogEntity>> = pregnancyDao.getAllKickLogs()
    suspend fun saveKickLog(kick: KickLogEntity) = pregnancyDao.insertKickLog(kick)
    suspend fun deleteKickLog(id: Int) = pregnancyDao.deleteKickLog(id)
    suspend fun deleteAllDailyLogs() = pregnancyDao.deleteAllDailyLogs()
    suspend fun deleteAllKickLogs() = pregnancyDao.deleteAllKickLogs()
}
