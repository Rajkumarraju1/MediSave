package com.pralayakaveri.medisave.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.pralayakaveri.medisave.MainActivity
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
                    if (!activeMemberListeners.containsKey(targetUserId)) {
                        listenToFamilyMemberDoseLogs(targetUserId, conn.relation)
                    }
                }
            }
        }
    }

    private fun listenToFamilyMemberDoseLogs(memberUserId: String, relation: String) {
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
                            scope.launch {
                                val userProfile = FamilyConnectionRepository().getUserPublicProfile(memberUserId)
                                val realName = userProfile?.get("name")?.toString() ?: "Family Member"
                                val titleName = if (relation.isNotEmpty() && relation != "Family Member") "$realName ($relation)" else realName
                                
                                showMissedDoseAlert(memberUserId, titleName, realName, medicineName, time, logId)
                            }
                            markAsNotified(memberUserId, todayStr, logId, currentUserId)
                        }
                    }
                }
            }
        activeMemberListeners[memberUserId] = listener
    }

    private fun showMissedDoseAlert(memberUserId: String, titleName: String, realName: String, medicineName: String, time: String, logId: String) {
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
            
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("type", "MISSED")
                putExtra("patientId", memberUserId)
                putExtra("patientName", realName)
                putExtra("logId", logId)
                putExtra("screen", "member_detail")
                action = "OPEN_MEMBER_DETAIL"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                logId.hashCode() + 10,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "caregiver_alerts"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Family Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent alerts for family medication adherence"
                    enableVibration(true)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val formattedContent = buildString {
                append("$realName did not confirm their $medicineName dose.")
                append("\n\n🕒 Scheduled: $time")
                append("\n\n⚠️ Please review their medication schedule.")
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠️ Missed Dose: $titleName")
                .setContentText("$realName did not confirm their $medicineName dose.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(formattedContent))
                .setPriority(NotificationCompat.PRIORITY_HIGH) 
                .setGroup(GROUP_CAREGIVER_ALERTS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_info_details, "📋 View Schedule", pendingIntent)

            // logId.hashCode() ensures deterministic ID for OS-level deduplication
            notificationManager.notify(logId.hashCode(), builder.build())
            
            // Post/update the summary notification
            updateCaregiverSummaryNotification(notificationManager)
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
    
    fun updateCaregiverSummaryNotification(notificationManager: NotificationManager) {
        val activeCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications.count { 
                it.notification.group == GROUP_CAREGIVER_ALERTS && it.id != SUMMARY_ID_CAREGIVER 
            }
        } else { 1 }

        if (activeCount == 0) {
            notificationManager.cancel(SUMMARY_ID_CAREGIVER)
            return
        }

        val text = if (activeCount == 1) {
            "1 family medication dose was not confirmed."
        } else {
            "$activeCount family medication doses were not confirmed."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("screen", "connections")
            action = "OPEN_CONNECTIONS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_ID_CAREGIVER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = NotificationCompat.Builder(context, "caregiver_alerts")
            .setContentTitle("Missed Doses: Family Alerts")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setGroup(GROUP_CAREGIVER_ALERTS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(SUMMARY_ID_CAREGIVER, summary)
    }

    fun stopListening() {
        connectionListener?.cancel()
        activeMemberListeners.values.forEach { it.remove() }
        activeMemberListeners.clear()
    }

    companion object {
        const val GROUP_CAREGIVER_ALERTS = "com.pralayakaveri.medisave.CAREGIVER_ALERTS"
        const val SUMMARY_ID_CAREGIVER = 2002
    }
}
