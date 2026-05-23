package com.pralayakaveri.medisave.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.pralayakaveri.medisave.model.Medicine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import com.pralayakaveri.medisave.data.PreferenceManager
import kotlinx.coroutines.flow.firstOrNull
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.MedicineReminderEntity
import android.content.Context
import com.pralayakaveri.medisave.model.DoseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.pralayakaveri.medisave.work.SyncWorker
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit
import java.time.Instant

class MedicineRepository(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val localDb = AppDatabase.getDatabase(context)
    private val preferenceManager = PreferenceManager(context)

    private fun mapFirestoreException(error: com.google.firebase.firestore.FirebaseFirestoreException): ResourceState<Nothing> {
        return when (error.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                ResourceState.PermissionDenied
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
            com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                ResourceState.NetworkFailure
            else -> 
                ResourceState.Error("DB_UNEXPECTED_ERROR")
        }
    }

    private fun extractLong(value: Any?): Long {
        return when (value) {
            is Long -> value
            is com.google.firebase.Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private fun extractTimestamp(value: Any?): com.google.firebase.Timestamp? {
        return when (value) {
            is com.google.firebase.Timestamp -> value
            is Long -> com.google.firebase.Timestamp(java.util.Date(value))
            is Number -> com.google.firebase.Timestamp(java.util.Date(value.toLong()))
            else -> null
        }
    }

    fun getMedicinesFlowLocal(): Flow<List<Medicine>> {
        return localDb.medicineReminderDao().observeAllReminders().map { entities ->
            entities.map { it.toMedicine() }
        }
    }

    fun getMedicinesFlowLocalByProfile(profileId: String): Flow<List<Medicine>> {
        return localDb.medicineReminderDao().getAllByProfileFlow(profileId).map { entities ->
            entities.distinctBy { it.id }.map { it.toMedicine() }
        }
    }

    fun getMedicinesFlowState(userId: String): Flow<ResourceState<List<Medicine>>> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "getMedicinesFlowState blocked: unauthorized or unauthenticated")
            trySend(ResourceState.PermissionDenied)
            close()
            return@callbackFlow
        }

        trySend(ResourceState.Loading)
        val collection = db.collection("users").document(userId).collection("medicines")
        
        val listener = collection.addSnapshotListener { snapshot, error ->
            try {
                if (error != null) {
                    android.util.Log.e("MedicineRepo", "Error observing medicines for $userId", error)
                    trySend(mapFirestoreException(error))
                    return@addSnapshotListener
                }
                
                if (snapshot == null || snapshot.isEmpty) {
                    trySend(ResourceState.Empty)
                    return@addSnapshotListener
                }
                
                val medicines = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toMedicine()
                    } catch (e: Exception) {
                        android.util.Log.e("MedicineRepo", "Error parsing medicine ${doc.id}", e)
                        null
                    }
                }
                
                // SYNC with local Room cache using timestamp validation
                cacheLocally(medicines)
                
                trySend(ResourceState.Success(medicines))
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "getMedicinesFlowState snapshot callback exception", e)
                trySend(ResourceState.Error("DATA_CORRUPTION_ERROR"))
            }
        }
        
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("MedicineRepo", "Error removing medicines snapshot listener", ex)
            }
        }
    }

    fun getMedicinesFlow(userId: String): Flow<List<Medicine>> {
        return getMedicinesFlowState(userId).map { state ->
            if (state is ResourceState.Success) state.data else emptyList()
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toMedicine(): Medicine {
        val doc = this
        val statusMapRaw = doc.get("statusMap")
        val statusMap = if (statusMapRaw is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            statusMapRaw as Map<String, String>
        } else {
            emptyMap<String, String>()
        }
        
        var pillsLeft = doc.getLong("pillsLeft")?.toInt() ?: 0
        var totalStock = doc.getLong("totalStock")?.toInt() ?: 0
        var isStockInferred = doc.getBoolean("isStockInferred") ?: false
        
        // Medical-grade backfill safety
        if (totalStock == 0 && pillsLeft > 0 && !doc.contains("totalStock")) {
            totalStock = pillsLeft
            isStockInferred = true
        }
        
        val createdAtRaw = doc.getTimestamp("createdAt")
        val createdAtMillis = createdAtRaw?.toDate()?.time ?: 0L
        val cloudStartDate = doc.getString("startDate")
        
        // Fallback for legacy data: If startDate is missing, use createdAt date
        val finalStartDate = if (cloudStartDate.isNullOrEmpty() && createdAtMillis > 0) {
            try {
                java.time.Instant.ofEpochMilli(createdAtMillis)
                    .atZone(java.time.ZoneId.of(doc.getString("timezone") ?: "UTC"))
                    .toLocalDate()
                    .toString()
            } catch (e: Exception) { "" }
        } else {
            cloudStartDate ?: ""
        }

        val lastUpdatedTs = extractTimestamp(doc.get("lastUpdated"))
        val createdAtTs = extractTimestamp(doc.get("createdAt"))
        
        return Medicine(
            id = doc.id,
            name = doc.getString("name") ?: "",
            dose = doc.getString("dose") ?: "",
            times = (doc.get("times") as? List<*>)?.map { it.toString() } ?: listOf("08:00"),
            instruction = doc.getString("instruction") ?: "",
            statusMap = statusMap,
            totalTaken = (doc.get("totalTaken") as? Number)?.toInt() ?: 0,
            totalMissed = (doc.get("totalMissed") as? Number)?.toInt() ?: 0,
            totalScheduled = (doc.get("totalScheduled") as? Number)?.toInt() ?: 0,
            pillsLeft = pillsLeft,
            totalStock = totalStock,
            isStockInferred = isStockInferred,
            lastUpdated = lastUpdatedTs,
            syncPending = false,
            doseQuantity = (doc.get("doseQuantity") as? Number)?.toInt() ?: 1,
            refillAt = (doc.get("refillAt") as? Number)?.toInt() ?: 5,
            colorHex = doc.getString("colorHex") ?: "#1D9E75",
            repeatDays = (doc.get("repeatDays") as? List<*>)?.map { (it as? Number)?.toInt() ?: 1 } ?: listOf(1, 2, 3, 4, 5, 6, 7),
            history = emptyMap(), 
            profileId = doc.getString("profileId") ?: "primary",
            createdAt = createdAtTs,
            startDate = finalStartDate,
            timezone = "Asia/Kolkata",
            caregiverAlertEnabled = doc.getBoolean("caregiverAlertEnabled") ?: true,
            lastRefillNotifiedAt = extractLong(doc.get("lastRefillNotifiedAt")),
            nextCheckAt = extractLong(doc.get("nextCheckAt"))
        )
    }

    suspend fun syncPendingResets(userId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "syncPendingResets blocked: unauthorized or unauthenticated")
            return
        }

        val todayStr = preferenceManager.getCurrentDateString()
        val lastResetStr = preferenceManager.lastResetDate.firstOrNull() ?: todayStr
        
        if (lastResetStr < todayStr) {
            android.util.Log.i("MedicineRepo", "Starting Daily Reset catch-up from $lastResetStr to $todayStr")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val lastDate = sdf.parse(lastResetStr) ?: java.util.Date()
            val calendar = java.util.Calendar.getInstance()
            calendar.time = lastDate
            
            // Catch up for each missed day
            while (true) {
                val currentEvalDate = sdf.format(calendar.time)
                if (currentEvalDate >= todayStr) break
                
                processHistoricalReset(userId, currentEvalDate)
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            
            preferenceManager.saveLastResetDate(todayStr)
            android.util.Log.i("MedicineRepo", "Daily Reset completed for $todayStr")
        } else {
            android.util.Log.d("MedicineRepo", "Daily Reset skipped: already reset today ($todayStr)")
        }
    }

    private suspend fun processHistoricalReset(userId: String, dateStr: String) {
        // Redundant as statusMap is already date-keyed.
    }

    private fun cacheLocally(medicines: List<Medicine>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                medicines.forEach { cloudMedicine: Medicine ->
                    val local = localDb.medicineReminderDao().getById(cloudMedicine.id)
                    
                    if (local == null) {
                        android.util.Log.d("MedicineRepo", "Caching NEW medicine: ${cloudMedicine.id} | profile: ${cloudMedicine.profileId}")
                        localDb.medicineReminderDao().insert(MedicineReminderEntity.fromMedicine(cloudMedicine))
                    } else {
                        // Strict timestamp reconciliation
                        val cloudTime = cloudMedicine.lastUpdated?.toDate()?.time ?: 0L
                        if (cloudTime > local.lastUpdated) {
                            localDb.medicineReminderDao().insert(MedicineReminderEntity.fromMedicine(cloudMedicine))
                        } else if (cloudTime < local.lastUpdated && !local.syncPending) {
                            // Local is newer but flag is lost? Re-flag or trust local Room database.
                        } else if (cloudTime == local.lastUpdated) {
                            // Drift detection
                            if (local.pillsLeft != cloudMedicine.pillsLeft || local.statusMap != cloudMedicine.statusMap) {
                                android.util.Log.w("MedicineRepo", "Drift Detected [${cloudMedicine.id}]. Local: ${local.pillsLeft}, Cloud: ${cloudMedicine.pillsLeft}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Error caching medicines", e)
            }
        }
    }

    suspend fun addMedicine(userId: String, medicine: Medicine): Result<String> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "addMedicine blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized addMedicine call"))
        }

        return try {
            android.util.Log.d("MedicineRepo", "addMedicine locally first: ${medicine.name}")
            
            // 1. Generate ID first
            val docRef = db.collection("users").document(userId).collection("medicines").document()
            val generatedId = docRef.id
            
            // 2. Insert into Local Room IMMEDIATELY to prevent scheduling blink/race
            val anchorTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
            val nextCheck = medicine.calculateNextCheckAt(anchorTime)
            
            val finalMedicine = medicine.copy(id = generatedId, nextCheckAt = nextCheck)
            val entity = MedicineReminderEntity.fromMedicine(finalMedicine).copy(syncPending = true)
            localDb.medicineReminderDao().insert(entity)
            
            val medicineData = hashMapOf(
                "name" to medicine.name,
                "dose" to medicine.dose,
                "times" to medicine.times,
                "instruction" to medicine.instruction,
                "statusMap" to medicine.statusMap,
                "pillsLeft" to medicine.pillsLeft,
                "totalStock" to medicine.totalStock,
                "isStockInferred" to medicine.isStockInferred,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(), // Assigned birth timestamp
                "timezone" to medicine.timezone,
                "doseQuantity" to medicine.doseQuantity,
                "refillAt" to medicine.refillAt,
                "colorHex" to medicine.colorHex,
                "repeatDays" to medicine.repeatDays,
                "profileId" to medicine.profileId,
                "startDate" to medicine.startDate,
                "caregiverAlertEnabled" to medicine.caregiverAlertEnabled,
                "lastRefillNotifiedAt" to medicine.lastRefillNotifiedAt,
                "nextCheckAt" to nextCheck
            )
            
            // 3. Write to Firestore with timeout/failure isolation
            try {
                docRef.set(medicineData).await()
                localDb.medicineReminderDao().markSyncComplete(generatedId)
                android.util.Log.i("MedicineRepo", "addMedicine synced to Firestore: $generatedId")
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Firestore addMedicine failed, queued for background sync", e)
                scheduleSyncWorker()
            }
            
            Result.success(generatedId)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "addMedicine failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMedicine(userId: String, medicineId: String): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "deleteMedicine blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized deleteMedicine call"))
        }

        return try {
            // 1. Fetch from Room BEFORE deletion
            val medicineEntity = localDb.medicineReminderDao().getById(medicineId)
            if (medicineEntity != null) {
                val medicine = medicineEntity.toMedicine()
                // 2. Cancel Alarms
                com.pralayakaveri.medisave.reminder.ReminderManager(context).cancelAlarmsForMedicine(medicine)
            }

            android.util.Log.i("MedicineRepo", "deleteMedicine locally first: $medicineId")
            // 3. Delete from Room first
            localDb.medicineReminderDao().deleteById(medicineId)

            // 4. Delete from Firestore with isolation
            try {
                db.collection("users").document(userId)
                    .collection("medicines")
                    .document(medicineId)
                    .delete()
                    .await()
                android.util.Log.i("MedicineRepo", "deleteMedicine deleted from Firestore: $medicineId")
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Firestore delete failed for $medicineId", e)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "Local delete failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateMedicineStatus(
        userId: String, 
        medicineId: String, 
        date: String? = null, 
        time: String, 
        newStatus: String
    ): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "updateMedicineStatus blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized updateMedicineStatus call"))
        }

        return try {
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val targetDate = date ?: todayStr
            
            // 1. Cross-Day Safety: Only allow mutating today's doses
            if (targetDate != todayStr) {
                android.util.Log.w("MedicineRepo", "Blocked cross-day modification for $medicineId at $targetDate")
                return Result.failure(IllegalArgumentException("Blocked cross-day modification"))
            }

            // 2. Local Authority & Pre-validation
            val localMed = localDb.medicineReminderDao().getById(medicineId) 
                ?: return Result.failure(NoSuchElementException("Medicine $medicineId not found in local database"))
            val statusKey = "${targetDate}_${time}"
            val oldStatus = localMed.statusMap[statusKey] ?: DoseStatus.PENDING.name

            // Idempotency Check
            if (oldStatus == newStatus) return Result.success(Unit)

            // Stock Safety Gate: Block TAKEN if there are insufficient pills for a full dose.
            if (newStatus == "TAKEN" && localMed.pillsLeft < localMed.doseQuantity) {
                android.util.Log.w(
                    "MedicineRepo",
                    "Blocked TAKEN [$medicineId]: pillsLeft=${localMed.pillsLeft} < doseQuantity=${localMed.doseQuantity}"
                )
                return Result.failure(IllegalStateException("Insufficient stock"))
            }

            var newPillsLeft = localMed.pillsLeft
            var newTotalTaken = localMed.totalTaken
            
            // Revert old state effects
            if (oldStatus == "TAKEN") {
                newPillsLeft += localMed.doseQuantity
                newTotalTaken--
            }

            // Apply new state effects
            if (newStatus == "TAKEN") {
                newPillsLeft -= localMed.doseQuantity
                newTotalTaken++
            }
            
            // Clamp for safety
            newPillsLeft = newPillsLeft.coerceIn(0, localMed.totalStock)
            val newStatusMap = localMed.statusMap.toMutableMap().apply { this[statusKey] = newStatus }
            val updateTimestamp = java.time.Instant.now().toEpochMilli()

            // Recalculate nextCheckAt
            val anchorTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
            val nextCheck = localMed.toMedicine().copy(statusMap = newStatusMap).calculateNextCheckAt(anchorTime)

            android.util.Log.i("MedicineRepo", "StockUpdate locally first | MedId: $medicineId | Action: $newStatus | Stock: ${localMed.pillsLeft} -> $newPillsLeft")

            // 3. Atomic Local Update (Room First)
            localDb.medicineReminderDao().updateStatusAndStockLocally(
                medicineId, newPillsLeft.toInt(), newTotalTaken.toInt(), newStatusMap, updateTimestamp
            )
            
            // 3a. Update nextCheckAt locally
            localDb.medicineReminderDao().updateNextCheckAt(medicineId, nextCheck)

            // 3b. Record flattened dose log
            val logId = "${userId}_${medicineId}_${targetDate}_${time}"
            val doseLog = DoseLogEntity(
                id = logId,
                userId = userId,
                medicineId = medicineId,
                medicineName = localMed.name,
                date = targetDate,
                time = time,
                status = newStatus,
                lastUpdatedAt = updateTimestamp,
                notified = false,
                syncPending = true
            )
            localDb.doseLogDao().insert(doseLog)

            // 4. Trigger WorkManager for FireStore Sync Retryability
            scheduleSyncWorker()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "updateMedicineStatus failed", e)
            Result.failure(e)
        }
    }

    suspend fun refillMedicine(userId: String, medicineId: String, quantity: Int): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "refillMedicine blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized refillMedicine call"))
        }

        return try {
            val timestamp = java.time.Instant.now().toEpochMilli()
            android.util.Log.i("MedicineRepo", "refillMedicine locally first: $medicineId | quantity: $quantity")
            
            // 1. Update SQLite locally
            localDb.medicineReminderDao().refillStock(medicineId, quantity, timestamp)
            
            // 2. Schedule WorkManager sync to push to Firestore
            scheduleSyncWorker()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "refillMedicine failed", e)
            Result.failure(e)
        }
    }

    private fun scheduleSyncWorker() {
        com.pralayakaveri.medisave.work.WorkScheduler.scheduleSyncWorker(context)
    }

    suspend fun syncPendingItems() {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            android.util.Log.e("MedicineRepo", "syncPendingItems aborted: Unauthenticated user")
            return
        }
        
        // Safety Check: Check for deletion lock
        try {
            val userDoc = db.collection("users").document(userId).get().await()
            if (userDoc.getBoolean("deletionInProgress") == true) {
                android.util.Log.w("MedicineRepo", "Sync aborted: Account deletion in progress for $userId")
                return
            }
        } catch (e: Exception) {
            android.util.Log.w("MedicineRepo", "Sync aborted: Could not check deletion lock status", e)
            return
        }

        // 1. Sync Medicines
        syncPendingMedicines(userId)
        
        // 2. Sync Dose Logs
        syncPendingDoseLogs(userId)

        // 3. Heartbeat & Adherence
        updateUserHeartbeat(userId)
    }

    private suspend fun updateUserHeartbeat(userId: String) {
        val lastUpdate = preferenceManager.lastActiveTimestamp.firstOrNull() ?: 0L
        val now = System.currentTimeMillis()
        
        // 5-minute throttle (300,000 ms)
        if (now - lastUpdate > 300_000) {
            try {
                val adherence = calculateAdherencelocally(userId)
                val userRef = db.collection("users").document(userId)
                
                android.util.Log.i("MedicineRepo", "Updating user heartbeat and average adherence locally calculated: $adherence%")
                db.runBatch { batch ->
                    batch.update(userRef, "lastActiveAt", com.google.firebase.Timestamp.now())
                    batch.update(userRef, "averageAdherence", adherence)
                }.await()
                
                preferenceManager.saveLastActiveTimestamp(now)
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Heartbeat failed", e)
            }
        }
    }

    private fun isTimePassed(time: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val now = java.util.Calendar.getInstance()
            val currentTime = sdf.format(now.time)
            currentTime >= time
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun calculateAdherencelocally(userId: String): Int {
        val activeProfileId = preferenceManager.activeProfileId.firstOrNull() ?: "primary"
        val rawList = localDb.medicineReminderDao().getAllReminders().filter { it.profileId == activeProfileId }
        
        val forcedZone = java.time.ZoneId.of("Asia/Kolkata")
        val anchorTime = java.time.ZonedDateTime.now(forcedZone)
        
        var takenCount = 0
        var scheduledCount = 0
        
        for (i in 0..6) {
            val loopDate = anchorTime.toLocalDate().minusDays(i.toLong())
            val dateStr = loopDate.toString()
            val isToday = i == 0
            
            rawList.forEach { medEntity ->
                val med = medEntity.toMedicine()
                med.times.forEach { time ->
                    if (com.pralayakaveri.medisave.util.ScheduleUtils.isDoseValid(med, dateStr, time)) {
                        val status = med.getStatusAt(dateStr, time)
                        val isPast = if (isToday) {
                            val doseTime = java.time.LocalTime.parse(time)
                            anchorTime.toLocalTime().isAfter(doseTime)
                        } else true
                        
                        if (status.startsWith("TAKEN")) {
                            takenCount++
                            scheduledCount++
                        } else if (isPast && (status == "MISSED" || status == "PENDING")) {
                            scheduledCount++
                        }
                    }
                }
            }
        }
        
        return if (scheduledCount > 0) ((takenCount.toFloat() / scheduledCount) * 100).toInt() else 100
    }

    suspend fun syncPendingMedicines(userId: String) {
        val pendingSyncs = localDb.medicineReminderDao().getPendingSyncs()
        if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
            android.util.Log.i("MedicineRepo", "[Sync Audit] Starting syncPendingMedicines for user $userId. Total pending items: ${pendingSyncs.size}")
        }
        
        pendingSyncs.forEach { localMed ->
            val docRef = db.collection("users").document(userId).collection("medicines").document(localMed.id)
            try {
                // Pre-push local syncPending verification to avoid process resurrection race duplicates
                val latestLocalMed = localDb.medicineReminderDao().getById(localMed.id)
                if (latestLocalMed == null) {
                    if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                        android.util.Log.w("MedicineRepo", "[Sync Audit] Skipping medicine ${localMed.id}: entity deleted locally during sync cycle")
                    }
                    return@forEach
                }
                if (!latestLocalMed.syncPending) {
                    if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                        android.util.Log.i("MedicineRepo", "[Sync Audit] Skipping medicine ${localMed.id}: already marked syncPending == false (already successfully synced in concurrent run)")
                    }
                    return@forEach
                }

                if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                    android.util.Log.i("MedicineRepo", "[Sync Audit] Syncing pending medicine change for ${latestLocalMed.id} inside isolated transaction")
                }
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    if (!snapshot.exists()) {
                        if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                            android.util.Log.i("MedicineRepo", "[Sync Audit] Medicine ${latestLocalMed.id} does not exist in Firestore. Creating document.")
                        }
                        val medicineData = hashMapOf(
                            "name" to latestLocalMed.name,
                            "dose" to latestLocalMed.dose,
                            "times" to latestLocalMed.times,
                            "instruction" to latestLocalMed.instruction,
                            "statusMap" to latestLocalMed.statusMap,
                            "pillsLeft" to latestLocalMed.pillsLeft,
                            "totalStock" to latestLocalMed.totalStock,
                            "isStockInferred" to latestLocalMed.isStockInferred,
                            "lastUpdated" to com.google.firebase.Timestamp(java.util.Date(latestLocalMed.lastUpdated)),
                            "createdAt" to com.google.firebase.Timestamp(java.util.Date(latestLocalMed.createdAt)),
                            "timezone" to latestLocalMed.timezone,
                            "doseQuantity" to latestLocalMed.doseQuantity,
                            "refillAt" to latestLocalMed.refillAt,
                            "colorHex" to latestLocalMed.colorHex,
                            "repeatDays" to latestLocalMed.repeatDays,
                            "profileId" to latestLocalMed.profileId,
                            "startDate" to latestLocalMed.startDate,
                            "caregiverAlertEnabled" to latestLocalMed.caregiverAlertEnabled,
                            "lastRefillNotifiedAt" to latestLocalMed.lastRefillNotifiedAt,
                            "nextCheckAt" to latestLocalMed.nextCheckAt
                        )
                        transaction.set(docRef, medicineData)
                    } else {
                        val cloudLastUpdatedTs = snapshot.getTimestamp("lastUpdated")
                        val cloudLastUpdated = cloudLastUpdatedTs?.toDate()?.time ?: 0L
                        
                        if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                            android.util.Log.i("MedicineRepo", "[Sync Audit] Medicine ${latestLocalMed.id} exists. Local lastUpdated: ${latestLocalMed.lastUpdated}, Cloud lastUpdated: $cloudLastUpdated")
                        }
                        // Only update cloud if local is explicitly newer
                        if (latestLocalMed.lastUpdated >= cloudLastUpdated) {
                            transaction.update(docRef, "statusMap", latestLocalMed.statusMap)
                            transaction.update(docRef, "pillsLeft", latestLocalMed.pillsLeft)
                            transaction.update(docRef, "totalStock", latestLocalMed.totalStock)
                            transaction.update(docRef, "totalTaken", latestLocalMed.totalTaken)
                            transaction.update(docRef, "nextCheckAt", latestLocalMed.nextCheckAt)
                            transaction.update(docRef, "lastRefillNotifiedAt", latestLocalMed.lastRefillNotifiedAt)
                            transaction.update(docRef, "lastUpdated", com.google.firebase.Timestamp(java.util.Date(latestLocalMed.lastUpdated)))
                        } else {
                            if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                                android.util.Log.i("MedicineRepo", "[Sync Audit] Cloud document for ${latestLocalMed.id} is newer. Skipping update to avoid regression.")
                            }
                        }
                    }
                }.await()
                
                // On success, remove pending flag from Room
                localDb.medicineReminderDao().markSyncComplete(latestLocalMed.id)
                if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                    android.util.Log.i("MedicineRepo", "[Sync Audit] Sync completed successfully for medicine: ${latestLocalMed.id}")
                }
            } catch (e: Exception) {
                if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
                    android.util.Log.e("MedicineRepo", "[Sync Audit] Firestore sync failed for ${localMed.id}", e)
                }
                throw e // Propagate to trigger WorkManager retry
            }
        }
    }

    private suspend fun syncPendingDoseLogs(userId: String) {
        val pendingLogs = localDb.doseLogDao().getPendingSyncs()
        if (pendingLogs.isEmpty()) return

        android.util.Log.i("MedicineRepo", "Syncing ${pendingLogs.size} pending dose logs to Firestore")

        // Batch size 400
        pendingLogs.chunked(400).forEach { batchList ->
            try {
                db.runBatch { batch ->
                    batchList.forEach { logEntity ->
                        // FLAT PATH: doseLogs/{userId}/logs/{logId}
                        val docRef = db.collection("doseLogs")
                            .document(userId)
                            .collection("logs")
                            .document(logEntity.id)
                        
                        val data = hashMapOf(
                            "userId" to logEntity.userId,
                            "medicineId" to logEntity.medicineId,
                            "medicineName" to logEntity.medicineName,
                            "date" to logEntity.date, // Kept as key for local indexing
                            "time" to logEntity.time,
                            "status" to logEntity.status,
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "lastUpdatedAt" to com.google.firebase.Timestamp(java.util.Date(logEntity.lastUpdatedAt)),
                            "notifiedTo" to emptyList<String>(),
                            "caregiverAlertEnabled" to logEntity.caregiverAlertEnabled
                        )
                        batch.set(docRef, data)
                    }
                }.await()
                
                // Mark all in this batch as complete
                batchList.forEach { 
                    localDb.doseLogDao().markSyncComplete(it.id)
                }
                android.util.Log.i("MedicineRepo", "Sync completed for ${batchList.size} dose logs batch")
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Batch sync failed for dose logs", e)
                throw e
            }
        }
    }

    suspend fun resetDailyStatuses(userId: String) {
        // Obsolete
    }

    suspend fun resetToday(userId: String, medicineId: String): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "resetToday blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized resetToday call"))
        }

        return try {
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val updateTimestamp = java.time.Instant.now().toEpochMilli()
            
            android.util.Log.i("MedicineRepo", "resetToday locally first: $medicineId")
            // 1. Update SQLite Room locally first
            val localMed = localDb.medicineReminderDao().getById(medicineId)
            if (localMed != null) {
                val newStatusMap = localMed.statusMap.filterKeys { k -> !k.startsWith(todayStr) }
                val updated = localMed.copy(
                    statusMap = newStatusMap,
                    lastUpdated = updateTimestamp,
                    syncPending = true
                )
                localDb.medicineReminderDao().insert(updated)
            }
            
            // 2. Queue background SyncWorker
            scheduleSyncWorker()
            
            // 3. Update Firestore with isolation
            try {
                val docRef = db.collection("users").document(userId).collection("medicines").document(medicineId)
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val statusMap = (snapshot.get("statusMap") as? Map<*, *>)?.filterKeys { k -> 
                        !(k as String).startsWith(todayStr) 
                    } ?: emptyMap<String, String>()
                    
                    transaction.update(docRef, "statusMap", statusMap)
                    transaction.update(docRef, "lastUpdated", com.google.firebase.Timestamp(java.util.Date(updateTimestamp)))
                }.await()
                localDb.medicineReminderDao().markSyncComplete(medicineId)
                android.util.Log.i("MedicineRepo", "resetToday completed on Firestore for medicine: $medicineId")
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Firestore resetToday failed, left as syncPending", e)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "resetToday failed", e)
            Result.failure(e)
        }
    }

    suspend fun clearHistory(userId: String, medicineId: String): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "clearHistory blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized clearHistory call"))
        }

        return try {
            val updateTimestamp = java.time.Instant.now().toEpochMilli()
            android.util.Log.i("MedicineRepo", "clearHistory locally first for medicine: $medicineId")
            
            // 1. Update Room first
            val localMed = localDb.medicineReminderDao().getById(medicineId)
            if (localMed != null) {
                val updated = localMed.copy(
                    statusMap = emptyMap(),
                    totalTaken = 0,
                    totalMissed = 0,
                    lastUpdated = updateTimestamp,
                    syncPending = true
                )
                localDb.medicineReminderDao().insert(updated)
            }
            
            // 2. Schedule SyncWorker
            scheduleSyncWorker()
            
            // 3. Write to Firestore
            try {
                val docRef = db.collection("users").document(userId).collection("medicines").document(medicineId)
                db.runTransaction { transaction ->
                    transaction.update(docRef, "statusMap", emptyMap<String, String>())
                    transaction.update(docRef, "totalTaken", 0)
                    transaction.update(docRef, "totalMissed", 0)
                    transaction.update(docRef, "lastUpdated", com.google.firebase.Timestamp(java.util.Date(updateTimestamp)))
                }.await()
                localDb.medicineReminderDao().markSyncComplete(medicineId)
                android.util.Log.i("MedicineRepo", "clearHistory synced on Firestore for medicine: $medicineId")
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Firestore clearHistory failed, left as syncPending", e)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "clearHistory failed", e)
            Result.failure(e)
        }
    }

    suspend fun clearGlobalHistory(userId: String): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "clearGlobalHistory blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized clearGlobalHistory call"))
        }

        return try {
            val updateTimestamp = java.time.Instant.now().toEpochMilli()
            android.util.Log.i("MedicineRepo", "clearGlobalHistory locally first for all medicines of user: $userId")
            
            // 1. Update Room first (all medicines)
            val allLocal = localDb.medicineReminderDao().getAllReminders()
            val updatedList = allLocal.map { localMed ->
                localMed.copy(
                    statusMap = emptyMap(),
                    totalTaken = 0,
                    totalMissed = 0,
                    lastUpdated = updateTimestamp,
                    syncPending = true
                )
            }
            localDb.medicineReminderDao().insertAll(updatedList)
            
            // 2. Schedule SyncWorker
            scheduleSyncWorker()
            
            // 3. Write to Firestore
            try {
                val collection = db.collection("users").document(userId).collection("medicines")
                val snapshot = collection.get().await()
                
                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "statusMap", emptyMap<String, String>())
                    batch.update(doc.reference, "totalTaken", 0)
                    batch.update(doc.reference, "totalMissed", 0)
                    batch.update(doc.reference, "lastUpdated", com.google.firebase.Timestamp(java.util.Date(updateTimestamp)))
                }
                batch.commit().await()
                
                // Mark all completed locally
                allLocal.forEach {
                    localDb.medicineReminderDao().markSyncComplete(it.id)
                }
                android.util.Log.i("MedicineRepo", "clearGlobalHistory batch commit successfully processed on Firestore")
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Firestore clearGlobalHistory failed, left as syncPending", e)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "clearGlobalHistory failed", e)
            Result.failure(e)
        }
    }

    suspend fun clearOldMedicines(userId: String): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("MedicineRepo", "clearOldMedicines blocked: unauthorized or unauthenticated")
            return Result.failure(Exception("Unauthorized clearOldMedicines call"))
        }

        return try {
            android.util.Log.i("MedicineRepo", "clearOldMedicines locally first for user: $userId")
            // 1. Clear local SQLite Room tables first
            localDb.medicineReminderDao().deleteByProfileId("primary")
            
            // 2. Delete from Firestore with isolation
            try {
                val collection = db.collection("users").document(userId).collection("medicines")
                val snapshot = collection.get().await()
                db.runBatch { batch ->
                    snapshot.documents.forEach { batch.delete(it.reference) }
                }.await()
                android.util.Log.d("MedicineRepo", "Firestore medicines cleared successfully for user: $userId")
            } catch (e: Exception) {
                android.util.Log.e("MedicineRepo", "Error clearing Firestore data during clearOldMedicines", e)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MedicineRepo", "clearOldMedicines failed", e)
            Result.failure(e)
        }
    }

    private fun extractLong(doc: com.google.firebase.firestore.DocumentSnapshot, field: String): Long? {
        return try {
            // Priority 1: Firestore Timestamp
            val ts = doc.getTimestamp(field)
            if (ts != null) return ts.toDate().time
            
            // Priority 2: Standard Long
            doc.getLong(field)
        } catch (e: Exception) {
            // Priority 3: Fallback if casting fails
            (doc.get(field) as? Number)?.toLong()
        }
    }
}
