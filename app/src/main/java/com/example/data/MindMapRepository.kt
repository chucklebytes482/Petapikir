package com.example.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.data.local.MindMapDao
import com.example.data.local.MindMapEntity
import com.example.model.MindMapDocument
import com.example.model.MindMapTreeOps
import com.example.model.SyncState
import com.example.sync.DriveAuthResult
import com.example.sync.DriveHttpException
import com.example.sync.DriveUnauthorizedException
import com.example.sync.GoogleDriveApiClient
import com.example.sync.GoogleDriveAuthManager
import com.example.sync.SyncPreferences
import com.example.sync.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

sealed class GlobalSyncStatus {
    data object NotConnected : GlobalSyncStatus()
    data object Syncing : GlobalSyncStatus()
    data class Synced(val timestamp: Long) : GlobalSyncStatus()
    data object OfflineQueued : GlobalSyncStatus()
    data class NeedsAuth(val pendingIntent: PendingIntent?, val message: String? = null) : GlobalSyncStatus()
    data class Error(val message: String) : GlobalSyncStatus()
}

data class MindMapSummary(
    val id: String,
    val title: String,
    val nodeCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState,
    val driveFileId: String?,
    val rootTitle: String,
    val previewChildTitles: List<String>
)

class MindMapRepository(
    context: Context,
    private val dao: MindMapDao,
    val codec: MindMapJsonCodec,
    private val syncPreferences: SyncPreferences,
    private val authManager: GoogleDriveAuthManager,
    private val driveClient: GoogleDriveApiClient
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var autoSyncDebounceJob: Job? = null

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _syncStatus = MutableStateFlow<GlobalSyncStatus>(GlobalSyncStatus.NotConnected)
    val syncStatus: StateFlow<GlobalSyncStatus> = _syncStatus.asStateFlow()

    private val _activeFolderInfo = MutableStateFlow<com.example.sync.SyncFolderInfo?>(null)
    val activeFolderInfo: StateFlow<com.example.sync.SyncFolderInfo?> = _activeFolderInfo.asStateFlow()

    val syncSettings: Flow<SyncSettings> = syncPreferences.settingsFlow
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
                syncState = runCatching { SyncState.valueOf(entity.syncState) }.getOrDefault(SyncState.LOCAL_ONLY),
                driveFileId = entity.driveFileId,
                rootTitle = rootNode?.title ?: entity.title,
                previewChildTitles = childTitles
            )
        }
    }

    init {
        registerNetworkCallback()
        scope.launch {
            val settings = syncPreferences.settingsFlow.first()
            if (settings.isDriveConnected) {
                _syncStatus.value = if (settings.lastSyncAt > 0L) {
                    GlobalSyncStatus.Synced(settings.lastSyncAt)
                } else {
                    GlobalSyncStatus.OfflineQueued
                }
            } else {
                _syncStatus.value = GlobalSyncStatus.NotConnected
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
        val settings = syncPreferences.settingsFlow.first()
        val nextState = if (settings.isDriveConnected) SyncState.PENDING_UPLOAD else SyncState.LOCAL_ONLY

        val entity = MindMapEntity(
            id = document.id,
            title = document.title,
            nodeCount = document.nodes.size,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt,
            documentJson = codec.encodeDocument(document),
            driveFileId = existing?.driveFileId,
            remoteModifiedAt = existing?.remoteModifiedAt ?: 0L,
            syncState = nextState.name,
            isDeleted = false
        )
        dao.upsert(entity)
    }

    suspend fun deleteMap(id: String) {
        val existing = dao.getById(id) ?: return
        if (existing.driveFileId != null) {
            dao.upsert(
                existing.copy(
                    isDeleted = true,
                    updatedAt = System.currentTimeMillis(),
                    syncState = SyncState.PENDING_UPLOAD.name
                )
            )
        } else {
            dao.hardDeleteById(id)
        }
    }

    suspend fun requestDriveConnection(activity: Activity? = null): DriveAuthResult {
        val result = authManager.getAccessToken(activity = activity, forceRefresh = true)
        if (result is DriveAuthResult.Authorized) {
            onDriveAuthorized(result.accessToken)
        } else if (result is DriveAuthResult.NeedsResolution) {
            _syncStatus.value = GlobalSyncStatus.NeedsAuth(result.pendingIntent)
        } else if (result is DriveAuthResult.Failed) {
            _syncStatus.value = GlobalSyncStatus.Error(result.message)
        }
        return result
    }

    suspend fun handleAuthIntentResult(activity: Activity? = null, data: Intent?): DriveAuthResult {
        val result = authManager.parseAuthorizationIntentResult(activity = activity, data = data)
        when (result) {
            is DriveAuthResult.Authorized -> onDriveAuthorized(result.accessToken)
            is DriveAuthResult.Failed -> {
                val connected = syncPreferences.settingsFlow.first().isDriveConnected
                _syncStatus.value = if (connected) {
                    GlobalSyncStatus.Error(result.message)
                } else {
                    GlobalSyncStatus.NotConnected
                }
            }
            is DriveAuthResult.NeedsResolution -> {
                _syncStatus.value = GlobalSyncStatus.NeedsAuth(result.pendingIntent)
            }
        }
        return result
    }

    private suspend fun onDriveAuthorized(accessToken: String) {
        val email = runCatching { driveClient.fetchUserEmail(accessToken) }.getOrNull()
        syncPreferences.setDriveConnected(true, email)
        syncNow()
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        syncPreferences.setAutoSyncEnabled(enabled)
        if (enabled) {
            val connected = syncPreferences.settingsFlow.first().isDriveConnected
            if (connected) {
                syncNow()
            }
        }
    }

    suspend fun disconnectDrive() {
        autoSyncDebounceJob?.cancel()
        authManager.invalidateToken()
        syncPreferences.setDriveConnected(false, null)
        dao.clearAllDriveBindings()
        _activeFolderInfo.value = null
        _syncStatus.value = GlobalSyncStatus.NotConnected
    }

    suspend fun syncNow(): Boolean = syncMutex.withLock {
        val settings = syncPreferences.settingsFlow.first()
        if (!settings.isDriveConnected) {
            _syncStatus.value = GlobalSyncStatus.NotConnected
            return@withLock false
        }
        if (!_isOnline.value) {
            _syncStatus.value = GlobalSyncStatus.OfflineQueued
            return@withLock false
        }

        _syncStatus.value = GlobalSyncStatus.Syncing

        return@withLock try {
            val token = obtainValidToken() ?: return@withLock false
            performBidirectionalSync(token)
            val now = System.currentTimeMillis()
            syncPreferences.setLastSyncAt(now)
            _syncStatus.value = GlobalSyncStatus.Synced(now)
            true
        } catch (_: DriveUnauthorizedException) {
            authManager.invalidateToken()
            when (val retry = authManager.getAccessToken(forceRefresh = true)) {
                is DriveAuthResult.Authorized -> {
                    try {
                        performBidirectionalSync(retry.accessToken)
                        val now = System.currentTimeMillis()
                        syncPreferences.setLastSyncAt(now)
                        _syncStatus.value = GlobalSyncStatus.Synced(now)
                        true
                    } catch (e: Exception) {
                        _syncStatus.value = GlobalSyncStatus.Error(e.localizedMessage ?: "Sinkronisasi gagal.")
                        false
                    }
                }
                is DriveAuthResult.NeedsResolution -> {
                    _syncStatus.value = GlobalSyncStatus.NeedsAuth(retry.pendingIntent, "Perlu izin ulang Google Drive.")
                    false
                }
                is DriveAuthResult.Failed -> {
                    _syncStatus.value = GlobalSyncStatus.Error(retry.message)
                    false
                }
            }
        } catch (e: DriveHttpException) {
            _syncStatus.value = GlobalSyncStatus.Error(e.message ?: "Kesalahan Google Drive (${e.code}).")
            false
        } catch (e: Exception) {
            if (!_isOnline.value) {
                _syncStatus.value = GlobalSyncStatus.OfflineQueued
            } else {
                _syncStatus.value = GlobalSyncStatus.Error(e.localizedMessage ?: "Gagal menyinkronkan ke Google Drive.")
            }
            false
        }
    }

    private suspend fun obtainValidToken(): String? {
        return when (val result = authManager.getAccessToken(forceRefresh = false)) {
            is DriveAuthResult.Authorized -> {
                val settings = syncPreferences.settingsFlow.first()
                if (settings.connectedEmail.isNullOrBlank()) {
                    runCatching { driveClient.fetchUserEmail(result.accessToken) }.getOrNull()?.let { email ->
                        syncPreferences.setConnectedEmail(email)
                    }
                }
                result.accessToken
            }
            is DriveAuthResult.NeedsResolution -> {
                _syncStatus.value = GlobalSyncStatus.NeedsAuth(result.pendingIntent)
                null
            }
            is DriveAuthResult.Failed -> {
                _syncStatus.value = GlobalSyncStatus.Error(result.message)
                null
            }
        }
    }

    private suspend fun performBidirectionalSync(token: String) {
        val folder = driveClient.ensureSyncFolder(token)
        _activeFolderInfo.value = folder
        val remoteFiles = driveClient.listVisibleFiles(token, folder.id)

        val remoteByMapId = mutableMapOf<String, DriveFileMetadata>()
        val remoteByFileId = mutableMapOf<String, DriveFileMetadata>()

        for (file in remoteFiles) {
            remoteByFileId[file.id] = file
            val fallbackDeterministicId = UUID.nameUUIDFromBytes("drive:${file.id}".toByteArray()).toString()
            val mapId = file.appProperties?.get("mindMapId")?.takeIf { it.isNotBlank() }
                ?: fallbackDeterministicId
            val existing = remoteByMapId[mapId]
            val existingModified = MindMapJsonCodec.parseIso8601Millis(existing?.modifiedTime)
            val fileModified = MindMapJsonCodec.parseIso8601Millis(file.modifiedTime)
            if (existing == null || fileModified >= existingModified) {
                remoteByMapId[mapId] = file
            }
        }

        val localRows = dao.getAllIncludingDeleted()
        val matchedRemoteIds = mutableSetOf<String>()

        // 1. Process local deletions, remote deletions, uploads, and remote updates (2-way)
        for (local in localRows) {
            val matchingRemote = local.driveFileId?.let { remoteByFileId[it] } ?: remoteByMapId[local.id]
            if (matchingRemote != null) {
                matchedRemoteIds.add(matchingRemote.id)
            }

            if (local.isDeleted) {
                if (matchingRemote != null) {
                    driveClient.deleteFile(token, matchingRemote.id)
                }
                dao.hardDeleteById(local.id)
                continue
            }

            if (matchingRemote == null) {
                // If this map was already synced to the visible folder (remoteModifiedAt > 0)
                // and has no pending local edits, another user or Drive removed it -> remove locally.
                if (local.driveFileId != null && local.remoteModifiedAt > 0L && local.syncState == SyncState.SYNCED.name) {
                    dao.hardDeleteById(local.id)
                    continue
                }

                val fileName = GoogleDriveApiClient.buildDriveFileName(local.title)
                val formattedJson = codec.decodeDocument(local.documentJson)?.let(codec::encodeDocument) ?: local.documentJson
                val created = driveClient.createVisibleFile(
                    accessToken = token,
                    folderId = folder.id,
                    fileName = fileName,
                    jsonContent = formattedJson,
                    mindMapId = local.id,
                    updatedAt = local.updatedAt
                )
                val createdModifiedMs = MindMapJsonCodec.parseIso8601Millis(created.modifiedTime)
                    .takeIf { it > 0L } ?: System.currentTimeMillis()
                dao.upsert(
                    local.copy(
                        documentJson = formattedJson,
                        driveFileId = created.id,
                        remoteModifiedAt = createdModifiedMs,
                        syncState = SyncState.SYNCED.name
                    )
                )
                continue
            }

            val remoteUpdatedAt = matchingRemote.appProperties?.get("updatedAt")?.toLongOrNull() ?: 0L
            val remoteModifiedMs = MindMapJsonCodec.parseIso8601Millis(matchingRemote.modifiedTime)
            val remoteModifiedNewer = remoteModifiedMs > 0L && local.remoteModifiedAt > 0L && remoteModifiedMs > local.remoteModifiedAt
            val remoteUpdatedNewer = remoteUpdatedAt > local.updatedAt

            if (remoteUpdatedNewer || (remoteModifiedNewer && (local.syncState == SyncState.SYNCED.name || remoteModifiedMs > local.updatedAt))) {
                val remoteJson = driveClient.downloadFileContent(token, matchingRemote.id)
                val fallbackId = matchingRemote.appProperties?.get("mindMapId")?.takeIf { it.isNotBlank() } ?: local.id
                val remoteDoc = codec.decodeDocument(remoteJson, fallbackId = fallbackId)
                if (remoteDoc != null) {
                    val effectiveUpdatedAt = maxOf(remoteDoc.updatedAt, remoteModifiedMs, local.updatedAt + 1L)
                    val normalizedDoc = if (remoteDoc.updatedAt < effectiveUpdatedAt) {
                        remoteDoc.copy(updatedAt = effectiveUpdatedAt)
                    } else {
                        remoteDoc
                    }
                    dao.upsert(
                        MindMapEntity(
                            id = local.id,
                            title = normalizedDoc.title,
                            nodeCount = normalizedDoc.nodes.size,
                            createdAt = normalizedDoc.createdAt,
                            updatedAt = normalizedDoc.updatedAt,
                            documentJson = codec.encodeDocument(normalizedDoc),
                            driveFileId = matchingRemote.id,
                            remoteModifiedAt = remoteModifiedMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                            syncState = SyncState.SYNCED.name,
                            isDeleted = false
                        )
                    )
                }
            } else {
                val fileName = GoogleDriveApiClient.buildDriveFileName(local.title)
                val formattedJson = codec.decodeDocument(local.documentJson)?.let(codec::encodeDocument) ?: local.documentJson
                val formatUpgraded = formattedJson != local.documentJson
                val needsUpload = formatUpgraded ||
                    local.syncState != SyncState.SYNCED.name ||
                    local.driveFileId != matchingRemote.id ||
                    local.remoteModifiedAt == 0L ||
                    local.updatedAt > remoteUpdatedAt ||
                    matchingRemote.name != fileName

                if (needsUpload) {
                    val updated = driveClient.updateVisibleFile(
                        accessToken = token,
                        fileId = matchingRemote.id,
                        fileName = fileName,
                        jsonContent = formattedJson,
                        mindMapId = local.id,
                        updatedAt = local.updatedAt
                    )
                    val updatedModifiedMs = MindMapJsonCodec.parseIso8601Millis(updated.modifiedTime)
                        .takeIf { it > 0L } ?: remoteModifiedMs.takeIf { it > 0L } ?: System.currentTimeMillis()
                    dao.upsert(
                        local.copy(
                            documentJson = formattedJson,
                            driveFileId = updated.id,
                            remoteModifiedAt = updatedModifiedMs,
                            syncState = SyncState.SYNCED.name
                        )
                    )
                }
            }
        }

        // 2. Download remote maps from PetaPikir folder (including files shared by other users) that don't exist locally yet
        for (remoteFile in remoteFiles) {
            if (remoteFile.id in matchedRemoteIds) continue
            val remoteJson = driveClient.downloadFileContent(token, remoteFile.id)
            val fallbackId = remoteFile.appProperties?.get("mindMapId")?.takeIf { it.isNotBlank() }
                ?: UUID.nameUUIDFromBytes("drive:${remoteFile.id}".toByteArray()).toString()
            val remoteDoc = codec.decodeDocument(remoteJson, fallbackId = fallbackId) ?: continue

            val existingByDocId = dao.getById(remoteDoc.id)
            if (existingByDocId != null && existingByDocId.isDeleted) continue

            val remoteModifiedMs = MindMapJsonCodec.parseIso8601Millis(remoteFile.modifiedTime)
                .takeIf { it > 0L } ?: System.currentTimeMillis()

            dao.upsert(
                MindMapEntity(
                    id = remoteDoc.id,
                    title = remoteDoc.title,
                    nodeCount = remoteDoc.nodes.size,
                    createdAt = remoteDoc.createdAt,
                    updatedAt = remoteDoc.updatedAt,
                    documentJson = codec.encodeDocument(remoteDoc),
                    driveFileId = remoteFile.id,
                    remoteModifiedAt = remoteModifiedMs,
                    syncState = SyncState.SYNCED.name,
                    isDeleted = false
                )
            )
        }
    }

    private fun checkCurrentlyOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = checkCurrentlyOnline()
                    if (!_isOnline.value) {
                        scope.launch {
                            val settings = syncPreferences.settingsFlow.first()
                            if (settings.isDriveConnected) {
                                _syncStatus.value = GlobalSyncStatus.OfflineQueued
                            }
                        }
                    }
                }
            })
        }
    }
}
