package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupPreferences
import com.example.backup.SyncFileCodec
import com.example.backup.SyncFileStore
import com.example.backup.SyncMerge
import com.example.backup.SyncRecord
import com.example.data.MindMapJsonCodec
import com.example.data.MindMapRepository
import com.example.data.local.AppDatabase
import com.example.model.MindMapDocument
import com.example.model.MindMapTreeOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Berkas sinkron dalam memori, pengganti PetapikirSyncManager di uji. */
private class FakeSyncStore(var text: String? = null, var failRead: Boolean = false) : SyncFileStore {
    override suspend fun read(): String? {
        if (failRead) throw IOException("berkas tidak bisa dibaca")
        return text
    }

    override suspend fun write(text: String) {
        this.text = text
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newRepo(store: SyncFileStore): Pair<MindMapRepository, com.example.data.local.AppDatabase> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = MindMapRepository(
            dao = db.mindMapDao(),
            codec = MindMapJsonCodec(),
            backupPreferences = BackupPreferences(context),
            syncStore = store
        )
        return repo to db
    }

    private fun doc(id: String, title: String, updatedAt: Long): MindMapDocument =
        MindMapTreeOps.createEmpty(title).copy(id = id, createdAt = 1_000L, updatedAt = updatedAt)

    @Test
    fun `write then read gives identical documents`() = runBlocking {
        val store = FakeSyncStore()
        val (repo, db) = newRepo(store)
        try {
            val original = doc("map-a", "Peta A", 5_000L)
            repo.saveDocument(original)
            repo.syncNow().getOrThrow()

            val (otherRepo, otherDb) = newRepo(store)
            try {
                otherRepo.syncNow().getOrThrow()
                val restored = otherRepo.getDocument("map-a")
                assertNotNull(restored)
                assertEquals(original.title, restored!!.title)
                assertEquals(original.nodes.size, restored.nodes.size)
                assertEquals(original.rootNodeId, restored.rootNodeId)
            } finally {
                otherDb.close()
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `deleted map stays deleted on another device and is not resurrected`() = runBlocking {
        val store = FakeSyncStore()
        val (deviceA, dbA) = newRepo(store)
        val (deviceB, dbB) = newRepo(store)
        try {
            deviceA.saveDocument(doc("map-x", "Akan Dihapus", 5_000L))
            deviceA.syncNow().getOrThrow()
            deviceB.syncNow().getOrThrow()
            assertNotNull(deviceB.getDocument("map-x"))

            deviceA.deleteMap("map-x")
            deviceA.syncNow().getOrThrow()
            assertTrue("berkas harus memuat penanda hapus", store.text!!.contains("\"deleted\": true"))

            deviceB.syncNow().getOrThrow()
            assertNull(deviceB.getDocument("map-x"))

            // Sinkron ulang tidak membangkitkan kembali peta yang sudah dihapus.
            deviceB.syncNow().getOrThrow()
            deviceA.syncNow().getOrThrow()
            assertNull(deviceA.getDocument("map-x"))
            assertNull(deviceB.getDocument("map-x"))
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    @Test
    fun `same map edited on two devices keeps the version with larger updatedAt`() = runBlocking {
        val store = FakeSyncStore()
        val (deviceA, dbA) = newRepo(store)
        val (deviceB, dbB) = newRepo(store)
        try {
            val base = doc("map-shared", "Awal", 1_000L)
            deviceA.saveDocument(base)
            deviceA.syncNow().getOrThrow()
            deviceB.syncNow().getOrThrow()

            deviceA.saveDocument(base.copy(title = "Versi A", updatedAt = 2_000L))
            deviceA.syncNow().getOrThrow()
            deviceB.saveDocument(base.copy(title = "Versi B", updatedAt = 3_000L))
            deviceB.syncNow().getOrThrow()
            deviceA.syncNow().getOrThrow()

            assertEquals("Versi B", deviceA.getDocument("map-shared")?.title)
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    @Test
    fun `unreadable or invalid device file is never overwritten`() = runBlocking {
        val broken = "{\"schemaVersion\":1,\"records\":[{"
        val store = FakeSyncStore(text = broken)
        val (repo, db) = newRepo(store)
        try {
            repo.saveDocument(doc("map-keep", "Harus Aman", 4_000L))
            val result = repo.syncNow()
            assertTrue(result.isFailure)
            assertEquals(broken, store.text)
        } finally {
            db.close()
        }

        val emptyStore = FakeSyncStore(text = "")
        val (emptyRepo, emptyDb) = newRepo(emptyStore)
        try {
            assertTrue(emptyRepo.syncNow().isFailure)
            assertEquals("", emptyStore.text)
        } finally {
            emptyDb.close()
        }

        val unreadableStore = FakeSyncStore(failRead = true)
        val (unreadableRepo, unreadableDb) = newRepo(unreadableStore)
        try {
            assertTrue(unreadableRepo.syncNow().isFailure)
            assertNull(unreadableStore.text)
        } finally {
            unreadableDb.close()
        }
    }

    @Test
    fun `device record with invalid document is refused and file is left alone`() = runBlocking {
        val text = SyncFileCodec.encode(
            listOf(SyncRecord(id = "map-bad", value = "{\"foo\":1}", updatedAtMillis = 9_000L))
        )
        val store = FakeSyncStore(text = text)
        val (repo, db) = newRepo(store)
        try {
            assertTrue(repo.syncNow().isFailure)
            assertEquals(text, store.text)
        } finally {
            db.close()
        }
    }

    @Test
    fun `codec write then read is stable`() {
        val records = listOf(
            SyncRecord("a", "{\"id\":\"a\",\"title\":\"A\"}", 10L),
            SyncRecord("b", "", 20L, deleted = true)
        )
        val text = SyncFileCodec.encode(records)
        val decoded = SyncFileCodec.decode(text)
        assertEquals(2, decoded.size)
        assertTrue(decoded[1].deleted)
        assertEquals(text, SyncFileCodec.encode(decoded))
    }

    @Test
    fun `last-write-wins merge keeps newest per id and never drops one side`() {
        val local = listOf(
            SyncRecord("a", "local-a", 100L),
            SyncRecord("b", "local-b", 300L),
            SyncRecord("c", "", 50L, deleted = true)
        )
        val device = listOf(
            SyncRecord("a", "device-a", 200L),
            SyncRecord("b", "device-b", 100L),
            SyncRecord("d", "device-d", 10L)
        )
        val merged = SyncMerge.mergeLastWriteWins(local, device).associateBy { it.id }
        assertEquals(4, merged.size)
        assertEquals("device-a", merged.getValue("a").value)
        assertEquals("local-b", merged.getValue("b").value)
        assertEquals("device-d", merged.getValue("d").value)
        assertTrue(merged.getValue("c").deleted)
        assertFalse(merged.getValue("a").deleted)
    }
}
