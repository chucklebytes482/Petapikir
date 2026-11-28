package com.example.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Lokasi tetap berkas sinkron (panduan 05 §1, penamaan 01 §4). */
object SyncLocation {
    const val ROOT_DIR = "/storage/emulated/0/autosync"
    const val APP_FOLDER = "petapikir_backup_json"
    const val FILE_NAME = "petapikir.json"
    const val FILE_PATH = "$ROOT_DIR/$APP_FOLDER/$FILE_NAME"
    const val DISPLAY_PATH = "$ROOT_DIR/$APP_FOLDER/"
}

/** Baca dan tulis seluruh berkas sinkron. Diganti fake di uji. */
interface SyncFileStore {
    /** null jika berkas belum ada. Melempar jika berkas ada tapi tidak bisa dibaca. */
    suspend fun read(): String?

    /** Menimpa seluruh berkas. Melempar jika gagal. */
    suspend fun write(text: String)
}

/**
 * Penyimpanan berkas sinkron Petapikir.
 * - Android 10+ (API 29): MediaStore dengan mode "wt", tanpa izin penyimpanan.
 * - Android 7–9 (API 24–28): app-private external files, karena aplikasi tidak meminta izin penyimpanan.
 */
class PetapikirSyncManager(
    context: Context,
    private val appFolder: String = SyncLocation.APP_FOLDER,
    private val fileName: String = SyncLocation.FILE_NAME
) : SyncFileStore {

    private val appContext = context.applicationContext
    private val relativePath = "autosync/$appFolder/"

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) readMediaStore() else readPrivate()
    }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeMediaStore(text) else writePrivate(text)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collection() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findMediaStoreUri(): android.net.Uri? {
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        appContext.contentResolver.query(
            collection(), projection, selection, arrayOf(relativePath, fileName), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection(), cursor.getLong(0))
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readMediaStore(): String? {
        val uri = findMediaStoreUri() ?: return null
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Berkas cadangan tidak bisa dibuka")
        return stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeMediaStore(text: String) {
        val uri = findMediaStoreUri() ?: run {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
            }
            appContext.contentResolver.insert(collection(), values)
                ?: throw IOException("Gagal membuat berkas cadangan")
        }
        val stream = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Gagal membuka berkas cadangan untuk ditulis")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun privateFile(): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(File(base, "autosync/$appFolder"), fileName)
    }

    private fun readPrivate(): String? {
        val file = privateFile()
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    private fun writePrivate(text: String) {
        val target = privateFile()
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$fileName.tmp")
        temp.writeText(text, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Gagal menyimpan berkas cadangan")
        }
    }
}
