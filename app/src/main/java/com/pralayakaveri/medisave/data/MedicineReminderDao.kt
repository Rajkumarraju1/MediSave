package com.pralayakaveri.medisave.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: MedicineReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<MedicineReminderEntity>)

    @Delete
    suspend fun delete(reminder: MedicineReminderEntity)

    @Query("SELECT * FROM medicine_reminders WHERE id = :medicineId")
    suspend fun getById(medicineId: String): MedicineReminderEntity?

    @Query("SELECT * FROM medicine_reminders")
    suspend fun getAllReminders(): List<MedicineReminderEntity>
    
    @Query("SELECT * FROM medicine_reminders")
    fun observeAllReminders(): Flow<List<MedicineReminderEntity>>

    @Query("SELECT * FROM medicine_reminders WHERE profileId = :profileId")
    fun getAllByProfileFlow(profileId: String): Flow<List<MedicineReminderEntity>>

    @Query("DELETE FROM medicine_reminders WHERE id = :medicineId")
    suspend fun deleteById(medicineId: String)

    @Query("SELECT * FROM medicine_reminders WHERE syncPending = 1")
    suspend fun getPendingSyncs(): List<MedicineReminderEntity>

    @Transaction
    suspend fun updateStatusAndStockLocally(
        medicineId: String, 
        newPillsLeft: Int, 
        newTotalTaken: Int, 
        newStatusMap: Map<String, String>, 
        timestamp: Long
    ) {
        val med = getById(medicineId) ?: return
        val updated = med.copy(
            pillsLeft = newPillsLeft,
            totalTaken = newTotalTaken,
            statusMap = newStatusMap,
            lastUpdated = timestamp,
            syncPending = true,
            lastRefillNotifiedAt = if (newPillsLeft > med.pillsLeft) 0L else med.lastRefillNotifiedAt
        )
        insert(updated)
    }

    @Query("UPDATE medicine_reminders SET syncPending = 0 WHERE id = :medicineId")
    suspend fun markSyncComplete(medicineId: String)

    @Query("SELECT COUNT(*) FROM medicine_reminders WHERE profileId = :profileId")
    suspend fun countByProfile(profileId: String): Int

    @Query("UPDATE medicine_reminders SET profileId = :newProfileId WHERE profileId = :oldProfileId")
    suspend fun migrateProfileId(oldProfileId: String, newProfileId: String)
    @Query("UPDATE medicine_reminders SET lastRefillNotifiedAt = :timestamp, syncPending = 1 WHERE id = :medicineId")
    suspend fun updateRefillNotificationTime(medicineId: String, timestamp: Long)

    @Transaction
    suspend fun refillStock(medicineId: String, quantity: Int, timestamp: Long) {
        val med = getById(medicineId) ?: return
        val newPillsLeft = med.pillsLeft + quantity
        val updated = med.copy(
            pillsLeft = newPillsLeft,
            totalStock = newPillsLeft,
            lastRefillNotifiedAt = 0L,
            lastUpdated = timestamp,
            syncPending = true
        )
        insert(updated)
    }

    @Query("UPDATE medicine_reminders SET nextCheckAt = :timestamp, syncPending = 1 WHERE id = :medicineId")
    suspend fun updateNextCheckAt(medicineId: String, timestamp: Long)

    @Query("DELETE FROM medicine_reminders WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: String)

    @Query("SELECT * FROM medicine_reminders WHERE profileId = :profileId")
    suspend fun getAllByProfile(profileId: String): List<MedicineReminderEntity>
}
