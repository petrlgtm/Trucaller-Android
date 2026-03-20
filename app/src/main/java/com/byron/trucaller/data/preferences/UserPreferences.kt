package com.byron.trucaller.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        private val AUTO_BACKUP_KEY = booleanPreferencesKey("auto_backup")
        private val LOGGED_IN_USER_ID = stringPreferencesKey("logged_in_user_id")
        private val ADMIN_ID = stringPreferencesKey("admin_id")
        private val ADMIN_NAME = stringPreferencesKey("admin_name")
        private val ADMIN_EMAIL = stringPreferencesKey("admin_email")
        private val STOLEN_REPORT_NOTIF = booleanPreferencesKey("stolen_report_notif")
        private val ALARM_NOTIF = booleanPreferencesKey("alarm_notif")
        private val DAILY_DIGEST = booleanPreferencesKey("daily_digest")
        private val WEEKLY_REPORT = booleanPreferencesKey("weekly_report")
        private val RECENT_LOOKUPS = stringPreferencesKey("recent_lookups")
        private val CONSENT_GIVEN = booleanPreferencesKey("consent_given")
        private val LAST_CONTACT_SYNC_TIMESTAMP = longPreferencesKey("last_contact_sync_timestamp")
        private val FCM_TOKEN_NEEDS_SYNC = booleanPreferencesKey("fcm_token_needs_sync")
        private val DEVICE_PROTECTION_PROMPT_SHOWN = booleanPreferencesKey("device_protection_prompt_shown")

        // ── Call Recording Settings ────────────────────────────────────
        private val RECORDING_CONSENT_ACCEPTED = booleanPreferencesKey("recording_consent_accepted")
        private val RECORDING_ENABLED = booleanPreferencesKey("recording_enabled")
        private val RECORDING_AUTO_RECORD = booleanPreferencesKey("recording_auto_record")
        private val RECORDING_PLAY_BEEP = booleanPreferencesKey("recording_play_beep")
        private val RECORDING_DIRECTION = stringPreferencesKey("recording_direction")
        private val RECORDING_CONSENT_MODE = stringPreferencesKey("recording_consent_mode")
        private val RECORDING_AUTO_DELETE = stringPreferencesKey("recording_auto_delete")
        private val RECORDING_STORAGE_LIMIT = stringPreferencesKey("recording_storage_limit")
    }

    val autoBackup: Flow<Boolean> = context.dataStore.data.map { it[AUTO_BACKUP_KEY] ?: true }
    val loggedInUserId: Flow<String?> = context.dataStore.data.map { it[LOGGED_IN_USER_ID] }
    val adminId: Flow<String?> = context.dataStore.data.map { it[ADMIN_ID] }
    val adminName: Flow<String> = context.dataStore.data.map { it[ADMIN_NAME] ?: "" }
    val adminEmail: Flow<String> = context.dataStore.data.map { it[ADMIN_EMAIL] ?: "" }
    val stolenReportNotif: Flow<Boolean> = context.dataStore.data.map { it[STOLEN_REPORT_NOTIF] ?: true }
    val alarmNotif: Flow<Boolean> = context.dataStore.data.map { it[ALARM_NOTIF] ?: true }
    val dailyDigest: Flow<Boolean> = context.dataStore.data.map { it[DAILY_DIGEST] ?: false }
    val weeklyReport: Flow<Boolean> = context.dataStore.data.map { it[WEEKLY_REPORT] ?: true }
    val consentGiven: Flow<Boolean> = context.dataStore.data.map { it[CONSENT_GIVEN] ?: false }
    val lastContactSyncTimestamp: Flow<Long> = context.dataStore.data.map { it[LAST_CONTACT_SYNC_TIMESTAMP] ?: 0L }
    val fcmTokenNeedsSync: Flow<Boolean> = context.dataStore.data.map { it[FCM_TOKEN_NEEDS_SYNC] ?: false }
    val deviceProtectionPromptShown: Flow<Boolean> = context.dataStore.data.map { it[DEVICE_PROTECTION_PROMPT_SHOWN] ?: false }
    // ── Call Recording Settings (flows) ───────────────────────────────
    val recordingConsentAccepted: Flow<Boolean> = context.dataStore.data.map { it[RECORDING_CONSENT_ACCEPTED] ?: false }
    val recordingEnabled: Flow<Boolean> = context.dataStore.data.map { it[RECORDING_ENABLED] ?: false }
    val recordingAutoRecord: Flow<Boolean> = context.dataStore.data.map { it[RECORDING_AUTO_RECORD] ?: false }
    val recordingPlayBeep: Flow<Boolean> = context.dataStore.data.map { it[RECORDING_PLAY_BEEP] ?: true }
    val recordingDirection: Flow<String> = context.dataStore.data.map { it[RECORDING_DIRECTION] ?: "ALL" }
    val recordingConsentMode: Flow<String> = context.dataStore.data.map { it[RECORDING_CONSENT_MODE] ?: "ONE_PARTY" }
    val recordingAutoDelete: Flow<String> = context.dataStore.data.map { it[RECORDING_AUTO_DELETE] ?: "NEVER" }
    val recordingStorageLimit: Flow<String> = context.dataStore.data.map { it[RECORDING_STORAGE_LIMIT] ?: "MB_500" }

    val recentLookups: Flow<List<String>> = context.dataStore.data.map {
        it[RECENT_LOOKUPS]?.split(",")?.filter { s -> s.isNotEmpty() } ?: emptyList()
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_BACKUP_KEY] = enabled }
    }

    suspend fun setLoggedInUserId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[LOGGED_IN_USER_ID] = id
            else it.remove(LOGGED_IN_USER_ID)
        }
    }

    suspend fun setAdminId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[ADMIN_ID] = id
            else it.remove(ADMIN_ID)
        }
    }

    suspend fun setAdminProfile(name: String, email: String) {
        context.dataStore.edit {
            it[ADMIN_NAME] = name
            it[ADMIN_EMAIL] = email
        }
    }

    suspend fun setStolenReportNotif(enabled: Boolean) {
        context.dataStore.edit { it[STOLEN_REPORT_NOTIF] = enabled }
    }

    suspend fun setAlarmNotif(enabled: Boolean) {
        context.dataStore.edit { it[ALARM_NOTIF] = enabled }
    }

    suspend fun setDailyDigest(enabled: Boolean) {
        context.dataStore.edit { it[DAILY_DIGEST] = enabled }
    }

    suspend fun setWeeklyReport(enabled: Boolean) {
        context.dataStore.edit { it[WEEKLY_REPORT] = enabled }
    }

    suspend fun setConsentGiven(given: Boolean) {
        context.dataStore.edit { it[CONSENT_GIVEN] = given }
    }

    suspend fun setLastContactSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[LAST_CONTACT_SYNC_TIMESTAMP] = timestamp }
    }

    suspend fun setFcmTokenNeedsSync(needsSync: Boolean) {
        context.dataStore.edit { it[FCM_TOKEN_NEEDS_SYNC] = needsSync }
    }

    suspend fun setDeviceProtectionPromptShown(shown: Boolean) {
        context.dataStore.edit { it[DEVICE_PROTECTION_PROMPT_SHOWN] = shown }
    }

    suspend fun addRecentLookup(entryId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_LOOKUPS]?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
            current.remove(entryId)
            current.add(0, entryId)
            while (current.size > 5) current.removeLast()
            prefs[RECENT_LOOKUPS] = current.joinToString(",")
        }
    }

    // ── Call Recording Settings (setters) ─────────────────────────────
    suspend fun setRecordingConsentAccepted(accepted: Boolean) {
        context.dataStore.edit { it[RECORDING_CONSENT_ACCEPTED] = accepted }
    }

    suspend fun setRecordingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[RECORDING_ENABLED] = enabled }
    }

    suspend fun setRecordingAutoRecord(auto: Boolean) {
        context.dataStore.edit { it[RECORDING_AUTO_RECORD] = auto }
    }

    suspend fun setRecordingPlayBeep(play: Boolean) {
        context.dataStore.edit { it[RECORDING_PLAY_BEEP] = play }
    }

    suspend fun setRecordingDirection(direction: String) {
        context.dataStore.edit { it[RECORDING_DIRECTION] = direction }
    }

    suspend fun setRecordingConsentMode(mode: String) {
        context.dataStore.edit { it[RECORDING_CONSENT_MODE] = mode }
    }

    suspend fun setRecordingAutoDelete(period: String) {
        context.dataStore.edit { it[RECORDING_AUTO_DELETE] = period }
    }

    suspend fun setRecordingStorageLimit(limit: String) {
        context.dataStore.edit { it[RECORDING_STORAGE_LIMIT] = limit }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(LOGGED_IN_USER_ID)
            it.remove(ADMIN_ID)
        }
    }
}
