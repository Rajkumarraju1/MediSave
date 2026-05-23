package com.pralayakaveri.medisave.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pralayakaveri.medisave.MainActivity
import com.pralayakaveri.medisave.R
import com.pralayakaveri.medisave.reminder.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class FCMService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val repo = AuthRepository(
                AppDatabase.getDatabase(this).userDao()
            )
            serviceScope.launch {
                repo.updateFcmTokens(userId, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"] ?: return

        serviceScope.launch {
            try {
                val prefManager = PreferenceManager(applicationContext)
                val pushEnabled = prefManager.pushNotificationsEnabled.first()
                if (!pushEnabled) {
                    android.util.Log.i("FCMService", "FCM message received but suppressed because Push Notifications are disabled in Settings")
                    return@launch
                }

                when (type) {
                    "CONNECTION_REQUEST" -> {
                        val requestId = data["requestId"] ?: ""
                        val senderId = data["senderId"] ?: ""
                        showConnectionRequestNotification(requestId, senderId)
                    }
                    "CAREGIVER_ALERT" -> {
                        val familyAlertsEnabled = prefManager.familyAlertsEnabled.first()
                        if (familyAlertsEnabled) {
                            showCaregiverAlertNotification(data)
                        } else {
                            android.util.Log.i("FCMService", "CAREGIVER_ALERT suppressed because Family Alerts are disabled in Settings")
                        }
                    }
                    "MISSED" -> {
                        val familyAlertsEnabled = prefManager.familyAlertsEnabled.first()
                        if (familyAlertsEnabled) {
                            showMissedDoseNotification(data)
                        } else {
                            android.util.Log.i("FCMService", "MISSED FCM alert suppressed because Family Alerts are disabled in Settings")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FCMService", "Error processing remote message in serviceScope", e)
            }
        }
    }

    private fun showMissedDoseNotification(data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "missed_doses"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Missed Doses",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for missed medication doses"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val patientId = data["patientId"] ?: ""
        val patientName = data["patientName"] ?: "Family Member"
        val logId = data["logId"] ?: ""
        val title = data["title"] ?: "Missed Dose: $patientName"
        val body = data["body"] ?: "A dose was missed."

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("type", "MISSED")
            putExtra("patientId", patientId)
            putExtra("patientName", patientName)
            putExtra("logId", logId)
            putExtra("screen", "member_detail")
            action = "OPEN_MEMBER_DETAIL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            logId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setGroup(GROUP_MISSED_DOSES)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(86400000) // 24 Hours
            .build()

        notificationManager.notify(logId.hashCode(), notification)
        showSummaryNotification(notificationManager, channelId)
    }

    private fun showCaregiverAlertNotification(data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        val patientId = data["patientId"] ?: ""
        val patientName = data["patientName"] ?: "Family Member"
        val medicineName = data["medicineName"] ?: "Medicine"
        val logId = data["logId"] ?: ""
        val time = data["scheduledTime"] ?: ""
        
        val title = "Missed Dose: $patientName"
        val body = "$patientName missed their $medicineName at $time"

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("type", "MISSED")
            putExtra("patientId", patientId)
            putExtra("patientName", patientName)
            putExtra("logId", logId)
            putExtra("screen", "member_detail")
            action = "OPEN_MEMBER_DETAIL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            logId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setGroup(GROUP_MISSED_DOSES)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // logId.hashCode() ensures deterministic ID for OS-level deduplication
        notificationManager.notify(logId.hashCode(), notification)
        showSummaryNotification(notificationManager, channelId)
    }

    private fun showSummaryNotification(notificationManager: NotificationManager, channelId: String) {
        val summaryNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Missed Doses")
            .setContentText("Multiple family members missed their medication")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setGroup(GROUP_MISSED_DOSES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_ID, summaryNotification)
    }

    companion object {
        private const val GROUP_MISSED_DOSES = "com.pralayakaveri.medisave.MISSED_DOSES"
        private const val SUMMARY_ID = 1000
    }

    private fun showConnectionRequestNotification(requestId: String, senderId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "connection_requests"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Connection Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for new family connection requests"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("type", "CONNECTION_REQUEST")
            putExtra("requestId", requestId)
            putExtra("senderId", senderId)
            action = "OPEN_CONNECTION_REQUEST"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 
            requestId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Connection Request")
            .setContentText("Someone wants to connect with your MediSave account")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Deterministic ID prevents duplicate notifications for the same request
        notificationManager.notify(requestId.hashCode(), notification)
    }

}
