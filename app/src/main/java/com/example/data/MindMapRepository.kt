package com.example.data

import com.example.backup.BackupPreferences
import com.example.backup.BackupSettings
import com.example.backup.SyncFileCodec
import com.example.backup.SyncFileStore
import com.example.backup.SyncLocation
import com.example.backup.SyncMerge
import com.example.backup.SyncRecord
import com.example.data.local.MindMapDao
import com.example.data.local.MindMapEntity
import com.example.model.MindMapDocument
import com.example.model.MindMapTreeOps
import com.example.model.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed class LocalBackupStatus {
    data object Ready : LocalBackupStatus()
    data object BackingUp : LocalBackupStatus()
    data class BackedUp(val timestamp: Long, val count: Int) : LocalBackupStatus()
    data class Error(val message: String) : LocalBackupStatus()
}

data class MindMapSummary(
    val id: String,
    val title: String,
    val nodeCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val backupState: SyncState,
    val backupLocation: String?,
    val rootTitle: String,
    val previewChildTitles: List<String>
)

/**
 * Penyimpanan utama di Room. Setiap perubahan disinkron ke satu berkas di
 * [SyncLocation.FILE_PATH] (panduan 05). Gabung memakai last-write-wins per id.
 */
class MindMapRepository(
    private val dao: MindMapDao,
    val codec: MindMapJsonCodec,
    private val backupPreferences: BackupPreferences,
    private val syncStore: SyncFileStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _backupStatus = MutableStateFlow<LocalBackupStatus>(LocalBackupStatus.Ready)
    val backupStatus: StateFlow<LocalBackupStatus> = _backupStatus.asStateFlow()

    val backupSettings: Flow<BackupSettings> = backupPreferences.settingsFlow
    val pendingCount: Flow<Int> = dao.observePendingCount()

    val summaries: Flow<List<MindMapSummary>> = dao.observeActiveMaps().map { entities ->
        entities.map { entity ->
            val doc = codec.decodeDocument(entity.documentJson)
            val rootNode = doc?.nodes?.firstOrNull { it.id == doc.rootNodeId }
            val childTitles = if (doc != null) {
                doc.nodes
                    .filter { it.parentId == doc.rootNodeId }
                    .sortedWith(compareBy({ it.orderIndex }, { it.id }))
                    .take(3)
                    .map { it.title }
            } else {
                emptyList()
            }
            MindMapSummary(
                id = entity.id,
                title = entity.title,
                nodeCount = entity.nodeCount,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                backupState = runCatching { SyncState.valueOf(entity.backupState) }.getOrDefault(SyncState.LOCAL_ONLY),
                backupLocation = entity.backupLocation,
                rootTitle = rootNode?.title ?: entity.title,
                previewChildTitles = childTitles
            )
        }
    }

    init {
        scope.launch {
            val settings = backupPreferences.settingsFlow.first()
            _backupStatus.value = if (settings.lastBackupAt > 0L) {
                LocalBackupStatus.BackedUp(timestamp = settings.lastBackupAt, count = settings.backedUpCount)
            } else {
                LocalBackupStatus.Ready
            }
            syncNow()
        }
    }

    /**
     * Sinkron penuh: baca berkas perangkat, gabung dengan lokal, terapkan ke lokal,
     * lalu tulis ulang berkas. Jika baca atau validasi gagal, tidak ada yang ditulis.
     */
    suspend fun syncNow(): Result<Unit> = syncMutex.withLock {
        _backupStatus.value = LocalBackupStatus.BackingUp
        val result = runCatching { performSync() }
        result.onSuccess { count ->
            val now = System.currentTimeMillis()
            backupPreferences.setLastBackupInfo(now, count)
            _backupStatus.value = LocalBackupStatus.BackedUp(now, count)
        }.onFailure { err ->
            _backupStatus.value = LocalBackupStatus.Error(
                err.localizedMessage ?: "Gagal menyimpan cadangan ke ${SyncLocation.DISPLAY_PATH}"
            )
        }
        result.map { }
    }

    /** Mengembalikan jumlah peta aktif yang tersimpan. */
    private suspend fun performSync(): Int {
        // 1. Baca berkas perangkat. Berkas rusak melempar exception di sini, sebelum ada tulis.
        val deviceRecords = syncStore.read()?.let { SyncFileCodec.decode(it) }.orEmpty()
        for (record in deviceRecords) {
            if (!record.deleted) {
                check(codec.decodeDocument(record.value, record.id) != null) {
                    "Data ${record.id} di berkas cadangan tidak valid. Berkas tidak ditimpa."
                }
            }
        }

        // 2. Gabung dengan data lokal, termasuk penanda hapus.
        val localRows = dao.getAllMaps()
        val localById = localRows.associateBy { it.id }
        val merged = SyncMerge.mergeLastWriteWins(
            local = localRows.map { it.toSyncRecord() },
            device = deviceRecords
        )

        // 3. Terapkan hasil gabung ke data lokal. Lewati baris yang berubah saat sinkron berjalan.
        for (record in merged) {
            val local = localById[record.id]
            if (local != null && local.updatedAt == record.updatedAtMillis && local.isDeleted == record.deleted) {
                continue
            }
            val current = dao.getById(record.id)
            if (current?.updatedAt != local?.updatedAt) continue

            if (record.deleted) {
                val tombstone = local?.copy(
                    isDeleted = true,
                    updatedAt = record.updatedAtMillis,
                    backupState = SyncState.PENDING_UPLOAD.name
                ) ?: MindMapEntity(
                    id = record.id,
                    title = "",
                    nodeCount = 0,
                    createdAt = record.updatedAtMillis,
                    updatedAt = record.updatedAtMillis,
                    documentJson = "",
                    isDeleted = true,
                    backupState = SyncState.PENDING_UPLOAD.name
                )
                dao.upsert(tombstone)
            } else {
                val doc = codec.decodeDocument(record.value, record.id) ?: continue
                dao.upsert(
                    entityFromDocument(
                        doc = doc,
                        backupLocation = local?.backupLocation,
                        lastBackedUpAt = local?.lastBackedUpAt ?: 0L
                    )
                )
            }
        }

        // 4. Tulis dari data lokal yang sudah berisi hasil gabung.
        val records = dao.getAllMaps().map { it.toSyncRecord() }
        if (records.isEmpty()) return 0
        syncStore.write(SyncFileCodec.encode(records))

        // 5. Tandai tersinkron.
        val now = System.currentTimeMillis()
        for (record in records) {
            dao.markSynced(record.id, record.updatedAtMillis, SyncLocation.FILE_PATH, now)
        }
        return records.count { !it.deleted }
    }

    private fun MindMapEntity.toSyncRecord(): SyncRecord = SyncRecord(
        id = id,
        value = if (isDeleted) "" else documentJson,
        updatedAtMillis = updatedAt,
        deleted = isDeleted
    )

    private fun entityFromDocument(
        doc: MindMapDocument,
        backupLocation: String?,
        lastBackedUpAt: Long
    ): MindMapEntity = MindMapEntity(
        id = doc.id,
        title = doc.title,
        nodeCount = doc.nodes.size,
        createdAt = doc.createdAt,
        updatedAt = doc.updatedAt,
        documentJson = codec.encodeDocument(doc),
        backupLocation = backupLocation,
        lastBackedUpAt = lastBackedUpAt,
        backupState = SyncState.PENDING_UPLOAD.name,
        isDeleted = false
    )

    fun observeDocument(id: String): Flow<MindMapDocument?> =
        dao.observeById(id).map { entity -> entity?.let { codec.decodeDocument(it.documentJson) } }

    suspend fun getDocument(id: String): MindMapDocument? =
        dao.getById(id)?.takeUnless { it.isDeleted }?.let { codec.decodeDocument(it.documentJson) }

    suspend fun createNewMap(title: String = "Peta Pikiran Baru"): MindMapDocument {
        val doc = MindMapTreeOps.createEmpty(title)
        saveDocument(doc)
        return doc
    }

    suspend fun createSampleMap(): MindMapDocument {
        val doc = MindMapTreeOps.createSampleProject()
        saveDocument(doc)
        return doc
    }

    suspend fun duplicateMap(sourceId: String): MindMapDocument? {
        val source = getDocument(sourceId) ?: return null
        val now = System.currentTimeMillis()
        val idMap = source.nodes.associate { it.id to UUID.randomUUID().toString() }
        val clonedNodes = source.nodes.map { node ->
            node.copy(
                id = idMap.getValue(node.id),
                parentId = node.parentId?.let { idMap[it] }
            )
        }
        val cloned = source.copy(
            id = UUID.randomUUID().toString(),
            title = "${source.title} (Salinan)",
            rootNodeId = idMap.getValue(source.rootNodeId),
            nodes = clonedNodes,
            createdAt = now,
            updatedAt = now
        )
        saveDocument(cloned)
        return cloned
    }

    suspend fun saveDocument(document: MindMapDocument) {
        val existing = dao.getById(document.id)
        dao.upsert(
            entityFromDocument(
                doc = document,
                backupLocation = existing?.backupLocation,
                lastBackedUpAt = existing?.lastBackedUpAt ?: 0L
            )
        )
        scope.launch { syncNow() }
    }

    /** Hapus sebagai penanda (soft delete) agar hapusan ikut tersinkron ke perangkat lain. */
    suspend fun deleteMap(id: String) {
        val existing = dao.getById(id)?.takeUnless { it.isDeleted } ?: return
        val now = maxOf(System.currentTimeMillis(), existing.updatedAt + 1)
        dao.softDelete(id, now)
        scope.launch { syncNow() }
    }
}
