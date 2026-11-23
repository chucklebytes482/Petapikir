package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.SyncState

/**
 * Room entity for storing mind maps locally.
 * Existing SQLite column names (`driveFileId`, `remoteModifiedAt`, `syncState`) are preserved
 * via `@ColumnInfo` so existing user installations upgrade cleanly without schema loss.
 */
@Entity(tableName = "mind_maps")
data class MindMapEntity(
    @PrimaryKey val id: String,
    val title: String,
    val nodeCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val documentJson: String,
    @ColumnInfo(name = "driveFileId")
    val backupLocation: String? = null,
    @ColumnInfo(name = "remoteModifiedAt")
    val lastBackedUpAt: Long = 0L,
    @ColumnInfo(name = "syncState")
    val backupState: String = SyncState.LOCAL_ONLY.name,
    val isDeleted: Boolean = false
)
