package com.example.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_prefs")

data class BackupSettings(
    val relativeSubPath: String = "",
    val treeUriString: String? = null,
    val lastBackupAt: Long = 0L,
    val backedUpCount: Int = 0
) {
    val displayPath: String
        get() = BackupPathValidator.toDisplayPath(relativeSubPath)

    val isDefaultPath: Boolean
        get() = relativeSubPath.isBlank() && treeUriString.isNullOrBlank()
}

class BackupPreferences(private val context: Context) {

    private object Keys {
        val RELATIVE_SUB_PATH = stringPreferencesKey("relative_sub_path")
        val TREE_URI = stringPreferencesKey("tree_uri")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val BACKED_UP_COUNT = intPreferencesKey("backed_up_count")
    }

    val settingsFlow: Flow<BackupSettings> = context.backupDataStore.data.map { prefs ->
        BackupSettings(
            relativeSubPath = prefs[Keys.RELATIVE_SUB_PATH].orEmpty(),
            treeUriString = prefs[Keys.TREE_URI],
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT] ?: 0L,
            backedUpCount = prefs[Keys.BACKED_UP_COUNT] ?: 0
        )
    }

    suspend fun setRelativeSubPath(normalizedSubPath: String) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.RELATIVE_SUB_PATH] = normalizedSubPath
            prefs.remove(Keys.TREE_URI)
        }
    }

    suspend fun setTreeUri(uriString: String, normalizedSubPath: String) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.TREE_URI] = uriString
            prefs[Keys.RELATIVE_SUB_PATH] = normalizedSubPath
        }
    }

    suspend fun resetToDefault() {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.RELATIVE_SUB_PATH] = ""
            prefs.remove(Keys.TREE_URI)
        }
    }

    suspend fun setLastBackupInfo(timestamp: Long, count: Int) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_AT] = timestamp
            prefs[Keys.BACKED_UP_COUNT] = count
        }
    }
}
