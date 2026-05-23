package com.pralayakaveri.medisave.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        val LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val SESSION_USER_ID = stringPreferencesKey("session_user_id")
        val MIGRATION_DONE = booleanPreferencesKey("migration_done")
        val LAST_ACTIVE_TIMESTAMP = longPreferencesKey("last_active_timestamp")
        
        // High-Fidelity Settings
        val PUSH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("push_notifications_enabled")
        val SNOOZE_DURATION = intPreferencesKey("snooze_duration")
        val MISSED_DOSE_ALERT_ENABLED = booleanPreferencesKey("missed_dose_alert_enabled")
        val FAMILY_ALERTS_ENABLED = booleanPreferencesKey("family_alerts_enabled")
        val REFILL_REMINDERS_ENABLED = booleanPreferencesKey("refill_reminders_enabled")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val APP_THEME = stringPreferencesKey("app_theme")
        val LAST_LOGIN_TIME = longPreferencesKey("last_login_time")
        val LAST_EXACT_ALARM_PERMISSION_STATE = booleanPreferencesKey("last_exact_alarm_permission_state")
        val DEGRADED_BANNER_DISMISSED = booleanPreferencesKey("degraded_banner_dismissed")
        val LAST_STARTUP_SYNC_TIME = longPreferencesKey("last_startup_sync_time")
    }

    val activeProfileId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[ACTIVE_PROFILE_ID] ?: "primary"
        }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_LOGGED_IN] ?: false
        }

    val sessionUserId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SESSION_USER_ID]
        }

    suspend fun saveSession(isLoggedIn: Boolean, userId: String?) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = isLoggedIn
            if (userId != null) {
                preferences[SESSION_USER_ID] = userId
            } else {
                preferences.remove(SESSION_USER_ID)
            }
        }
    }

    suspend fun saveActiveProfileId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_PROFILE_ID] = id
        }
    }

    val lastResetDate: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_RESET_DATE]
        }

    suspend fun saveLastResetDate(date: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_RESET_DATE] = date
        }
    }

    val migrationDone: Flow<Boolean> = context.dataStore.data
        .map { it[MIGRATION_DONE] ?: false }

    suspend fun markMigrationDone() {
        context.dataStore.edit { it[MIGRATION_DONE] = true }
    }

    val lastActiveTimestamp: Flow<Long> = context.dataStore.data
        .map { it[LAST_ACTIVE_TIMESTAMP] ?: 0L }

    suspend fun saveLastActiveTimestamp(timestamp: Long) {
        context.dataStore.edit { it[LAST_ACTIVE_TIMESTAMP] = timestamp }
    }

    // High-Fidelity Settings Flows
    val pushNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[PUSH_NOTIFICATIONS_ENABLED] ?: true }

    val snoozeDuration: Flow<Int> = context.dataStore.data
        .map { it[SNOOZE_DURATION] ?: 10 }

    val missedDoseAlertEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[MISSED_DOSE_ALERT_ENABLED] ?: true }

    val familyAlertsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[FAMILY_ALERTS_ENABLED] ?: true }

    val refillRemindersEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[REFILL_REMINDERS_ENABLED] ?: true }

    val appLanguage: Flow<String> = context.dataStore.data
        .map { it[APP_LANGUAGE] ?: "English" }

    val appTheme: Flow<String> = context.dataStore.data
        .map { it[APP_THEME] ?: "System" }

    val lastExactAlarmPermissionState: Flow<Boolean> = context.dataStore.data
        .map { it[LAST_EXACT_ALARM_PERMISSION_STATE] ?: true }

    val degradedBannerDismissed: Flow<Boolean> = context.dataStore.data
        .map { it[DEGRADED_BANNER_DISMISSED] ?: false }

    // Setters
    suspend fun togglePushNotifications(enabled: Boolean) {
        context.dataStore.edit { it[PUSH_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun updateSnoozeDuration(minutes: Int) {
        context.dataStore.edit { it[SNOOZE_DURATION] = minutes }
    }

    suspend fun toggleMissedDoseAlert(enabled: Boolean) {
        context.dataStore.edit { it[MISSED_DOSE_ALERT_ENABLED] = enabled }
    }

    suspend fun toggleFamilyAlerts(enabled: Boolean) {
        context.dataStore.edit { it[FAMILY_ALERTS_ENABLED] = enabled }
    }

    suspend fun toggleRefillReminders(enabled: Boolean) {
        context.dataStore.edit { it[REFILL_REMINDERS_ENABLED] = enabled }
    }

    suspend fun updateLanguage(lang: String) {
        context.dataStore.edit { it[APP_LANGUAGE] = lang }
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[APP_THEME] = theme }
    }

    val lastLoginTime: Flow<Long> = context.dataStore.data
        .map { it[LAST_LOGIN_TIME] ?: 0L }

    suspend fun saveLastLoginTime(time: Long) {
        context.dataStore.edit { it[LAST_LOGIN_TIME] = time }
    }

    suspend fun saveExactAlarmPermissionState(state: Boolean) {
        context.dataStore.edit { it[LAST_EXACT_ALARM_PERMISSION_STATE] = state }
    }

    suspend fun saveDegradedBannerDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[DEGRADED_BANNER_DISMISSED] = dismissed }
    }

    val lastStartupSyncTime: Flow<Long> = context.dataStore.data
        .map { it[LAST_STARTUP_SYNC_TIME] ?: 0L }

    suspend fun saveLastStartupSyncTime(timestamp: Long) {
        context.dataStore.edit { it[LAST_STARTUP_SYNC_TIME] = timestamp }
    }

    suspend fun clearAllData() {
        context.dataStore.edit { it.clear() }
    }

    fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}
