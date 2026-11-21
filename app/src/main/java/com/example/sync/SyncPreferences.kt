package com.example.sync

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

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

data class SyncSettings(
    val isDriveConnected: Boolean = false,
    val connectedEmail: String? = null,
    val autoSyncEnabled: Boolean = true,
    val lastSyncAt: Long = 0L
)

class SyncPreferences(private val context: Context) {

    private object Keys {
        val DRIVE_CONNECTED = booleanPreferencesKey("drive_connected")
        val CONNECTED_EMAIL = stringPreferencesKey("connected_email")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }

    val settingsFlow: Flow<SyncSettings> = context.syncDataStore.data.map { prefs ->
        SyncSettings(
            isDriveConnected = prefs[Keys.DRIVE_CONNECTED] ?: false,
            connectedEmail = prefs[Keys.CONNECTED_EMAIL],
            autoSyncEnabled = prefs[Keys.AUTO_SYNC_ENABLED] ?: true,
            lastSyncAt = prefs[Keys.LAST_SYNC_AT] ?: 0L
        )
    }

    suspend fun setDriveConnected(connected: Boolean, email: String?) {
        context.syncDataStore.edit { prefs ->
            prefs[Keys.DRIVE_CONNECTED] = connected
            if (email != null) {
                prefs[Keys.CONNECTED_EMAIL] = email
            } else if (!connected) {
                prefs.remove(Keys.CONNECTED_EMAIL)
            }
        }
    }

    suspend fun setConnectedEmail(email: String) {
        context.syncDataStore.edit { prefs ->
            prefs[Keys.CONNECTED_EMAIL] = email
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.syncDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setLastSyncAt(timestamp: Long) {
        context.syncDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_AT] = timestamp
        }
    }
}
