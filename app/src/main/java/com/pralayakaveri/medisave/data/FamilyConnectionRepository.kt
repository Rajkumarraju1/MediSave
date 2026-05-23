package com.pralayakaveri.medisave.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.pralayakaveri.medisave.model.Connection
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

class FamilyConnectionRepository {
    private val db = FirebaseFirestore.getInstance()

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

    suspend fun findUserByCode(code: String): com.pralayakaveri.medisave.data.UserEntity? {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "findUserByCode blocked: Not authenticated")
            return null
        }
        return try {
            val query = db.collection("users")
                .whereEqualTo("connectionCode", code.uppercase())
                .get()
                .await()
            
            if (query.isEmpty) return null
            
            val doc = query.documents.first()
            com.pralayakaveri.medisave.data.UserEntity(
                userId = doc.id,
                name = doc.getString("name") ?: "User",
                email = "", // Restricted
                phone = "",
                age = doc.getString("age") ?: "--",
                gender = "",
                conditions = emptyList(),
                language = "",
                connectionCode = doc.getString("connectionCode") ?: ""
            )
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error in findUserByCode", e)
            null
        }
    }

    suspend fun sendRequest(receiverId: String, relation: String): Result<Unit> {
        val senderId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid 
            ?: return Result.failure(Exception("Not authenticated"))

        if (senderId == receiverId) {
            return Result.failure(Exception("You cannot connect to yourself"))
        }

        android.util.Log.d("FamilyConnectionRepo", "Sending connection request from $senderId to $receiverId (relation: $relation)")

        val connectionId = listOf(senderId, receiverId).sorted().joinToString("_")
        val activeConnRef = db.collection("active_connections").document(connectionId)

        return try {
            // Validate that the receiverId exists in the Firestore users collection
            val receiverDoc = db.collection("users").document(receiverId).get().await()
            if (!receiverDoc.exists()) {
                return Result.failure(Exception("Invalid target user"))
            }

            // Check for bi-directional requests
            val outgoingDocs = db.collection("connections")
                .whereEqualTo("senderId", senderId)
                .whereEqualTo("receiverId", receiverId)
                .get()
                .await()
                .documents

            val incomingDocs = db.collection("connections")
                .whereEqualTo("senderId", receiverId)
                .whereEqualTo("receiverId", senderId)
                .get()
                .await()
                .documents

            if (outgoingDocs.any { it.getString("status") == "pending" }) {
                return Result.failure(Exception("Request already exists"))
            }

            if (incomingDocs.any { it.getString("status") == "pending" }) {
                return Result.failure(Exception("Incoming request already exists"))
            }

            // Anti-Spam 90-Day Rolling Cooldown Validation
            val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
            val recentDeclines = outgoingDocs.filter { doc ->
                doc.getString("status") == "declined" &&
                (doc.getLong("declinedAt") ?: doc.getLong("timestamp") ?: 0L) >= ninetyDaysAgo
            }

            val declineCount = recentDeclines.size
            if (declineCount > 0) {
                val mostRecentDecline = recentDeclines.maxByOrNull { doc ->
                    doc.getLong("declinedAt") ?: doc.getLong("timestamp") ?: 0L
                }
                val mostRecentDeclinedAt = mostRecentDecline?.getLong("declinedAt") ?: mostRecentDecline?.getLong("timestamp") ?: 0L
                
                val cooldown = when {
                    declineCount <= 1 -> 24L * 60 * 60 * 1000L // 24 hours
                    declineCount == 2 -> 72L * 60 * 60 * 1000L // 3 days
                    else -> 7L * 24 * 60 * 60 * 1000L // 7 days
                }

                val timeElapsed = System.currentTimeMillis() - mostRecentDeclinedAt
                if (timeElapsed < cooldown) {
                    val errorCode = when {
                        declineCount <= 1 -> "Cooldown: 1"
                        declineCount == 2 -> "Cooldown: 2"
                        else -> "Cooldown: 3"
                    }
                    return Result.failure(Exception(errorCode))
                }
            }

            db.runTransaction { transaction ->
                val activeConnSnapshot = transaction.get(activeConnRef)
                if (activeConnSnapshot.exists()) {
                    throw Exception("You are already connected to this user")
                }

                val connectionData = hashMapOf(
                    "senderId" to senderId,
                    "receiverId" to receiverId,
                    "relation" to relation,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis(),
                    "notified" to false,
                    "handledBySender" to false
                )
                val newRequestRef = db.collection("connections").document()
                transaction.set(newRequestRef, connectionData)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error sending connection request", e)
            Result.failure(e)
        }
    }

    fun observeIncomingRequestsState(userId: String): Flow<ResourceState<List<Connection>>> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("FamilyConnectionRepo", "observeIncomingRequestsState blocked: unauthorized or not logged in")
            trySend(ResourceState.PermissionDenied)
            close()
            return@callbackFlow
        }
        
        // Trigger non-blocking, throttled background self-healing reconciliation task
        triggerReconciliation(userId, this)

        trySend(ResourceState.Loading)
        val listener = db.collection("connections")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                try {
                    if (error != null) {
                        android.util.Log.e("FamilyConnectionRepo", "observeIncomingRequestsState snapshot error", error)
                        trySend(mapFirestoreException(error))
                        return@addSnapshotListener
                    }
                    
                    if (snapshot == null || snapshot.isEmpty) {
                        trySend(ResourceState.Empty)
                        return@addSnapshotListener
                    }

                    val requests = snapshot.documents.map { doc ->
                        Connection(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            receiverId = doc.getString("receiverId") ?: "",
                            relation = doc.getString("relation") ?: "",
                            status = doc.getString("status") ?: "pending",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            notified = doc.getBoolean("notified") ?: false,
                            handledBySender = doc.getBoolean("handledBySender") ?: false
                        )
                    }
                    trySend(ResourceState.Success(requests))
                } catch (e: Exception) {
                    android.util.Log.e("FamilyConnectionRepo", "observeIncomingRequestsState snapshot callback exception", e)
                    trySend(ResourceState.Error("DATA_CORRUPTION_ERROR"))
                }
            }
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("FamilyConnectionRepo", "Error removing observeIncomingRequestsState snapshot listener", ex)
            }
        }
    }

    fun observeIncomingRequests(userId: String): Flow<List<Connection>> {
        return observeIncomingRequestsState(userId).map { state ->
            if (state is ResourceState.Success) state.data else emptyList()
        }
    }

    fun observeAcceptedConnectionsState(userId: String): Flow<ResourceState<List<Connection>>> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("FamilyConnectionRepo", "observeAcceptedConnectionsState blocked: unauthorized or not logged in")
            trySend(ResourceState.PermissionDenied)
            close()
            return@callbackFlow
        }
        
        trySend(ResourceState.Loading)
        val listener = db.collection("active_connections")
            .where(
                Filter.or(
                    Filter.equalTo("userA", userId),
                    Filter.equalTo("userB", userId)
                )
            )
            .addSnapshotListener { snapshot, error ->
                try {
                    if (error != null) {
                        android.util.Log.e("FamilyConnectionRepo", "observeAcceptedConnectionsState snapshot error", error)
                        trySend(mapFirestoreException(error))
                        return@addSnapshotListener
                    }
                    
                    if (snapshot == null || snapshot.isEmpty) {
                        trySend(ResourceState.Empty)
                        return@addSnapshotListener
                    }
                    
                    val connectionDocs = snapshot.documents

                    val connections = connectionDocs.map { doc ->
                        val userA = doc.getString("userA") ?: ""
                        val userB = doc.getString("userB") ?: ""
                        
                        val labels = doc.get("labels") as? Map<*, *>
                        val resolvedRelation = labels?.get(userId)?.toString() 
                            ?: doc.getString("relation") 
                            ?: "Family Member"

                        val acceptedAtTs = doc.get("acceptedAt")
                        val timestampMillis = when (acceptedAtTs) {
                            is com.google.firebase.Timestamp -> acceptedAtTs.toDate().time
                            is Number -> acceptedAtTs.toLong()
                            else -> 0L
                        }

                        Connection(
                            id = doc.id,
                            senderId = userA,
                            receiverId = userB,
                            relation = resolvedRelation,
                            labels = labels?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(),
                            status = "accepted",
                            timestamp = timestampMillis,
                            notified = false,
                            handledBySender = false
                        )
                    }
                    trySend(ResourceState.Success(connections))
                } catch (e: Exception) {
                    android.util.Log.e("FamilyConnectionRepo", "observeAcceptedConnectionsState snapshot callback exception", e)
                    trySend(ResourceState.Error("DATA_CORRUPTION_ERROR"))
                }
            }
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("FamilyConnectionRepo", "Error removing observeAcceptedConnectionsState snapshot listener", ex)
            }
        }
    }

    fun observeAcceptedConnections(userId: String): Flow<List<Connection>> {
        return observeAcceptedConnectionsState(userId).map { state ->
            if (state is ResourceState.Success) state.data else emptyList()
        }
    }

    fun observeOutgoingRequestsState(userId: String): Flow<ResourceState<List<Connection>>> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || currentUid != userId) {
            android.util.Log.e("FamilyConnectionRepo", "observeOutgoingRequestsState blocked: unauthorized or not logged in")
            trySend(ResourceState.PermissionDenied)
            close()
            return@callbackFlow
        }
        
        trySend(ResourceState.Loading)
        val listener = db.collection("connections")
            .whereEqualTo("senderId", userId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                try {
                    if (error != null) {
                        android.util.Log.e("FamilyConnectionRepo", "observeOutgoingRequestsState snapshot error", error)
                        trySend(mapFirestoreException(error))
                        return@addSnapshotListener
                    }
                    
                    if (snapshot == null || snapshot.isEmpty) {
                        trySend(ResourceState.Empty)
                        return@addSnapshotListener
                    }
                    
                    val requests = snapshot.documents.map { doc ->
                        Connection(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            receiverId = doc.getString("receiverId") ?: "",
                            relation = doc.getString("relation") ?: "",
                            status = "pending",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            notified = doc.getBoolean("notified") ?: false,
                            handledBySender = doc.getBoolean("handledBySender") ?: false
                        )
                    }
                    trySend(ResourceState.Success(requests))
                } catch (e: Exception) {
                    android.util.Log.e("FamilyConnectionRepo", "observeOutgoingRequestsState snapshot callback exception", e)
                    trySend(ResourceState.Error("DATA_CORRUPTION_ERROR"))
                }
            }
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("FamilyConnectionRepo", "Error removing observeOutgoingRequestsState snapshot listener", ex)
            }
        }
    }

    fun observeOutgoingRequests(userId: String): Flow<List<Connection>> {
        return observeOutgoingRequestsState(userId).map { state ->
            if (state is ResourceState.Success) state.data else emptyList()
        }
    }

    suspend fun acceptRequest(
        requestId: String, 
        senderId: String, 
        receiverId: String, 
        senderRelation: String,
        receiverRelation: String
    ): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid 
            ?: return Result.failure(Exception("Not authenticated"))

        if (currentUid != receiverId) {
            android.util.Log.e("FamilyConnectionRepo", "acceptRequest blocked: unauthorized UID $currentUid attempting to accept on behalf of $receiverId")
            return Result.failure(Exception("Unauthorized request acceptance"))
        }

        return try {
            val connectionId = listOf(senderId, receiverId).sorted().joinToString("_")
            val requestRef = db.collection("connections").document(requestId)
            val connectionRef = db.collection("active_connections").document(connectionId)

            android.util.Log.i("FamilyConnectionRepo", "Accepting request $requestId inside isolated transaction | connId: $connectionId")

            db.runTransaction { transaction ->
                val connectionSnapshot = transaction.get(connectionRef)
                
                // 1. Create permanent connection link if not exists
                if (!connectionSnapshot.exists()) {
                    val connectionData = hashMapOf(
                        "userA" to senderId,
                        "userB" to receiverId,
                        "relation" to senderRelation, // Legacy fallback
                        "labels" to mapOf(
                            senderId to senderRelation, // How sender sees receiver
                            receiverId to receiverRelation // How receiver sees sender
                        ),
                        "acceptedAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(connectionRef, connectionData)
                } else {
                    // Update existing labels if connection already exists
                    transaction.update(connectionRef, "labels.$senderId", senderRelation)
                    transaction.update(connectionRef, "labels.$receiverId", receiverRelation)
                }

                // 2. Update request status to accepted and reset handledBySender for safety
                transaction.update(requestRef, "status", "accepted")
                transaction.update(requestRef, "handledBySender", false)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Transaction failed inside acceptRequest", e)
            Result.failure(e)
        }
    }

    suspend fun declineRequest(requestId: String): Result<Unit> {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid 
            ?: return Result.failure(Exception("Not authenticated"))

        return try {
            android.util.Log.i("FamilyConnectionRepo", "Soft-declining request $requestId by user $currentUid")
            db.collection("connections").document(requestId).update(
                mapOf(
                    "status" to "declined",
                    "declinedAt" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error soft-declining request $requestId", e)
            Result.failure(e)
        }
    }

    suspend fun getUserPublicProfile(userId: String): Map<String, Any>? {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "getUserPublicProfile blocked: Not authenticated")
            return null
        }
        return try {
            val doc = db.collection("users").document(userId).get().await()
            if (!doc.exists()) return null
            
            val timestamp = doc.getTimestamp("createdAt")?.toDate() ?: java.util.Date()
            mapOf(
                "id" to doc.id,
                "name" to (doc.getString("name") ?: "MediSave User"),
                "joinedAt" to timestamp.time
            )
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Failed to retrieve public profile for $userId", e)
            null
        }
    }

    suspend fun markRequestAsHandled(requestId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "markRequestAsHandled blocked: Not authenticated")
            return
        }
        try {
            android.util.Log.d("FamilyConnectionRepo", "User $currentUid marking request $requestId as handled")
            db.collection("connections").document(requestId).update("handledBySender", true).await()
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Failed to mark request $requestId handled", e)
        }
    }

    data class WeeklyStats(
        val medicineCount: Int,
        val adherence: Int,
        val missedCount: Int
    )

    suspend fun getMemberWeeklyStats(memberId: String): WeeklyStats {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "getMemberWeeklyStats blocked: Not authenticated")
            return WeeklyStats(0, 100, 0)
        }
        return try {
            val medicines = db.collection("users").document(memberId)
                .collection("medicines").get().await()
            
            val medicineCount = medicines.size()
            
            // Weekly Adherence calculation (Last 7 days)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val calendar = java.util.Calendar.getInstance()
            
            var totalMissed = 0
            var totalTaken = 0
            
            for (i in 0 until 7) {
                val dateStr = sdf.format(calendar.time)
                val logs = db.collection("doseLogs")
                    .document(memberId)
                    .collection(dateStr).get().await()
                
                logs.documents.forEach { doc ->
                    val status = doc.getString("status")
                    if (status == "TAKEN") totalTaken++
                    if (status == "MISSED") totalMissed++
                }
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            
            val total = totalTaken + totalMissed
            val adherence = if (total > 0) (totalTaken * 100) / total else 100
            
            WeeklyStats(medicineCount, adherence, totalMissed)
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error calculating weekly stats for member $memberId", e)
            WeeklyStats(0, 100, 0)
        }
    }

    suspend fun getMemberAdherence(memberUserId: String): Int {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "getMemberAdherence blocked: Not authenticated")
            return 100
        }
        return try {
            val userDoc = db.collection("users").document(memberUserId).get().await()
            val precomputed = userDoc.getLong("averageAdherence")
            if (precomputed != null) return precomputed.toInt()

            // Fallback to manual calculation - use flat logs with UTC today window
            val now = java.util.Calendar.getInstance()
            now.set(java.util.Calendar.HOUR_OF_DAY, 0)
            now.set(java.util.Calendar.MINUTE, 0)
            now.set(java.util.Calendar.SECOND, 0)
            now.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = com.google.firebase.Timestamp(now.time)
            
            val logsQuery = db.collection("doseLogs")
                .document(memberUserId)
                .collection("logs")
                .whereGreaterThanOrEqualTo("timestamp", startTime)
                .get()
                .await()
                
            val logs = logsQuery.documents
            val taken = logs.count { it.getString("status") == "TAKEN" }
            val relevant = logs.count { it.getString("status") == "TAKEN" || it.getString("status") == "MISSED" }
            
            if (relevant > 0) (taken * 100) / relevant else 100
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error calculating adherence for member $memberUserId", e)
            100
        }
    }

    fun listenToMemberLogs(
        memberId: String, 
        startTime: com.google.firebase.Timestamp, 
        endTime: com.google.firebase.Timestamp
    ): Flow<List<com.pralayakaveri.medisave.ui.TimelineItem>> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "listenToMemberLogs blocked: Not authenticated")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("doseLogs")
            .document(memberId)
            .collection("logs")
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .whereLessThan("timestamp", endTime)
            .orderBy("timestamp")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                try {
                    if (error != null) {
                        android.util.Log.e("FamilyConnectionRepo", "listenToMemberLogs snapshot error for member $memberId", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    
                    val logs = snapshot?.documents?.mapNotNull { doc ->
                        // Guard against null timestamp latency
                        val ts = doc.getTimestamp("timestamp") ?: return@mapNotNull null
                        
                        com.pralayakaveri.medisave.ui.TimelineItem(
                            medicineName = doc.getString("medicineName") ?: "Unknown",
                            time = doc.getString("time") ?: "",
                            status = doc.getString("status") ?: "PENDING",
                            medId = doc.getString("medicineId") ?: ""
                        )
                    } ?: emptyList()
                    
                    trySend(logs)
                } catch (e: Exception) {
                    android.util.Log.e("FamilyConnectionRepo", "listenToMemberLogs snapshot callback exception", e)
                    trySend(emptyList())
                }
            }
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("FamilyConnectionRepo", "Error removing listenToMemberLogs snapshot listener", ex)
            }
        }
    }

    fun listenToWeeklyLogs(
        memberId: String,
        startTime: com.google.firebase.Timestamp
    ): Flow<List<Map<String, Any>>> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "listenToWeeklyLogs blocked: Not authenticated")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("doseLogs")
            .document(memberId)
            .collection("logs")
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .orderBy("timestamp")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                try {
                    if (error != null) {
                        android.util.Log.e("FamilyConnectionRepo", "listenToWeeklyLogs snapshot error for member $memberId", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    
                    val logs = snapshot?.documents?.mapNotNull { doc ->
                        val ts = doc.getTimestamp("timestamp") ?: return@mapNotNull null
                        mapOf(
                            "status" to (doc.getString("status") ?: "PENDING"),
                            "timestamp" to ts,
                            "medicineId" to (doc.getString("medicineId") ?: ""),
                            "date" to (doc.getString("date") ?: ""),
                            "time" to (doc.getString("time") ?: "")
                        )
                    } ?: emptyList()
                    
                    trySend(logs)
                } catch (e: Exception) {
                    android.util.Log.e("FamilyConnectionRepo", "listenToWeeklyLogs snapshot callback exception", e)
                    trySend(emptyList())
                }
            }
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("FamilyConnectionRepo", "Error removing listenToWeeklyLogs snapshot listener", ex)
            }
        }
    }

    fun listenToMemberProfile(memberId: String): Flow<Map<String, Any>?> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "listenToMemberProfile blocked: Not authenticated")
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(memberId)
            .addSnapshotListener { snapshot, error ->
                try {
                    if (error != null) {
                        android.util.Log.e("FamilyConnectionRepo", "listenToMemberProfile snapshot error for member $memberId", error)
                        trySend(null)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.data)
                } catch (e: Exception) {
                    android.util.Log.e("FamilyConnectionRepo", "listenToMemberProfile snapshot callback exception", e)
                    trySend(null)
                }
            }
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("FamilyConnectionRepo", "Error removing listenToMemberProfile snapshot listener", ex)
            }
        }
    }

    suspend fun removeConnection(currentUserId: String, targetUserId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null || (currentUid != currentUserId && currentUid != targetUserId)) {
            android.util.Log.e("FamilyConnectionRepo", "removeConnection blocked: unauthorized UID $currentUid attempting to disconnect $currentUserId and $targetUserId")
            return
        }

        try {
            val connectionId = listOf(currentUserId, targetUserId).sorted().joinToString("_")
            android.util.Log.i("FamilyConnectionRepo", "Removing connection link $connectionId in batch operation")

            // Find connection request documents in BOTH directions
            val query = db.collection("connections")
                .where(
                    Filter.or(
                        Filter.and(
                            Filter.equalTo("senderId", currentUserId),
                            Filter.equalTo("receiverId", targetUserId)
                        ),
                        Filter.and(
                            Filter.equalTo("senderId", targetUserId),
                            Filter.equalTo("receiverId", currentUserId)
                        )
                    )
                ).get().await()

            db.runBatch { batch ->
                // 1. Delete the active link
                batch.delete(db.collection("active_connections").document(connectionId))
                
                // 2. Mark historical requests as revoked
                query.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "status" to "revoked",
                        "handledBySender" to true
                    ))
                }
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error inside removeConnection batch update", e)
        }
    }

    fun listenToMemberMedicinesState(memberId: String): Flow<ResourceState<List<com.pralayakaveri.medisave.model.Medicine>>> = callbackFlow {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            android.util.Log.e("FamilyConnectionRepo", "listenToMemberMedicinesState blocked: Not authenticated")
            trySend(ResourceState.PermissionDenied)
            close()
            return@callbackFlow
        }

        trySend(ResourceState.Loading)
        val listener = db.collection("users").document(memberId)
            .collection("medicines")
            .addSnapshotListener { snapshot, error ->
                try {
                    if (error != null) {
                        android.util.Log.e("FamilyConnectionRepo", "listenToMemberMedicinesState snapshot error for member $memberId", error)
                        trySend(mapFirestoreException(error))
                        return@addSnapshotListener
                    }
                    
                    if (snapshot == null || snapshot.isEmpty) {
                        trySend(ResourceState.Empty)
                        return@addSnapshotListener
                    }
                    
                    val medicines = snapshot.documents.mapNotNull { doc ->
                        try {
                            val statusMap = (doc.get("statusMap") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap()
                            
                            val lastUpdatedTs = extractTimestamp(doc.get("lastUpdated"))
                            val createdAtTs = extractTimestamp(doc.get("createdAt"))

                            com.pralayakaveri.medisave.model.Medicine(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                dose = doc.getString("dose") ?: "",
                                times = (doc.get("times") as? List<*>)?.map { it.toString() } ?: listOf("08:00"),
                                instruction = doc.getString("instruction") ?: "",
                                statusMap = statusMap,
                                totalTaken = (doc.get("totalTaken") as? Number)?.toInt() ?: 0,
                                totalMissed = (doc.get("totalMissed") as? Number)?.toInt() ?: 0,
                                totalScheduled = (doc.get("totalScheduled") as? Number)?.toInt() ?: 0,
                                pillsLeft = (doc.get("pillsLeft") as? Number)?.toInt() ?: 0,
                                totalStock = (doc.get("totalStock") as? Number)?.toInt() ?: 0,
                                isStockInferred = doc.getBoolean("isStockInferred") ?: false,
                                lastUpdated = lastUpdatedTs,
                                syncPending = false,
                                doseQuantity = (doc.get("doseQuantity") as? Number)?.toInt() ?: 1,
                                refillAt = (doc.get("refillAt") as? Number)?.toInt() ?: 5,
                                colorHex = doc.getString("colorHex") ?: "#1D9E75",
                                repeatDays = (doc.get("repeatDays") as? List<*>)?.map { (it as? Number)?.toInt() ?: 1 } ?: listOf(1, 2, 3, 4, 5, 6, 7),
                                profileId = doc.getString("profileId") ?: "primary",
                                createdAt = createdAtTs,
                                startDate = doc.getString("startDate") ?: "",
                                timezone = "Asia/Kolkata",
                                caregiverAlertEnabled = doc.getBoolean("caregiverAlertEnabled") ?: true,
                                lastRefillNotifiedAt = extractLong(doc.get("lastRefillNotifiedAt"))
                            )

                        } catch (e: Exception) {
                            android.util.Log.e("FamilyConnectionRepo", "Error parsing medicine ${doc.id}", e)
                            null
                        }
                    }
                    trySend(ResourceState.Success(medicines))
                } catch (e: Exception) {
                    android.util.Log.e("FamilyConnectionRepo", "listenToMemberMedicinesState snapshot callback exception", e)
                    trySend(ResourceState.Error("DATA_CORRUPTION_ERROR"))
                }
            }
        awaitClose {
            try {
                listener.remove()
            } catch (ex: Exception) {
                android.util.Log.e("FamilyConnectionRepo", "Error removing listenToMemberMedicinesState snapshot listener", ex)
            }
        }
    }

    fun listenToMemberMedicines(memberId: String): Flow<List<com.pralayakaveri.medisave.model.Medicine>> {
        return listenToMemberMedicinesState(memberId).map { state ->
            if (state is ResourceState.Success) state.data else emptyList()
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

    suspend fun updateConnectionLabel(targetUserId: String, newLabel: String) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val connectionId = listOf(currentUserId, targetUserId).sorted().joinToString("_")
        
        try {
            android.util.Log.i("FamilyConnectionRepo", "User $currentUserId updating label for connection $connectionId to $newLabel")
            
            // Asymmetric Safeguard: Only update the key for the current user
            db.collection("active_connections").document(connectionId)
                .update("labels.$currentUserId", newLabel).await()
            
            // Also update the historical request for backward compatibility in list views
            val requests = db.collection("connections")
                .whereEqualTo("status", "accepted")
                .get().await()
            
            db.runBatch { batch ->
                requests.documents.filter { doc ->
                    val s = doc.getString("senderId") ?: ""
                    val r = doc.getString("receiverId") ?: ""
                    listOf(s, r).sorted().joinToString("_") == connectionId
                }.forEach { doc ->
                    batch.update(doc.reference, "labels.$currentUserId", newLabel)
                    // Legacy fallback for old clients
                    batch.update(doc.reference, "relation", newLabel)
                }
            }.await()
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error updating connection label for $connectionId", e)
        }
    }

    suspend fun disconnectMember(targetUserId: String) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            android.util.Log.i("FamilyConnectionRepo", "User $currentUserId initiating disconnection of member $targetUserId")
            removeConnection(currentUserId, targetUserId)
        } catch (e: Exception) {
            android.util.Log.e("FamilyConnectionRepo", "Error disconnecting member $targetUserId", e)
        }
    }

    /**
     * Session-Throttled Background Reconciliation System (Self-Healing)
     */
    suspend fun reconcileCaregiverState(userId: String) {
        val startTime = System.currentTimeMillis()
        android.util.Log.i("CaregiverTelemetry", "[Reconciliation Start] User: $userId")
        
        try {
            // 1. Fetch pending requests involving this user (both incoming and outgoing)
            val incomingPending = db.collection("connections")
                .whereEqualTo("receiverId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
                .documents

            val outgoingPending = db.collection("connections")
                .whereEqualTo("senderId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
                .documents

            val allPending = (incomingPending + outgoingPending).distinctBy { it.id }
            if (allPending.isEmpty()) {
                android.util.Log.i("CaregiverTelemetry", "[Reconciliation End] No pending requests to reconcile. Completed in ${System.currentTimeMillis() - startTime}ms")
                return
            }

            var healedCount = 0
            var duplicateCount = 0
            var orphanCount = 0
            var selfPurgeCount = 0

            // Group pending requests by sorted sender-receiver pair key
            val uniquePendingPairs = mutableMapOf<String, MutableList<com.google.firebase.firestore.DocumentSnapshot>>()
            allPending.forEach { doc ->
                val s = doc.getString("senderId") ?: ""
                val r = doc.getString("receiverId") ?: ""
                if (s.isNotEmpty() && r.isNotEmpty()) {
                    val pairKey = if (s < r) "${s}_${r}" else "${r}_${s}"
                    uniquePendingPairs.getOrPut(pairKey) { mutableListOf() }.add(doc)
                }
            }

            // Pre-fetch active connection document state and user profile presence in parallel
            val pairKeys = uniquePendingPairs.keys.toList()
            val activeConnTasks = pairKeys.map { key ->
                key to db.collection("active_connections").document(key).get()
            }
            val activeConns = activeConnTasks.map { (key, task) ->
                key to task.await()
            }.toMap()

            val userIdsToCheck = uniquePendingPairs.flatMap { (_, docs) ->
                val s = docs.first().getString("senderId") ?: ""
                val r = docs.first().getString("receiverId") ?: ""
                listOf(s, r)
            }.filter { it.isNotEmpty() }.toSet()

            val userTasks = userIdsToCheck.map { uid ->
                uid to db.collection("users").document(uid).get()
            }
            val usersExistMap = userTasks.map { (uid, task) ->
                uid to task.await().exists()
            }.toMap()

            // Run atomic write batch cleanup
            db.runBatch { batch ->
                uniquePendingPairs.forEach { (pairKey, docs) ->
                    val senderId = docs.first().getString("senderId") ?: ""
                    val receiverId = docs.first().getString("receiverId") ?: ""

                    // 1. Self-referential purge
                    if (senderId == receiverId) {
                        docs.forEach { doc ->
                            batch.delete(doc.reference)
                            selfPurgeCount++
                        }
                        return@forEach
                    }

                    // 2. Orphaned request purge (if sender or receiver doesn't exist)
                    val senderExists = usersExistMap[senderId] ?: false
                    val receiverExists = usersExistMap[receiverId] ?: false
                    if (!senderExists || !receiverExists) {
                        docs.forEach { doc ->
                            batch.delete(doc.reference)
                            orphanCount++
                        }
                        return@forEach
                    }

                    // 3. Interrupted state healing
                    val activeConnExists = activeConns[pairKey]?.exists() ?: false
                    if (activeConnExists) {
                        docs.forEach { doc ->
                            batch.update(doc.reference, "status", "accepted")
                            healedCount++
                        }
                        return@forEach
                    }

                    // 4. Deduplication (keep newest, delete duplicates)
                    if (docs.size > 1) {
                        val sorted = docs.sortedByDescending { doc ->
                            doc.getLong("timestamp") ?: 0L
                        }
                        val newest = sorted.first()
                        val duplicates = sorted.drop(1)
                        duplicates.forEach { doc ->
                            batch.delete(doc.reference)
                            duplicateCount++
                        }
                    }
                }
            }.await()

            val duration = System.currentTimeMillis() - startTime
            android.util.Log.i(
                "CaregiverTelemetry",
                "[Reconciliation End] Completed in ${duration}ms | Healed: $healedCount | Duplicates Removed: $duplicateCount | Orphans Removed: $orphanCount | Self-referential Purged: $selfPurgeCount"
            )
        } catch (e: Exception) {
            android.util.Log.e("CaregiverTelemetry", "Error executing reconcileCaregiverState", e)
            throw e
        }
    }

    /**
     * Session-Throttling Guard Wrapper to launch reconciliation once-per-session per user
     */
    fun triggerReconciliation(userId: String, coroutineScope: kotlinx.coroutines.CoroutineScope) {
        synchronized(reconciledUsers) {
            if (reconciledUsers.contains(userId)) return
            reconciledUsers.add(userId)
        }
        
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                reconcileCaregiverState(userId)
            } catch (e: Exception) {
                android.util.Log.e("CaregiverTelemetry", "Reconciliation failed for user: $userId. Removing from cache to allow retry.", e)
                synchronized(reconciledUsers) {
                    reconciledUsers.remove(userId)
                }
            }
        }
    }

    companion object {
        // Thread-safe session tracker
        private val reconciledUsers = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    }
}



