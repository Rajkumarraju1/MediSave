package com.pralayakaveri.medisave.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DoseLogEntity)

    @Query("SELECT * FROM dose_logs WHERE syncPending = 1")
    suspend fun getPendingSyncs(): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun getById(id: String): DoseLogEntity?

    @Query("UPDATE dose_logs SET syncPending = 0 WHERE id = :logId")
    suspend fun markSyncComplete(logId: String)

    @Query("SELECT * FROM dose_logs WHERE userId = :userId AND date >= :startDate")
    fun getLogsForUser(userId: String, startDate: String): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE userId = :userId AND date = :date AND time = :time AND medicineName = :medicineName LIMIT 1")
    suspend fun getLog(userId: String, medicineName: String, date: String, time: String): DoseLogEntity?

    @Query("UPDATE dose_logs SET userId = :newUserId WHERE userId = :oldUserId")
    suspend fun migrateUserId(oldUserId: String, newUserId: String)

    @Query("DELETE FROM dose_logs WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)
}
