package com.pralayakaveri.medisave.data

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.pralayakaveri.medisave.reminder.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.first

class CaregiverNotificationManager(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var connectionListener: Job? = null
    private val activeMemberListeners = mutableMapOf<String, ListenerRegistration>()
    private val processedLogs = mutableSetOf<String>()

    fun startListening(userId: String) {
        connectionListener?.cancel()
        connectionListener = scope.launch {
            val connectionRepo = FamilyConnectionRepository()
            connectionRepo.observeAcceptedConnections(userId)
                .catch { e ->
                    android.util.Log.e("CaregiverNotificationManager", "Error observing accepted connections: ${e.message}", e)
                }
                .collect { connections ->
                val connectedMemberIds = connections.map { 
                    if (it.senderId == userId) it.receiverId else it.senderId 
                }.toSet()

                // cleanup listeners for users no longer connected
                val toRemove = activeMemberListeners.keys.minus(connectedMemberIds)
                toRemove.forEach { memberId ->
                    activeMemberListeners[memberId]?.remove()
                    activeMemberListeners.remove(memberId)
                }

                // Add listeners for new connections
                connections.forEach { conn ->
                    val targetUserId = if (conn.senderId == userId) conn.receiverId else conn.senderId
                    val targetName = "Family Member" // Simplified for base notification
                    
                    if (!activeMemberListeners.containsKey(targetUserId)) {
                        listenToFamilyMemberDoseLogs(targetUserId, targetName)
                    }
                }
            }
        }
    }

    private fun listenToFamilyMemberDoseLogs(memberUserId: String, memberName: String) {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        
        val listener = db.collection("doseLogs")
            .document(memberUserId)
            .collection("logs")
            .whereEqualTo("date", todayStr)
            .whereEqualTo("status", "MISSED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CaregiverNotificationManager", "Firestore error listening to dose logs for member $memberUserId: ${error.message}", error)
                    return@addSnapshotListener
                }
                
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val medicineName = doc.getString("medicineName") ?: "Medicine"
                        val time = doc.getString("time") ?: ""
                        val logId = doc.id
                        
                        @Suppress("UNCHECKED_CAST")
                        val notifiedTo = doc.get("notifiedTo") as? List<String> ?: emptyList()
                        
                        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@forEach
                        
                        // DE-DUPLICATION CHECK using currentUserId in notifiedTo
                        if (!notifiedTo.contains(currentUserId) && !processedLogs.contains(logId)) {
                            processedLogs.add(logId)
                            showMissedDoseAlert(memberName, medicineName, time, logId)
                            markAsNotified(memberUserId, todayStr, logId, currentUserId)
                        }
                    }
                }
            }
        activeMemberListeners[memberUserId] = listener
    }

    private fun showMissedDoseAlert(memberName: String, medicineName: String, time: String, logId: String) {
        scope.launch {
            val prefManager = PreferenceManager(context)
            val pushEnabled = prefManager.pushNotificationsEnabled.catch { emit(true) }.first()
            val familyAlertsEnabled = prefManager.familyAlertsEnabled.catch { emit(true) }.first()
            
            if (!pushEnabled || !familyAlertsEnabled) {
                android.util.Log.i("CaregiverManager", "showMissedDoseAlert suppressed. Push: $pushEnabled, FamilyAlerts: $familyAlertsEnabled")
                return@launch
            }

            // PERMISSION CHECK
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return@launch // Skip if no permission
                }
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val builder = NotificationCompat.Builder(context, AlarmReceiver.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("$memberName missed a dose")
                .setContentText("$memberName missed their $medicineName at $time")
                .setPriority(NotificationCompat.PRIORITY_HIGH) 
                .setAutoCancel(true)

            notificationManager.notify(logId.hashCode(), builder.build())
        }
    }

    private fun markAsNotified(memberId: String, date: String, logId: String, currentUserId: String) {
        scope.launch {
            try {
                db.collection("doseLogs")
                    .document(memberId)
                    .collection("logs")
                    .document(logId)
                    .update("notifiedTo", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId))
                    .await()
            } catch (e: Exception) {
                android.util.Log.e("CaregiverManager", "Failed to mark as notified", e)
            }
        }
    }
    
    fun stopListening() {
        connectionListener?.cancel()
        activeMemberListeners.values.forEach { it.remove() }
        activeMemberListeners.clear()
    }
}
