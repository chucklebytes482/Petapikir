package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.backup.BackupSettings
import com.example.data.LocalBackupStatus
import com.example.data.MindMapSummary
import com.example.model.SyncState
import com.example.ui.components.LocalBackupSheetContent
import com.example.ui.screens.MindMapLibraryScreen
import com.example.ui.theme.PetaPikirTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeAndLocalBackupUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `home screen renders Priority 1 local backup card strictly above Priority 2 web guide card with default Documents path`() {
        var openedBackupSheet = false
        var openedWebGuide = false

        val sampleSummary = MindMapSummary(
            id = "map_1",
            title = "Rencana Aplikasi",
            nodeCount = 7,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            backupState = SyncState.SYNCED,
            backupLocation = "/storage/emulated/0/Documents/Rencana Aplikasi.json",
            rootTitle = "Rencana Aplikasi",
            previewChildTitles = listOf("UI", "Backup", "CI/CD")
        )

        composeTestRule.setContent {
            PetaPikirTheme {
                var query by remember { mutableStateOf("") }
                MindMapLibraryScreen(
                    summaries = listOf(sampleSummary),
                    searchQuery = query,
                    backupStatus = LocalBackupStatus.BackedUp(1_700_000_000_000L, 1),
                    backupSettings = BackupSettings(),
                    onSearchQueryChange = { query = it },
                    onOpenMap = {},
                    onCreateNewMap = {},
                    onCreateSampleMap = {},
                    onDuplicateMap = {},
                    onDeleteMap = {},
                    onOpenBackupSheet = { openedBackupSheet = true },
                    onOpenWebGuide = { openedWebGuide = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        val backupCard = composeTestRule.onNodeWithTag("home_backup_status_card")
        val webGuideCard = composeTestRule.onNodeWithTag("home_web_guide_card")

        backupCard.assertIsDisplayed()
        webGuideCard.assertIsDisplayed()
        composeTestRule.onNodeWithText("Cadangan Perangkat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Panduan & Arti Ikon").assertIsDisplayed()
        composeTestRule.onNodeWithText("/storage/emulated/0/Documents/").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_drive_status_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("theme_selector").assertDoesNotExist()

        val backupBounds = backupCard.getUnclippedBoundsInRoot()
        val guideBounds = webGuideCard.getUnclippedBoundsInRoot()
        assertTrue(
            "Priority #1 Pencadangan Lokal (${backupBounds.top}) must appear above Priority #2 Web Panduan (${guideBounds.top})",
            backupBounds.bottom <= guideBounds.top
        )

        backupCard.performClick()
        webGuideCard.performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedBackupSheet)
        assertTrue(openedWebGuide)

        // Search input + clear button check
        composeTestRule.onNodeWithTag("search_maps_input").performTextInput("Rencana")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_search_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_search_button").assertDoesNotExist()
    }

    @Test
    fun `local backup sheet supports picking folder and saving custom subpath without manual reset, import, or backup-all buttons`() {
        var pickedFolder = false
        var savedSubPath: String? = null

        composeTestRule.setContent {
            PetaPikirTheme {
                LocalBackupSheetContent(
                    backupSettings = BackupSettings(),
                    backupStatus = LocalBackupStatus.Ready,
                    onPickDocumentsFolder = { pickedFolder = true },
                    onSaveSubPath = { savedSubPath = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("local_backup_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_pick_folder_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("backup_subpath_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("backup_subpath_input").performTextInput("Catatan/Ide")
        composeTestRule.onNodeWithTag("backup_save_path_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("backup_reset_default_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("backup_all_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("backup_import_button").assertDoesNotExist()
        composeTestRule.waitForIdle()

        assertTrue(pickedFolder)
        assertEquals("Catatan/Ide", savedSubPath)
    }
}
