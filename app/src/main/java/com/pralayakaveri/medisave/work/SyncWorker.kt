package com.pralayakaveri.medisave.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pralayakaveri.medisave.data.MedicineRepository

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = MedicineRepository(applicationContext)
        return try {
            repository.syncPendingItems()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed, retrying...", e)
            Result.retry()
        }
    }
}
