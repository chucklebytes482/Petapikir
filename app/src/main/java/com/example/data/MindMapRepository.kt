package com.example.data

import com.example.backup.BackupPathValidator
import com.example.backup.BackupPreferences
import com.example.backup.BackupSettings
import com.example.backup.LocalBackupStorage
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

class MindMapRepository(
    private val dao: MindMapDao,
    val codec: MindMapJsonCodec,
    private val backupPreferences: BackupPreferences,
    private val backupStorage: LocalBackupStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupMutex = Mutex()

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
            if (settings.lastBackupAt > 0L) {
                _backupStatus.value = LocalBackupStatus.BackedUp(
                    timestamp = settings.lastBackupAt,
                    count = settings.backedUpCount
                )
            } else {
                _backupStatus.value = LocalBackupStatus.Ready
            }
        }
    }

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
        val encoded = codec.encodeDocument(document)
        val pendingEntity = MindMapEntity(
            id = document.id,
            title = document.title,
            nodeCount = document.nodes.size,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt,
            documentJson = encoded,
            backupLocation = existing?.backupLocation,
            lastBackedUpAt = existing?.lastBackedUpAt ?: 0L,
            backupState = SyncState.PENDING_UPLOAD.name,
            isDeleted = false
        )
        dao.upsert(pendingEntity)

        scope.launch {
            autoBackupSingleDocument(document.id)
        }
    }

    suspend fun deleteMap(id: String) {
        val existing = dao.getById(id) ?: return
        val settings = backupPreferences.settingsFlow.first()
        dao.hardDeleteById(id)
        scope.launch {
            backupMutex.withLock {
                backupStorage.deleteBackup(
                    previousLocation = existing.backupLocation,
                    title = existing.title,
                    settings = settings
                )
            }
        }
    }

    suspend fun updateBackupSubPath(rawSubPath: String): Result<String> {
        val normalizedResult = BackupPathValidator.normalizeSubPath(rawSubPath)
        val normalized = normalizedResult.getOrElse { return Result.failure(it) }
        backupPreferences.setRelativeSubPath(normalized)
        dao.resetAllBackupLocations()
        val displayPath = BackupPathValidator.toDisplayPath(normalized)
        scope.launch { syncAllToActivePath() }
        return Result.success(displayPath)
    }

    suspend fun updateBackupTreeUri(uriString: String, treeDocumentId: String?): Result<String> {
        val normalizedResult = BackupPathValidator.parseDocumentsTreeUri(treeDocumentId)
        val normalized = normalizedResult.getOrElse { return Result.failure(it) }
        backupPreferences.setTreeUri(uriString, normalized)
        dao.resetAllBackupLocations()
        val displayPath = BackupPathValidator.toDisplayPath(normalized)
        scope.launch { syncAllToActivePath() }
        return Result.success(displayPath)
    }

    private suspend fun syncAllToActivePath(): Result<Int> = backupMutex.withLock {
        val settings = backupPreferences.settingsFlow.first()
        val activeMaps = dao.getActiveMaps()
        if (activeMaps.isEmpty()) {
            val now = System.currentTimeMillis()
            backupPreferences.setLastBackupInfo(now, 0)
            _backupStatus.value = LocalBackupStatus.BackedUp(now, 0)
            return@withLock Result.success(0)
        }

        _backupStatus.value = LocalBackupStatus.BackingUp
        var savedCount = 0
        var lastError: Throwable? = null

        for (entity in activeMaps) {
            val doc = codec.decodeDocument(entity.documentJson) ?: continue
            val writeResult = backupStorage.backupDocument(
                document = doc,
                previousLocation = entity.backupLocation,
                settings = settings
            )
            writeResult.onSuccess { info ->
                savedCount++
                dao.upsert(
                    entity.copy(
                        backupLocation = info.locationKey,
                        lastBackedUpAt = info.timestamp,
                        backupState = SyncState.SYNCED.name
                    )
                )
            }.onFailure { err ->
                lastError = err
            }
        }

        return@withLock if (savedCount > 0 || lastError == null) {
            val now = System.currentTimeMillis()
            backupPreferences.setLastBackupInfo(now, savedCount)
            _backupStatus.value = LocalBackupStatus.BackedUp(now, savedCount)
            Result.success(savedCount)
        } else {
            val message = lastError?.localizedMessage ?: "Gagal menyimpan cadangan ke ${settings.displayPath}"
            _backupStatus.value = LocalBackupStatus.Error(message)
            Result.failure(lastError ?: IllegalStateException(message))
        }
    }

    private suspend fun autoBackupSingleDocument(documentId: String) = backupMutex.withLock {
        val entity = dao.getById(documentId)?.takeUnless { it.isDeleted } ?: return@withLock
        val doc = codec.decodeDocument(entity.documentJson) ?: return@withLock
        val settings = backupPreferences.settingsFlow.first()
        _backupStatus.value = LocalBackupStatus.BackingUp

        val result = backupStorage.backupDocument(
            document = doc,
            previousLocation = entity.backupLocation,
            settings = settings
        )

        result.onSuccess { info ->
            dao.upsert(
                entity.copy(
                    backupLocation = info.locationKey,
                    lastBackedUpAt = info.timestamp,
                    backupState = SyncState.SYNCED.name
                )
            )
            val totalBackedUp = dao.getActiveMaps().count { it.backupState == SyncState.SYNCED.name }
            backupPreferences.setLastBackupInfo(info.timestamp, totalBackedUp)
            _backupStatus.value = LocalBackupStatus.BackedUp(info.timestamp, totalBackedUp)
        }.onFailure { err ->
            _backupStatus.value = LocalBackupStatus.Error(
                err.localizedMessage ?: "Gagal mencadangkan otomatis ke ${settings.displayPath}"
            )
        }
    }
}
