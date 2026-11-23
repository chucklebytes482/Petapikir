package com.example.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.data.MindMapJsonCodec
import com.example.model.MindMapDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class BackupWriteResult(
    val locationKey: String,
    val fileName: String,
    val displayPath: String,
    val timestamp: Long
)

interface LocalBackupStorage {
    suspend fun backupDocument(
        document: MindMapDocument,
        previousLocation: String?,
        settings: BackupSettings
    ): Result<BackupWriteResult>

    suspend fun deleteBackup(
        previousLocation: String?,
        title: String,
        settings: BackupSettings
    )

    suspend fun importDocuments(settings: BackupSettings): Result<List<MindMapDocument>>
}

/**
 * Automatic local JSON backup storage targeting `/storage/emulated/0/Documents/` by default
 * (with zero automatic app subfolder) or any user-selected path inside `Documents/`.
 *
 * Uses zero broad storage permissions:
 * - Persisted SAF tree URI (`OpenDocumentTree`) when user picks a folder via system picker.
 * - `MediaStore.Files` with `RELATIVE_PATH = "Documents/"` (or `"Documents/<subPath>/"`) on API 29+.
 * - Safe filesystem fallback on API 24-28 (or JVM test environments).
 */
class AndroidLocalBackupManager(
    context: Context,
    private val codec: MindMapJsonCodec
) : LocalBackupStorage {

    private val appContext = context.applicationContext

    override suspend fun backupDocument(
        document: MindMapDocument,
        previousLocation: String?,
        settings: BackupSettings
    ): Result<BackupWriteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val json = codec.encodeDocument(document)
            val now = System.currentTimeMillis()
            val displayPath = settings.displayPath

            val treeUriString = settings.treeUriString
            if (!treeUriString.isNullOrBlank()) {
                val safResult = runCatching {
                    writeToSafTree(
                        treeUri = Uri.parse(treeUriString),
                        document = document,
                        json = json,
                        previousLocation = previousLocation,
                        displayPath = displayPath,
                        timestamp = now
                    )
                }
                if (safResult.isSuccess) {
                    return@runCatching safResult.getOrThrow()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mediaStoreResult = runCatching {
                    writeToMediaStoreDocuments(
                        document = document,
                        json = json,
                        previousLocation = previousLocation,
                        normalizedSubPath = settings.relativeSubPath,
                        displayPath = displayPath,
                        timestamp = now
                    )
                }
                if (mediaStoreResult.isSuccess) {
                    return@runCatching mediaStoreResult.getOrThrow()
                }
            }

            writeToFallbackDirectory(
                document = document,
                json = json,
                previousLocation = previousLocation,
                normalizedSubPath = settings.relativeSubPath,
                displayPath = displayPath,
                timestamp = now
            )
        }
    }

    override suspend fun deleteBackup(
        previousLocation: String?,
        title: String,
        settings: BackupSettings
    ) = withContext(Dispatchers.IO) {
        runCatching {
            if (!previousLocation.isNullOrBlank()) {
                if (previousLocation.startsWith("content://")) {
                    val uri = Uri.parse(previousLocation)
                    val deletedViaDoc = runCatching {
                        DocumentsContract.deleteDocument(appContext.contentResolver, uri)
                    }.getOrDefault(false)
                    if (!deletedViaDoc) {
                        runCatching { appContext.contentResolver.delete(uri, null, null) }
                    }
                    return@runCatching
                } else {
                    val file = File(previousLocation)
                    if (file.exists()) {
                        file.delete()
                        return@runCatching
                    }
                }
            }
        }
        Unit
    }

    override suspend fun importDocuments(settings: BackupSettings): Result<List<MindMapDocument>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val treeUriString = settings.treeUriString
                if (!treeUriString.isNullOrBlank()) {
                    val safDocs = runCatching {
                        readFromSafTree(Uri.parse(treeUriString))
                    }
                    if (safDocs.isSuccess) {
                        return@runCatching safDocs.getOrThrow()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val mediaDocs = runCatching {
                        readFromMediaStoreDocuments(settings.relativeSubPath)
                    }
                    if (mediaDocs.isSuccess && mediaDocs.getOrThrow().isNotEmpty()) {
                        return@runCatching mediaDocs.getOrThrow()
                    }
                }

                readFromFallbackDirectory(settings.relativeSubPath)
            }
        }

    private fun writeToSafTree(
        treeUri: Uri,
        document: MindMapDocument,
        json: String,
        previousLocation: String?,
        displayPath: String,
        timestamp: Long
    ): BackupWriteResult {
        val resolver = appContext.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        val desiredFileName = BackupPathValidator.buildSafeFileName(document.title)
        val existingChildren = querySafChildren(childrenUri)
        val previousUri = previousLocation?.takeIf { it.startsWith("content://") }?.let(Uri::parse)

        val matchedByPrevious = previousUri?.let { prev ->
            existingChildren.firstOrNull {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, it.docId) == prev
            }
        }

        val targetFileName = resolveUniqueSafFileName(
            desiredFileName = desiredFileName,
            documentId = document.id,
            existingChildren = existingChildren,
            ownedDocId = matchedByPrevious?.docId,
            treeUri = treeUri
        )

        val targetUri: Uri = if (matchedByPrevious != null && matchedByPrevious.displayName == targetFileName) {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, matchedByPrevious.docId)
        } else {
            if (matchedByPrevious != null) {
                val oldUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, matchedByPrevious.docId)
                runCatching { DocumentsContract.deleteDocument(resolver, oldUri) }
            }
            val sameNameExisting = existingChildren.firstOrNull { it.displayName == targetFileName }
            if (sameNameExisting != null) {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, sameNameExisting.docId)
            } else {
                DocumentsContract.createDocument(
                    resolver,
                    parentDocUri,
                    "application/json",
                    targetFileName
                ) ?: throw IOException("Tidak dapat membuat berkas cadangan di folder terpilih.")
            }
        }

        resolver.openOutputStream(targetUri, "wt")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IOException("Gagal membuka aliran tulis untuk cadangan lokal.")

        return BackupWriteResult(
            locationKey = targetUri.toString(),
            fileName = targetFileName,
            displayPath = displayPath,
            timestamp = timestamp
        )
    }

    private fun readFromSafTree(treeUri: Uri): List<MindMapDocument> {
        val resolver = appContext.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val children = querySafChildren(childrenUri)
        val result = mutableListOf<MindMapDocument>()
        for (child in children) {
            if (!child.displayName.endsWith(".json", ignoreCase = true)) continue
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.docId)
            val content = runCatching {
                resolver.openInputStream(docUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull() ?: continue
            codec.decodeDocument(content)?.let(result::add)
        }
        return result
    }

    private data class SafChild(val docId: String, val displayName: String)

    private fun querySafChildren(childrenUri: Uri): List<SafChild> {
        val resolver = appContext.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        val list = mutableListOf<SafChild>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (idIdx >= 0 && nameIdx >= 0) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    list += SafChild(docId, name)
                }
            }
        }
        return list
    }

    private fun resolveUniqueSafFileName(
        desiredFileName: String,
        documentId: String,
        existingChildren: List<SafChild>,
        ownedDocId: String?,
        treeUri: Uri
    ): String {
        val conflict = existingChildren.firstOrNull {
            it.displayName.equals(desiredFileName, ignoreCase = true) && it.docId != ownedDocId
        } ?: return desiredFileName

        val conflictUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, conflict.docId)
        val conflictDoc = runCatching {
            appContext.contentResolver.openInputStream(conflictUri)?.bufferedReader()?.use { it.readText() }
                ?.let { codec.decodeDocument(it) }
        }.getOrNull()

        return if (conflictDoc == null || conflictDoc.id == documentId) {
            desiredFileName
        } else {
            BackupPathValidator.buildSafeFileName(
                title = desiredFileName.removeSuffix(".json"),
                suffix = documentId.take(6)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToMediaStoreDocuments(
        document: MindMapDocument,
        json: String,
        previousLocation: String?,
        normalizedSubPath: String,
        displayPath: String,
        timestamp: Long
    ): BackupWriteResult {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val relativePath = BackupPathValidator.toMediaStoreRelativePath(normalizedSubPath)
        val desiredFileName = BackupPathValidator.buildSafeFileName(document.title)

        val previousUri = previousLocation?.takeIf { it.startsWith("content://media/") }?.let(Uri::parse)
        val existingEntries = queryMediaStoreJsonEntries(collection, relativePath)
        val matchedPrevious = previousUri?.let { prev -> existingEntries.firstOrNull { it.uri == prev } }

        val targetFileName = resolveUniqueMediaStoreFileName(
            desiredFileName = desiredFileName,
            documentId = document.id,
            entries = existingEntries,
            ownedUri = matchedPrevious?.uri
        )

        val existingTarget = existingEntries.firstOrNull {
            it.displayName.equals(targetFileName, ignoreCase = true)
        }

        if (previousUri != null && previousUri != existingTarget?.uri) {
            runCatching { resolver.delete(previousUri, null, null) }
        }

        val targetUri: Uri = if (existingTarget != null) {
            existingTarget.uri
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.insert(collection, values)
                ?: throw IOException("MediaStore tidak mengembalikan URI untuk $relativePath$targetFileName")
        }

        resolver.openOutputStream(targetUri, "wt")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IOException("Gagal membuka aliran tulis MediaStore.")

        if (existingTarget == null) {
            val doneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            runCatching { resolver.update(targetUri, doneValues, null, null) }
        }

        return BackupWriteResult(
            locationKey = targetUri.toString(),
            fileName = targetFileName,
            displayPath = displayPath,
            timestamp = timestamp
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readFromMediaStoreDocuments(normalizedSubPath: String): List<MindMapDocument> {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val relativePath = BackupPathValidator.toMediaStoreRelativePath(normalizedSubPath)
        val entries = queryMediaStoreJsonEntries(collection, relativePath)
        val docs = mutableListOf<MindMapDocument>()
        for (entry in entries) {
            val content = runCatching {
                resolver.openInputStream(entry.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull() ?: continue
            codec.decodeDocument(content)?.let(docs::add)
        }
        return docs
    }

    private data class MediaStoreEntry(val uri: Uri, val displayName: String)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryMediaStoreJsonEntries(collection: Uri, relativePath: String): List<MediaStoreEntry> {
        val resolver = appContext.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val trimmedPath = relativePath.trimEnd('/')
        val selection = "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?) AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(relativePath, trimmedPath, "%.json")
        val result = mutableListOf<MediaStoreEntry>()
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (idIdx >= 0 && nameIdx >= 0) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    result += MediaStoreEntry(uri, name)
                }
            }
        }
        return result
    }

    private fun resolveUniqueMediaStoreFileName(
        desiredFileName: String,
        documentId: String,
        entries: List<MediaStoreEntry>,
        ownedUri: Uri?
    ): String {
        val conflict = entries.firstOrNull {
            it.displayName.equals(desiredFileName, ignoreCase = true) && it.uri != ownedUri
        } ?: return desiredFileName

        val conflictDoc = runCatching {
            appContext.contentResolver.openInputStream(conflict.uri)?.bufferedReader()?.use { it.readText() }
                ?.let { codec.decodeDocument(it) }
        }.getOrNull()

        return if (conflictDoc == null || conflictDoc.id == documentId) {
            desiredFileName
        } else {
            BackupPathValidator.buildSafeFileName(
                title = desiredFileName.removeSuffix(".json"),
                suffix = documentId.take(6)
            )
        }
    }

    private fun resolveFallbackDirectory(normalizedSubPath: String): File {
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val candidate = if (normalizedSubPath.isBlank()) publicDocs else File(publicDocs, normalizedSubPath)
        if ((candidate.exists() || candidate.mkdirs()) && candidate.canWrite()) {
            return candidate
        }
        val appDocs = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
        val fallback = if (normalizedSubPath.isBlank()) appDocs else File(appDocs, normalizedSubPath)
        fallback.mkdirs()
        return fallback
    }

    private fun writeToFallbackDirectory(
        document: MindMapDocument,
        json: String,
        previousLocation: String?,
        normalizedSubPath: String,
        displayPath: String,
        timestamp: Long
    ): BackupWriteResult {
        val dir = resolveFallbackDirectory(normalizedSubPath)
        return DirectoryLocalBackupStorage.writeDocumentToDir(
            dir = dir,
            document = document,
            json = json,
            previousLocation = previousLocation,
            displayPath = displayPath,
            timestamp = timestamp,
            codec = codec
        )
    }

    private fun readFromFallbackDirectory(normalizedSubPath: String): List<MindMapDocument> {
        val dir = resolveFallbackDirectory(normalizedSubPath)
        return DirectoryLocalBackupStorage.readDocumentsFromDir(dir, codec)
    }
}

/**
 * Pure directory-backed implementation useful for deterministic unit tests and fallback I/O.
 */
class DirectoryLocalBackupStorage(
    private val documentsRootDir: File,
    private val codec: MindMapJsonCodec
) : LocalBackupStorage {

    override suspend fun backupDocument(
        document: MindMapDocument,
        previousLocation: String?,
        settings: BackupSettings
    ): Result<BackupWriteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = resolveDir(settings.relativeSubPath)
            val json = codec.encodeDocument(document)
            writeDocumentToDir(
                dir = targetDir,
                document = document,
                json = json,
                previousLocation = previousLocation,
                displayPath = settings.displayPath,
                timestamp = System.currentTimeMillis(),
                codec = codec
            )
        }
    }

    override suspend fun deleteBackup(
        previousLocation: String?,
        title: String,
        settings: BackupSettings
    ) = withContext(Dispatchers.IO) {
        if (!previousLocation.isNullOrBlank()) {
            val prev = File(previousLocation)
            if (prev.exists()) prev.delete()
        }
        Unit
    }

    override suspend fun importDocuments(settings: BackupSettings): Result<List<MindMapDocument>> =
        withContext(Dispatchers.IO) {
            runCatching {
                readDocumentsFromDir(resolveDir(settings.relativeSubPath), codec)
            }
        }

    private fun resolveDir(normalizedSubPath: String): File {
        val dir = if (normalizedSubPath.isBlank()) {
            documentsRootDir
        } else {
            File(documentsRootDir, normalizedSubPath)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    companion object {
        fun writeDocumentToDir(
            dir: File,
            document: MindMapDocument,
            json: String,
            previousLocation: String?,
            displayPath: String,
            timestamp: Long,
            codec: MindMapJsonCodec
        ): BackupWriteResult {
            if (!dir.exists()) dir.mkdirs()
            val desiredName = BackupPathValidator.buildSafeFileName(document.title)
            val previousFile = previousLocation?.takeUnless { it.startsWith("content://") }?.let(::File)

            val candidate = File(dir, desiredName)
            val targetFile = if (candidate.exists() && candidate != previousFile) {
                val existingDoc = runCatching { codec.decodeDocument(candidate.readText(Charsets.UTF_8)) }.getOrNull()
                if (existingDoc == null || existingDoc.id == document.id) {
                    candidate
                } else {
                    File(dir, BackupPathValidator.buildSafeFileName(document.title, suffix = document.id.take(6)))
                }
            } else {
                candidate
            }

            if (previousFile != null && previousFile.exists() && previousFile.canonicalPath != targetFile.canonicalPath) {
                previousFile.delete()
            }

            targetFile.writeText(json, Charsets.UTF_8)
            return BackupWriteResult(
                locationKey = targetFile.absolutePath,
                fileName = targetFile.name,
                displayPath = displayPath,
                timestamp = timestamp
            )
        }

        fun readDocumentsFromDir(dir: File, codec: MindMapJsonCodec): List<MindMapDocument> {
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            return dir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
                .mapNotNull { file ->
                    runCatching { codec.decodeDocument(file.readText(Charsets.UTF_8)) }.getOrNull()
                }
        }
    }
}
