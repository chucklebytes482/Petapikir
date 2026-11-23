package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MindMapDao {

    @Query("SELECT * FROM mind_maps WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeActiveMaps(): Flow<List<MindMapEntity>>

    @Query("SELECT * FROM mind_maps WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getActiveMaps(): List<MindMapEntity>

    @Query("SELECT * FROM mind_maps WHERE id = :id AND isDeleted = 0")
    fun observeById(id: String): Flow<MindMapEntity?>

    @Query("SELECT * FROM mind_maps WHERE id = :id")
    suspend fun getById(id: String): MindMapEntity?

    @Query("SELECT COUNT(*) FROM mind_maps WHERE isDeleted = 0 AND syncState != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MindMapEntity)

    @Query("DELETE FROM mind_maps WHERE id = :id")
    suspend fun hardDeleteById(id: String)

    @Query("UPDATE mind_maps SET driveFileId = NULL, remoteModifiedAt = 0, syncState = 'LOCAL_ONLY'")
    suspend fun resetAllBackupLocations()
}
