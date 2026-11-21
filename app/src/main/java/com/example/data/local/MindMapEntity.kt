package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.SyncState

@Entity(tableName = "mind_maps")
data class MindMapEntity(
    @PrimaryKey val id: String,
    val title: String,
    val nodeCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val documentJson: String,
    val driveFileId: String? = null,
    val remoteModifiedAt: Long = 0L,
    val syncState: String = SyncState.LOCAL_ONLY.name,
    val isDeleted: Boolean = false
)
