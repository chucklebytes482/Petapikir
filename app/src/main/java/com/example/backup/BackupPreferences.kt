package com.example.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_prefs")

data class BackupSettings(
    val lastBackupAt: Long = 0L,
    val backedUpCount: Int = 0
) {
    val displayPath: String
        get() = SyncLocation.DISPLAY_PATH
}

class BackupPreferences(private val context: Context) {

    private object Keys {
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val BACKED_UP_COUNT = intPreferencesKey("backed_up_count")
    }

    val settingsFlow: Flow<BackupSettings> = context.backupDataStore.data.map { prefs ->
        BackupSettings(
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT] ?: 0L,
            backedUpCount = prefs[Keys.BACKED_UP_COUNT] ?: 0
        )
    }

    suspend fun setLastBackupInfo(timestamp: Long, count: Int) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_AT] = timestamp
            prefs[Keys.BACKED_UP_COUNT] = count
        }
    }
}
