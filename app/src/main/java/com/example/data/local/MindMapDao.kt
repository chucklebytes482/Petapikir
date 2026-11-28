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

    /** Semua baris termasuk penanda hapus (isDeleted = 1), dipakai untuk sinkron. */
    @Query("SELECT * FROM mind_maps")
    suspend fun getAllMaps(): List<MindMapEntity>

    @Query("SELECT * FROM mind_maps WHERE id = :id AND isDeleted = 0")
    fun observeById(id: String): Flow<MindMapEntity?>

    @Query("SELECT * FROM mind_maps WHERE id = :id")
    suspend fun getById(id: String): MindMapEntity?

    @Query("SELECT COUNT(*) FROM mind_maps WHERE isDeleted = 0 AND syncState != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MindMapEntity)

    /** Hapus sebagai penanda, bukan menghapus baris (panduan 05 §3). */
    @Query("UPDATE mind_maps SET isDeleted = 1, updatedAt = :updatedAt, syncState = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)

    /** Tandai tersinkron hanya jika baris belum berubah sejak ditulis. */
    @Query(
        "UPDATE mind_maps SET syncState = 'SYNCED', driveFileId = :location, remoteModifiedAt = :syncedAt " +
            "WHERE id = :id AND updatedAt = :updatedAt"
    )
    suspend fun markSynced(id: String, updatedAt: Long, location: String, syncedAt: Long)
}
