package com.example.sync

import com.example.data.DriveFileMetadata
import com.example.data.MindMapJsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class DriveUnauthorizedException : IOException("Drive token unauthorized")
class DriveHttpException(val code: Int, message: String) : IOException(message)

data class SyncFolderInfo(
    val id: String,
    val name: String,
    val isSharedWithMe: Boolean,
    val ownerName: String? = null
)

/**
 * Lightweight Google Drive v3 REST client using Android's built-in HttpURLConnection
 * (zero third-party HTTP dependencies to keep APK well under 10 MB).
 *
 * Consolidates all synced mind map JSON files inside a single visible "PetaPikir" folder
 * in Google Drive (supporting both owned folders and folders/shortcuts shared by other users).
 */
class GoogleDriveApiClient(
    private val codec: MindMapJsonCodec
) {
    companion object {
        const val APP_FOLDER_NAME = "PetaPikir"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val SHORTCUT_MIME_TYPE = "application/vnd.google-apps.shortcut"
        private val INVALID_FILE_CHARS = Regex("""[\\/:*?"<>|\r\n]+""")

        fun buildDriveFileName(title: String): String {
            val cleaned = title
                .trim()
                .replace(INVALID_FILE_CHARS, " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(64)
                .ifBlank { "Peta Pikiran" }
            return "$cleaned.json"
        }
    }

    suspend fun fetchUserEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        val (code, body) = executeRequest(
            url = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)",
            method = "GET",
            accessToken = accessToken
        )
        if (code !in 200..299) return@withContext null
        runCatching {
            val userObj = JSONObject(body).optJSONObject("user")
            userObj?.optString("emailAddress")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun ensureSyncFolder(accessToken: String): SyncFolderInfo = withContext(Dispatchers.IO) {
        val query = "(mimeType='$FOLDER_MIME_TYPE' or mimeType='$SHORTCUT_MIME_TYPE') and name='$APP_FOLDER_NAME' and trashed=false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val listUrl = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=drive" +
            "&includeItemsFromAllDrives=true&supportsAllDrives=true" +
            "&q=$encodedQuery" +
            "&fields=files(id,name,mimeType,ownedByMe,shared,owners(displayName,emailAddress),shortcutDetails(targetId,targetMimeType),modifiedTime)" +
            "&pageSize=25"

        val (listCode, listBody) = executeRequest(
            url = listUrl,
            method = "GET",
            accessToken = accessToken
        )
        if (listCode == 401) throw DriveUnauthorizedException()
        if (listCode !in 200..299) {
            throw DriveHttpException(listCode, "Gagal memeriksa folder Drive ($listCode)")
        }

        val candidates = codec.decodeDriveFileList(listBody)?.files.orEmpty()

        // 1. Prefer shortcut pointing to a shared PetaPikir folder ("Add shortcut to Drive")
        val shortcutFolder = candidates.firstOrNull {
            it.mimeType == SHORTCUT_MIME_TYPE &&
                it.shortcutTargetMimeType == FOLDER_MIME_TYPE &&
                !it.shortcutTargetId.isNullOrBlank()
        }?.let { shortcut ->
            fetchFileMetadata(accessToken, shortcut.shortcutTargetId!!)
        }
        if (shortcutFolder != null && shortcutFolder.mimeType == FOLDER_MIME_TYPE) {
            return@withContext SyncFolderInfo(
                id = shortcutFolder.id,
                name = shortcutFolder.name.ifBlank { APP_FOLDER_NAME },
                isSharedWithMe = !shortcutFolder.ownedByMe,
                ownerName = shortcutFolder.ownerDisplayName
            )
        }

        // 2. Next prefer a PetaPikir folder shared by another Google Drive user
        val sharedFolder = candidates.firstOrNull {
            it.mimeType == FOLDER_MIME_TYPE && !it.ownedByMe
        }
        if (sharedFolder != null) {
            return@withContext SyncFolderInfo(
                id = sharedFolder.id,
                name = sharedFolder.name.ifBlank { APP_FOLDER_NAME },
                isSharedWithMe = true,
                ownerName = sharedFolder.ownerDisplayName
            )
        }

        // 3. Otherwise use existing owned PetaPikir folder
        val ownedFolder = candidates.firstOrNull { it.mimeType == FOLDER_MIME_TYPE }
        if (ownedFolder != null) {
            return@withContext SyncFolderInfo(
                id = ownedFolder.id,
                name = ownedFolder.name.ifBlank { APP_FOLDER_NAME },
                isSharedWithMe = false,
                ownerName = ownedFolder.ownerDisplayName
            )
        }

        // 4. Create the single PetaPikir folder in My Drive root
        val createMetadata = JSONObject().apply {
            put("name", APP_FOLDER_NAME)
            put("mimeType", FOLDER_MIME_TYPE)
            put("description", "Folder sinkronisasi otomatis 2 arah untuk peta pikiran PetaPikir")
            put(
                "appProperties",
                JSONObject().apply {
                    put("app", APP_FOLDER_NAME)
                    put("folderType", "sync_root")
                }
            )
        }.toString()

        val (createCode, createBody) = executeRequest(
            url = "https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id,name,ownedByMe",
            method = "POST",
            accessToken = accessToken,
            jsonBody = createMetadata
        )
        if (createCode == 401) throw DriveUnauthorizedException()
        if (createCode !in 200..299) {
            throw DriveHttpException(createCode, "Gagal membuat folder PetaPikir di Drive ($createCode)")
        }

        val created = codec.decodeDriveFile(createBody)
            ?: throw IOException("ID folder Google Drive tidak diterima.")
        SyncFolderInfo(
            id = created.id,
            name = created.name.ifBlank { APP_FOLDER_NAME },
            isSharedWithMe = false,
            ownerName = null
        )
    }

    private fun fetchFileMetadata(accessToken: String, fileId: String): DriveFileMetadata? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId" +
            "?supportsAllDrives=true" +
            "&fields=id,name,mimeType,modifiedTime,appProperties,ownedByMe,shared,owners(displayName,emailAddress),trashed"
        val (code, body) = executeRequest(url = url, method = "GET", accessToken = accessToken)
        if (code == 401) throw DriveUnauthorizedException()
        if (code !in 200..299) return null
        val isTrashed = runCatching { JSONObject(body).optBoolean("trashed", false) }.getOrDefault(false)
        if (isTrashed) return null
        return codec.decodeDriveFile(body)
    }

    suspend fun listVisibleFiles(accessToken: String, folderId: String): List<DriveFileMetadata> =
        withContext(Dispatchers.IO) {
            val query = "'$folderId' in parents and trashed=false and mimeType!='$FOLDER_MIME_TYPE'"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files" +
                "?spaces=drive" +
                "&includeItemsFromAllDrives=true&supportsAllDrives=true" +
                "&q=$encodedQuery" +
                "&fields=files(id,name,mimeType,modifiedTime,appProperties,shortcutDetails(targetId,targetMimeType))" +
                "&pageSize=100"
            val (code, body) = executeRequest(
                url = url,
                method = "GET",
                accessToken = accessToken
            )
            if (code == 401) throw DriveUnauthorizedException()
            if (code !in 200..299) {
                throw DriveHttpException(code, "Gagal membaca daftar Drive ($code)")
            }
            val rawFiles = codec.decodeDriveFileList(body)?.files.orEmpty()
            val resolved = mutableListOf<DriveFileMetadata>()
            for (item in rawFiles) {
                val actual = if (item.mimeType == SHORTCUT_MIME_TYPE && !item.shortcutTargetId.isNullOrBlank()) {
                    fetchFileMetadata(accessToken, item.shortcutTargetId)
                } else {
                    item
                } ?: continue

                val isMindMapFile = actual.name.endsWith(".json", ignoreCase = true) ||
                    actual.mimeType == "application/json" ||
                    actual.appProperties?.containsKey("mindMapId") == true
                if (isMindMapFile) {
                    resolved.add(actual)
                }
            }
            resolved
        }

    suspend fun downloadFileContent(accessToken: String, fileId: String): String = withContext(Dispatchers.IO) {
        val (code, body) = executeRequest(
            url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true",
            method = "GET",
            accessToken = accessToken
        )
        if (code == 401) throw DriveUnauthorizedException()
        if (code !in 200..299) {
            throw DriveHttpException(code, "Gagal mengunduh berkas Drive ($code)")
        }
        body
    }

    suspend fun createVisibleFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        jsonContent: String,
        mindMapId: String,
        updatedAt: Long
    ): DriveFileMetadata = withContext(Dispatchers.IO) {
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
            put("mimeType", "application/json")
            put("description", "Peta pikiran PetaPikir")
            put(
                "appProperties",
                JSONObject().apply {
                    put("mindMapId", mindMapId)
                    put("updatedAt", updatedAt.toString())
                }
            )
        }.toString()

        val (code, body) = executeMultipartUpload(
            url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true&fields=id,name,mimeType,modifiedTime,appProperties",
            accessToken = accessToken,
            methodOverride = null,
            metadataJson = metadata,
            contentJson = jsonContent
        )
        if (code == 401) throw DriveUnauthorizedException()
        if (code !in 200..299) {
            throw DriveHttpException(code, "Gagal mengunggah ke Drive ($code)")
        }
        codec.decodeDriveFile(body) ?: throw IOException("Respons Google Drive tidak valid.")
    }

    suspend fun updateVisibleFile(
        accessToken: String,
        fileId: String,
        fileName: String,
        jsonContent: String,
        mindMapId: String,
        updatedAt: Long
    ): DriveFileMetadata = withContext(Dispatchers.IO) {
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "application/json")
            put(
                "appProperties",
                JSONObject().apply {
                    put("mindMapId", mindMapId)
                    put("updatedAt", updatedAt.toString())
                }
            )
        }.toString()

        // Use POST with X-HTTP-Method-Override: PATCH for full compatibility across Android APIs
        val (code, body) = executeMultipartUpload(
            url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart&supportsAllDrives=true&fields=id,name,mimeType,modifiedTime,appProperties",
            accessToken = accessToken,
            methodOverride = "PATCH",
            metadataJson = metadata,
            contentJson = jsonContent
        )
        if (code == 401) throw DriveUnauthorizedException()
        if (code !in 200..299) {
            throw DriveHttpException(code, "Gagal memperbarui Drive ($code)")
        }
        codec.decodeDriveFile(body) ?: throw IOException("Respons Google Drive tidak valid.")
    }

    suspend fun deleteFile(accessToken: String, fileId: String) = withContext(Dispatchers.IO) {
        val (code, _) = executeRequest(
            url = "https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true",
            method = "DELETE",
            accessToken = accessToken
        )
        if (code == 401) throw DriveUnauthorizedException()
        if (code !in 200..299 && code != 404) {
            throw DriveHttpException(code, "Gagal menghapus berkas Drive ($code)")
        }
    }

    private fun executeRequest(
        url: String,
        method: String,
        accessToken: String,
        jsonBody: String? = null
    ): Pair<Int, String> {
        val bodyBytes = jsonBody?.toByteArray(Charsets.UTF_8)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (bodyBytes != null) {
                doOutput = true
                setFixedLengthStreamingMode(bodyBytes.size)
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        return try {
            if (bodyBytes != null) {
                conn.outputStream.use { it.write(bodyBytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            conn.disconnect()
        }
    }

    private fun executeMultipartUpload(
        url: String,
        accessToken: String,
        methodOverride: String?,
        metadataJson: String,
        contentJson: String
    ): Pair<Int, String> {
        val boundary = "PetaPikir_${UUID.randomUUID().toString().replace("-", "")}"
        val payload = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(contentJson).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }.toByteArray(Charsets.UTF_8)

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 25_000
            setFixedLengthStreamingMode(payload.size)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            if (methodOverride != null) {
                setRequestProperty("X-HTTP-Method-Override", methodOverride)
            }
        }
        return try {
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            conn.disconnect()
        }
    }
}
