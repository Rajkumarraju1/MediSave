package com.pralayakaveri.medisave

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color
import com.google.android.libraries.places.api.Places
import com.pralayakaveri.medisave.ui.MainScreen
import com.pralayakaveri.medisave.ui.theme.MediSaveTheme

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pralayakaveri.medisave.data.AuthRepository
import com.pralayakaveri.medisave.data.MedicineRepository
import com.pralayakaveri.medisave.reminder.AlarmReceiver
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull

class MainActivity : AppCompatActivity() {

    private val medicineRepository by lazy { MedicineRepository(applicationContext) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted!
        }
    }


    private val deepLinkRequestId = mutableStateOf<String?>(null)
    private val deepLinkSenderId = mutableStateOf<String?>(null)
    private val deepLinkPatientId = mutableStateOf<String?>(null)
    private val deepLinkPatientName = mutableStateOf<String?>(null)
    private val deepLinkMedicineId = mutableStateOf<String?>(null)
    private val deepLinkReceiverId = mutableStateOf<String?>(null)
    private val deepLinkRelation = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        checkPermissions()
        com.pralayakaveri.medisave.work.WorkScheduler.scheduleDailyReset(applicationContext)
        com.pralayakaveri.medisave.work.WorkScheduler.scheduleRefillReminder(applicationContext)
        
        // Initial handle
        handleIntent(intent)

        // Trigger Sync-on-Active (with 30-second interval guard)
        lifecycleScope.launch {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                val prefs = com.pralayakaveri.medisave.data.PreferenceManager(applicationContext)
                val lastSyncTime = prefs.lastStartupSyncTime.firstOrNull() ?: 0L
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSyncTime > 30_000L) {
                    android.util.Log.i("MainActivity", "[Startup Sync] Throttled startup sync triggered. Time since last sync: ${(currentTime - lastSyncTime) / 1000}s. Scheduling SyncWorker.")
                    prefs.saveLastStartupSyncTime(currentTime)
                    medicineRepository.syncPendingResets(userId)
                    
                    // Trigger SyncWorker to resolve pending local changes
                    com.pralayakaveri.medisave.work.WorkScheduler.scheduleSyncWorker(applicationContext)
                } else {
                    android.util.Log.d("MainActivity", "[Startup Sync] Throttled startup sync skipped. Last sync was ${currentTime - lastSyncTime}ms ago (minimum 30s guard).")
                }
            }
        }

        // Initialize Google Places SDK
        try {
            if (!Places.isInitialized()) {
                val apiKey = BuildConfig.MAPS_API_KEY
                if (apiKey.isNotEmpty()) {
                    Places.initialize(applicationContext, apiKey)
                    android.util.Log.d("MainActivity", "Places SDK initialized successfully")
                } else {
                    android.util.Log.e("MainActivity", "Places API Key is empty - check secrets.properties")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize Places SDK: ${e.message}")
        }

        enableEdgeToEdge()
        
        // Final Status Bar & Edge-to-Edge refinements
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val targetMedicineId = intent.getStringExtra("TARGET_MEDICINE_ID")

        setContent {
            val prefManager = remember { com.pralayakaveri.medisave.data.PreferenceManager(applicationContext) }
            val themePreference by prefManager.appTheme.collectAsState(initial = null)

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val currentTheme = themePreference ?: "Light"
            val isDark = currentTheme == "Dark"

            if (themePreference != null) {
                LaunchedEffect(isDark) {
                    val mode = if (isDark) {
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    }
                    if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode) {
                        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
                    }
                }
            }

            val targetMedicineId = intent.getStringExtra("TARGET_MEDICINE_ID")

            MediSaveTheme(themePreference = currentTheme) {
                MainScreen(
                    targetMedicineId = targetMedicineId,
                    deepLinkRequestId = deepLinkRequestId.value,
                    deepLinkSenderId = deepLinkSenderId.value,
                    deepLinkPatientId = deepLinkPatientId.value,
                    deepLinkPatientName = deepLinkPatientName.value,
                    deepLinkMedicineId = deepLinkMedicineId.value,
                    deepLinkReceiverId = deepLinkReceiverId.value,
                    deepLinkRelation = deepLinkRelation.value,
                    onDeepLinkConsumed = {
                        deepLinkRequestId.value = null
                        deepLinkSenderId.value = null
                        deepLinkPatientId.value = null
                        deepLinkPatientName.value = null
                        deepLinkMedicineId.value = null
                        deepLinkReceiverId.value = null
                        deepLinkRelation.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val requestId = intent?.getStringExtra("requestId")
        val senderId = intent?.getStringExtra("senderId")
        if (requestId != null && senderId != null) {
            deepLinkRequestId.value = requestId
            deepLinkSenderId.value = senderId
        }

        val patientId = intent?.getStringExtra("patientId")
        val patientName = intent?.getStringExtra("patientName")
        if (patientId != null && patientName != null) {
            deepLinkPatientId.value = patientId
            deepLinkPatientName.value = patientName
        }

        val logId = intent?.getStringExtra("logId")
        if (logId != null) {
            val parts = logId.split("_")
            if (parts.size >= 4) {
                deepLinkMedicineId.value = parts[1]
            }
        }

        val receiverId = intent?.getStringExtra("receiverId")
        val relation = intent?.getStringExtra("relation")
        if (receiverId != null && relation != null) {
            deepLinkReceiverId.value = receiverId
            deepLinkRelation.value = relation
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 1. Medicine Channel
            val name = "Medicine Reminders"
            val channel = NotificationChannel(AlarmReceiver.CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for your scheduled medications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
            
            // 2. Connection Requests Channel
            val connName = "Connection Requests"
            val connChannel = NotificationChannel("connection_requests", connName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for new family connection requests"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(connChannel)
            
            // 3. Refill Reminders Channel
            val refillName = "Refill Reminders"
            val refillChannel = NotificationChannel("refill_reminders", refillName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when medicine stock is running low"
            }
            notificationManager.createNotificationChannel(refillChannel)
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val postNotificationsPermission = "android.permission.POST_NOTIFICATIONS"
            if (ContextCompat.checkSelfPermission(
                    this,
                    postNotificationsPermission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(postNotificationsPermission)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Continuous audit of exact alarm permissions
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val prefManager = com.pralayakaveri.medisave.data.PreferenceManager(applicationContext)
        val reminderManager = com.pralayakaveri.medisave.reminder.ReminderManager(applicationContext)
        
        lifecycleScope.launch {
            val currentPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            
            val cachedPermission = prefManager.lastExactAlarmPermissionState.firstOrNull() ?: true
            
            if (currentPermission != cachedPermission) {
                prefManager.saveExactAlarmPermissionState(currentPermission)
                prefManager.saveDegradedBannerDismissed(false)
                
                if (currentPermission) {
                    // Newly granted: restore timing precision for all alarms
                    val db = com.pralayakaveri.medisave.data.AppDatabase.getDatabase(applicationContext)
                    var userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    if (userId.isNullOrEmpty()) {
                        userId = prefManager.sessionUserId.firstOrNull()
                    }
                    if (userId.isNullOrEmpty()) {
                        userId = db.userDao().getPrimaryUser()?.userId
                    }
                    if (userId.isNullOrEmpty()) {
                        userId = "primary"
                    }
                    reminderManager.rescheduleAllAlarms(userId)
                    android.util.Log.d("MainActivity", "Exact alarm permission re-granted. Rescheduled all alarms for user: $userId")
                } else {
                    android.util.Log.w("MainActivity", "Exact alarm permission revoked. App transitioned to degraded-timing mode.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}