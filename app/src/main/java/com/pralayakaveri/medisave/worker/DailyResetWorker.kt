package com.pralayakaveri.medisave.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.medisave.data.AuthRepository
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.data.PreferenceManager

class DailyResetWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefManager = PreferenceManager(applicationContext)
        val todayStr = prefManager.getCurrentDateString()
        
        return try {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            
            if (userId != null) {
                val medicineRepository = MedicineRepository(applicationContext)
                
                // 1. Run local catch-up and Firestore sync
                android.util.Log.d("DailyResetWorker", "Daily Reset executed for date: $todayStr")
                medicineRepository.syncPendingResets(userId)
            } else {
                android.util.Log.d("DailyResetWorker", "No user logged in. Skipping logic but rescheduling.")
            }
            
            // 2. Schedule next run (Exact Midnight)
            com.pralayakaveri.medisave.work.WorkScheduler.scheduleDailyReset(applicationContext)
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailyResetWorker", "Reset failed for $todayStr", e)
            Result.retry()
        }
    }
}
