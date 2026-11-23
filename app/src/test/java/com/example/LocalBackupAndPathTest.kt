package com.example

import com.example.backup.BackupPathValidator
import com.example.backup.BackupSettings
import com.example.backup.DirectoryLocalBackupStorage
import com.example.data.MindMapJsonCodec
import com.example.model.MindMapTreeOps
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupAndPathTest {

    @Test
    fun `default path targets Documents root directly without automatic subfolder`() {
        val defaultSettings = BackupSettings()
        assertEquals("/storage/emulated/0/Documents/", defaultSettings.displayPath)
        assertEquals("Documents/", BackupPathValidator.toMediaStoreRelativePath(defaultSettings.relativeSubPath))
        assertTrue(defaultSettings.isDefaultPath)
        assertFalse(defaultSettings.displayPath.contains("PetaPikir", ignoreCase = true))
    }

    @Test
    fun `validator accepts flexible subpaths inside Documents and strips full prefix cleanly`() {
        val emptyResult = BackupPathValidator.normalizeSubPath("")
        assertEquals("", emptyResult.getOrThrow())
        assertEquals("/storage/emulated/0/Documents/", BackupPathValidator.toDisplayPath(emptyResult.getOrThrow()))

        val subResult = BackupPathValidator.normalizeSubPath("Kerja/Catatan 2026")
        assertEquals("Kerja/Catatan 2026", subResult.getOrThrow())
        assertEquals("/storage/emulated/0/Documents/Kerja/Catatan 2026/", BackupPathValidator.toDisplayPath(subResult.getOrThrow()))
        assertEquals("Documents/Kerja/Catatan 2026/", BackupPathValidator.toMediaStoreRelativePath(subResult.getOrThrow()))

        val fullPrefixResult = BackupPathValidator.normalizeSubPath("/storage/emulated/0/Documents/Proyek/Alpha/")
        assertEquals("Proyek/Alpha", fullPrefixResult.getOrThrow())

        val rootPrefixOnly = BackupPathValidator.normalizeSubPath("/storage/emulated/0/Documents/")
        assertEquals("", rootPrefixOnly.getOrThrow())
    }

    @Test
    fun `validator rejects path traversal and paths outside Documents`() {
        assertTrue(BackupPathValidator.normalizeSubPath("../Download").isFailure)
        assertTrue(BackupPathValidator.normalizeSubPath("Kerja/../../DCIM").isFailure)
        assertTrue(BackupPathValidator.normalizeSubPath("/storage/emulated/0/Download/Peta").isFailure)
        assertTrue(BackupPathValidator.normalizeSubPath("Invalid:Name?").isFailure)
    }

    @Test
    fun `SAF tree document ID validation allows Documents root and nested folders only`() {
        assertEquals("", BackupPathValidator.parseDocumentsTreeUri("primary:Documents").getOrThrow())
        assertEquals("Arsip/Peta", BackupPathValidator.parseDocumentsTreeUri("primary:Documents/Arsip/Peta").getOrThrow())
        assertTrue(BackupPathValidator.parseDocumentsTreeUri("primary:Download").isFailure)
        assertTrue(BackupPathValidator.parseDocumentsTreeUri("primary:DCIM/Camera").isFailure)
        assertTrue(BackupPathValidator.parseDocumentsTreeUri(null).isFailure)
    }

    @Test
    fun `directory local backup saves directly in Documents root without automatic subfolder and handles rename and import`() = runTest {
        val tempDocumentsRoot = Files.createTempDirectory("petapikir_docs_test").toFile()
        try {
            val codec = MindMapJsonCodec()
            val storage = DirectoryLocalBackupStorage(tempDocumentsRoot, codec)
            val defaultSettings = BackupSettings()

            val original = MindMapTreeOps.createSampleProject()
            val firstWrite = storage.backupDocument(original, previousLocation = null, settings = defaultSettings).getOrThrow()

            val savedFile = File(firstWrite.locationKey)
            assertTrue("Backup JSON file should exist directly inside Documents root", savedFile.exists())
            assertEquals(tempDocumentsRoot.canonicalPath, savedFile.parentFile?.canonicalPath)
            assertEquals("Peluncuran Produk.json", savedFile.name)
            assertFalse("No automatic PetaPikir subfolder should be created", File(tempDocumentsRoot, "PetaPikir").exists())

            // Rename updates file cleanly without leaving orphaned duplicate
            val renamed = MindMapTreeOps.renameDocument(original, "Strategi Q4")
            val secondWrite = storage.backupDocument(renamed, previousLocation = firstWrite.locationKey, settings = defaultSettings).getOrThrow()
            val renamedFile = File(secondWrite.locationKey)
            assertTrue(renamedFile.exists())
            assertEquals("Strategi Q4.json", renamedFile.name)
            assertFalse("Old filename should be removed after rename", savedFile.exists())

            // Custom subfolder inside Documents
            val customSettings = BackupSettings(relativeSubPath = "Tim/Sprint")
            val subWrite = storage.backupDocument(renamed, previousLocation = null, settings = customSettings).getOrThrow()
            val subFile = File(subWrite.locationKey)
            assertTrue(subFile.exists())
            assertEquals(File(tempDocumentsRoot, "Tim/Sprint").canonicalPath, subFile.parentFile?.canonicalPath)

            // Import reads back valid MindMapDocument
            val imported = storage.importDocuments(customSettings).getOrThrow()
            assertEquals(1, imported.size)
            assertEquals(renamed.id, imported.first().id)
            assertEquals("Strategi Q4", imported.first().title)

            // Delete removes backup file
            storage.deleteBackup(subWrite.locationKey, renamed.title, customSettings)
            assertFalse(subFile.exists())
        } finally {
            tempDocumentsRoot.deleteRecursively()
        }
    }
}
