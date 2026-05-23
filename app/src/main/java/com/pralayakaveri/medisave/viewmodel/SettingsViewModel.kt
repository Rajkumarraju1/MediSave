package com.pralayakaveri.medisave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.PreferenceManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class DeletionStage {
    IDLE, REAUTH, PURGING, AUTH_DELETE, COMPLETED
}

enum class SettingsError {
    AUTH_REQUIRED,
    NETWORK_FAILURE,
    PERMISSION_DENIED,
    UNKNOWN
}

data class SettingsUiState(
    val pushNotificationsEnabled: Boolean = true,
    val snoozeDuration: Int = 10,
    val missedDoseAlertEnabled: Boolean = true,
    val familyAlertsEnabled: Boolean = true,
    val refillRemindersEnabled: Boolean = true,
    val appLanguage: String = "English",
    val appTheme: String = "System",
    val isLoading: Boolean = false,
    val error: String? = null,
    val classifiedError: SettingsError? = null,
    val isAccountDeleted: Boolean = false,
    val deletionStage: DeletionStage = DeletionStage.IDLE,
    val deletionProgress: String = "",
    val isAlarmPrecisionDegraded: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefManager = PreferenceManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val authRepo = com.pralayakaveri.medisave.data.AuthRepository(database.userDao())
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = combine(
        prefManager.pushNotificationsEnabled.catch { 
            android.util.Log.e("SettingsVM", "pushNotificationsEnabled preference flow exception caught", it)
            emit(true) 
        },
        prefManager.snoozeDuration.catch { 
            android.util.Log.e("SettingsVM", "snoozeDuration preference flow exception caught", it)
            emit(10) 
        },
        prefManager.missedDoseAlertEnabled.catch { 
            android.util.Log.e("SettingsVM", "missedDoseAlertEnabled preference flow exception caught", it)
            emit(true) 
        },
        prefManager.familyAlertsEnabled.catch { 
            android.util.Log.e("SettingsVM", "familyAlertsEnabled preference flow exception caught", it)
            emit(true) 
        },
        prefManager.refillRemindersEnabled.catch { 
            android.util.Log.e("SettingsVM", "refillRemindersEnabled preference flow exception caught", it)
            emit(true) 
        },
        prefManager.appLanguage.catch { 
            android.util.Log.e("SettingsVM", "appLanguage preference flow exception caught", it)
            emit("English") 
        },
        prefManager.appTheme.catch { 
            android.util.Log.e("SettingsVM", "appTheme preference flow exception caught", it)
            emit("System") 
        },
        prefManager.lastExactAlarmPermissionState.catch { 
            android.util.Log.e("SettingsVM", "lastExactAlarmPermissionState preference flow exception caught", it)
            emit(true) 
        },
        _uiState
    ) { args: Array<Any?> ->
        try {
            val p0 = args[0] as Boolean
            val p1 = args[1] as Int
            val p2 = args[2] as Boolean
            val p3 = args[3] as Boolean
            val p4 = args[4] as Boolean
            val p5 = args[5] as String
            val p6 = args[6] as String
            val p7 = args[7] as Boolean
            val state = args[8] as SettingsUiState
            
            state.copy(
                pushNotificationsEnabled = p0,
                snoozeDuration = p1,
                missedDoseAlertEnabled = p2,
                familyAlertsEnabled = p3,
                refillRemindersEnabled = p4,
                appLanguage = p5,
                appTheme = p6,
                isAlarmPrecisionDegraded = !p7
            )
        } catch (e: Exception) {
            android.util.Log.e("SettingsVM", "Unexpected exception inside SettingsUiState combine mapping", e)
            SettingsUiState(error = "Failed to combine user preference flows", classifiedError = SettingsError.UNKNOWN)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private fun classifyException(e: Throwable): SettingsError {
        if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException ||
            e is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            return SettingsError.AUTH_REQUIRED
        }
        
        if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
            return when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                    SettingsError.PERMISSION_DENIED
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                    SettingsError.NETWORK_FAILURE
                else -> SettingsError.UNKNOWN
            }
        }
        
        if (e is java.io.IOException || 
            e is java.net.ConnectException || 
            e is java.net.UnknownHostException || 
            e is java.net.SocketTimeoutException) {
            return SettingsError.NETWORK_FAILURE
        }
        
        val msg = e.message ?: ""
        return if (msg.contains("recent-login") || msg.contains("sign-in") || msg.contains("authenticated")) {
            SettingsError.AUTH_REQUIRED
        } else if (msg.contains("permission") || msg.contains("denied")) {
            SettingsError.PERMISSION_DENIED
        } else if (msg.contains("network") || msg.contains("connection") || msg.contains("unavailable")) {
            SettingsError.NETWORK_FAILURE
        } else {
            SettingsError.UNKNOWN
        }
    }

    private fun getSanitizedErrorMessage(errorType: SettingsError): String {
        return when (errorType) {
            SettingsError.AUTH_REQUIRED -> "Your session has expired. Please re-authenticate."
            SettingsError.NETWORK_FAILURE -> "Network connection is unavailable. Please check your internet settings."
            SettingsError.PERMISSION_DENIED -> "Access denied. You do not have permission to perform this action."
            SettingsError.UNKNOWN -> "An unexpected settings synchronization error occurred. Please try again later."
        }
    }

    init {
        checkDeletionLock()
    }

    private fun checkDeletionLock() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            android.util.Log.d("SettingsVM", "checkDeletionLock skipped: current authenticated user is null")
            return
        }
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Querying Firestore deletion lock status for user: $userId")
                val doc = firestore.collection("users").document(userId).get().await()
                if (doc.getBoolean("deletionInProgress") == true) {
                    android.util.Log.w("SettingsVM", "Deletion lock detected! Auto-resuming interrupted deletion flow for $userId")
                    _uiState.update {
                        it.copy(
                            deletionStage = DeletionStage.PURGING,
                            deletionProgress = "Resuming interrupted deletion..."
                        )
                    }
                    // Auto-resume purge if lock is found
                    startDeletionFlow(null, true)
                } else {
                    android.util.Log.d("SettingsVM", "No active deletion lock discovered for user: $userId")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Exception checking deletion lock in Firestore", e)
                // If doc doesn't exist, we might be stuck at AUTH_DELETE stage
                if (auth.currentUser != null) {
                    android.util.Log.w("SettingsVM", "User exists but profile is missing, likely stuck at AUTH_DELETE stage")
                    _uiState.update { it.copy(deletionStage = DeletionStage.AUTH_DELETE) }
                }
            }
        }
    }

    fun togglePushNotifications(enabled: Boolean) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Toggling push notifications locally: $enabled")
                prefManager.togglePushNotifications(enabled)
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    android.util.Log.d("SettingsVM", "Syncing push notifications preference to Firestore for user: $userId")
                    firestore.collection("users").document(userId)
                        .update("pushNotificationsEnabled", enabled).await()
                    android.util.Log.i("SettingsVM", "Successfully synced push notifications preference to Firestore")
                } else {
                    android.util.Log.w("SettingsVM", "Skipped Firestore sync for push settings: User is not authenticated")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to sync push settings to Firestore", e)
                val errorType = classifyException(e)
                _uiState.update { it.copy(error = getSanitizedErrorMessage(errorType), classifiedError = errorType) }
            }
        }
    }

    fun updateSnoozeDuration(minutes: Int) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Updating snooze duration: $minutes minutes")
                prefManager.updateSnoozeDuration(minutes)
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to update snooze duration locally", e)
            }
        }
    }

    fun toggleMissedDoseAlert(enabled: Boolean) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Toggling missed dose alert: $enabled")
                prefManager.toggleMissedDoseAlert(enabled)
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to toggle missed dose alert locally", e)
            }
        }
    }

    fun toggleFamilyAlerts(enabled: Boolean) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Toggling family alerts locally: $enabled")
                prefManager.toggleFamilyAlerts(enabled)
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    android.util.Log.d("SettingsVM", "Syncing family alerts preference to Firestore for user: $userId")
                    firestore.collection("users").document(userId)
                        .update("familyAlertsEnabled", enabled).await()
                    android.util.Log.i("SettingsVM", "Successfully synced family alerts preference to Firestore")
                } else {
                    android.util.Log.w("SettingsVM", "Skipped Firestore sync for family alerts: User is not authenticated")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to sync family alerts to Firestore", e)
                val errorType = classifyException(e)
                _uiState.update { it.copy(error = getSanitizedErrorMessage(errorType), classifiedError = errorType) }
            }
        }
    }

    fun toggleRefillReminders(enabled: Boolean) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Toggling refill reminders: $enabled")
                prefManager.toggleRefillReminders(enabled)
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to toggle refill reminders locally", e)
            }
        }
    }

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Updating language preference: $lang")
                prefManager.updateLanguage(lang)
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to update language locally", e)
            }
        }
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsVM", "Updating theme preference: $theme")
                prefManager.updateTheme(theme)
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to update theme locally", e)
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                android.util.Log.i("SettingsVM", "User initiating logout flow")
                auth.signOut()
                prefManager.saveSession(false, null)
                android.util.Log.i("SettingsVM", "Logout flow completed successfully")
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Exception during logout flow", e)
                onComplete()
            }
        }
    }

    fun reauthenticateWithGoogle(idToken: String) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    android.util.Log.e("SettingsVM", "reauthenticateWithGoogle blocked: currentUser is null")
                    _uiState.update { it.copy(isLoading = false, error = "Authentication session expired", classifiedError = SettingsError.AUTH_REQUIRED) }
                    return@launch
                }
                
                android.util.Log.i("SettingsVM", "Starting Google re-authentication flow for user: ${user.uid}")
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        deletionStage = DeletionStage.REAUTH,
                        deletionProgress = "Verifying Google account..."
                    )
                }
                val result = authRepo.reauthenticateWithGoogle(idToken)
                if (result.isSuccess) {
                    android.util.Log.i("SettingsVM", "Google re-authentication successful. Proceeding to purge.")
                    startDeletionFlow(null, true)
                } else {
                    val ex = result.exceptionOrNull()
                    android.util.Log.e("SettingsVM", "Google re-authentication failed", ex)
                    val errorType = ex?.let { classifyException(it) } ?: SettingsError.UNKNOWN
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = getSanitizedErrorMessage(errorType),
                            classifiedError = errorType,
                            deletionStage = DeletionStage.IDLE
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Exception during Google re-authentication flow", e)
                val errorType = classifyException(e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = getSanitizedErrorMessage(errorType),
                        classifiedError = errorType,
                        deletionStage = DeletionStage.IDLE
                    )
                }
            }
        }
    }

    fun startDeletionFlow(password: String?, isResume: Boolean = false) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    android.util.Log.e("SettingsVM", "startDeletionFlow blocked: User is not authenticated")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "User session expired or not authenticated",
                            classifiedError = SettingsError.AUTH_REQUIRED,
                            deletionStage = DeletionStage.IDLE
                        )
                    }
                    return@launch
                }

                android.util.Log.i("SettingsVM", "Starting account deletion flow. User: ${user.uid} | isResume: $isResume")

                // 1. RE-AUTH (if not resuming)
                if (!isResume && password != null) {
                    android.util.Log.d("SettingsVM", "Step 1: Re-authenticating user with password")
                    _uiState.update {
                        it.copy(
                            isLoading = true,
                            deletionStage = DeletionStage.REAUTH,
                            deletionProgress = "Verifying identity..."
                        )
                    }
                    val reauthResult = authRepo.reauthenticate(password)
                    if (reauthResult.isFailure) {
                        val ex = reauthResult.exceptionOrNull()
                        android.util.Log.e("SettingsVM", "Re-authentication failed", ex)
                        val errorType = ex?.let { classifyException(it) } ?: SettingsError.UNKNOWN
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Invalid password. Please try again.",
                                classifiedError = errorType,
                                deletionStage = DeletionStage.IDLE
                            )
                        }
                        return@launch
                    }
                    android.util.Log.i("SettingsVM", "Step 1 complete: Password verification succeeded")
                }

                // 2. PURGE FIRESTORE
                android.util.Log.d("SettingsVM", "Step 2: Purging Firestore user data")
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        deletionStage = DeletionStage.PURGING,
                        deletionProgress = "Purging health data and connections..."
                    )
                }
                try {
                    authRepo.deleteUserFirestoreData(user.uid)
                    android.util.Log.i("SettingsVM", "Step 2 complete: Firestore data purged successfully")
                } catch (pe: Exception) {
                    android.util.Log.e("SettingsVM", "Firestore data purge encountered an error, but proceeding with account deletion", pe)
                }

                // 3. DELETE AUTH ACCOUNT
                android.util.Log.d("SettingsVM", "Step 3: Removing user account from Firebase Auth")
                _uiState.update {
                    it.copy(
                        deletionStage = DeletionStage.AUTH_DELETE,
                        deletionProgress = "Removing login account..."
                    )
                }
                user.delete().await()
                android.util.Log.i("SettingsVM", "Step 3 complete: Firebase Auth account deleted successfully")

                // 4. CLEANUP LOCAL
                android.util.Log.d("SettingsVM", "Step 4: Purging all local Room databases and shared preferences")
                try {
                    database.clearAllTables()
                    prefManager.clearAllData()
                    android.util.Log.i("SettingsVM", "Step 4 complete: Local cleanups successfully completed")
                } catch (le: Exception) {
                    android.util.Log.e("SettingsVM", "Error clearing local databases", le)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        deletionStage = DeletionStage.COMPLETED,
                        isAccountDeleted = true
                    )
                }
                android.util.Log.i("SettingsVM", "Full account deletion flow successfully completed")
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Exception in startDeletionFlow", e)
                val errorType = classifyException(e)
                val errorMsg = if (e.message?.contains("recent-login") == true) {
                    "Security timeout. Please re-enter your password to continue."
                } else {
                    getSanitizedErrorMessage(errorType)
                }
                _uiState.update { it.copy(isLoading = false, error = errorMsg, classifiedError = errorType) }

                // If we failed at Auth Delete stage, keep the UI in AUTH_DELETE mode
                if (_uiState.value.deletionStage == DeletionStage.AUTH_DELETE) {
                    android.util.Log.w("SettingsVM", "Failed at AUTH_DELETE stage. Leaving UI in retry state.")
                }
            }
        }
    }

    fun getSignInProviders(): List<String> {
        val providers = auth.currentUser?.providerData?.map { it.providerId } ?: emptyList()
        android.util.Log.d("SettingsVM", "Retrieved sign-in providers: $providers")
        return providers
    }

    fun retryAuthDelete() {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                if (user == null) {
                    android.util.Log.e("SettingsVM", "retryAuthDelete blocked: currentUser is null")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Session expired. Please re-authenticate.",
                            classifiedError = SettingsError.AUTH_REQUIRED,
                            deletionStage = DeletionStage.IDLE
                        )
                    }
                    return@launch
                }
                
                android.util.Log.i("SettingsVM", "Retrying Firebase Auth account deletion for user: ${user.uid}")
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        deletionStage = DeletionStage.AUTH_DELETE,
                        deletionProgress = "Retrying account removal..."
                    )
                }
                user.delete().await()
                android.util.Log.i("SettingsVM", "Retry complete: Firebase Auth account deleted successfully")

                android.util.Log.d("SettingsVM", "Purging local data caches")
                try {
                    database.clearAllTables()
                    prefManager.clearAllData()
                    android.util.Log.i("SettingsVM", "Local data caches successfully cleared")
                } catch (le: Exception) {
                    android.util.Log.e("SettingsVM", "Error clearing local databases during retry", le)
                }
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        deletionStage = DeletionStage.COMPLETED,
                        isAccountDeleted = true
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Exception during retryAuthDelete", e)
                val errorType = classifyException(e)
                _uiState.update { it.copy(isLoading = false, error = getSanitizedErrorMessage(errorType), classifiedError = errorType) }
            }
        }
    }

    fun refreshExactAlarmPermission() {
        viewModelScope.launch {
            try {
                val alarmManager = getApplication<Application>().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                val currentPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
                val cachedPermission = prefManager.lastExactAlarmPermissionState.first()
                if (currentPermission != cachedPermission) {
                    prefManager.saveExactAlarmPermissionState(currentPermission)
                    android.util.Log.i("SettingsVM", "Exact alarm permission state changed from cached $cachedPermission to $currentPermission. Persisting state.")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsVM", "Failed to refresh exact alarm permission state", e)
            }
        }
    }
}
