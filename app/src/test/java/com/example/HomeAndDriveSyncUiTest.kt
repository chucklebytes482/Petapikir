package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.GlobalSyncStatus
import com.example.data.MindMapSummary
import com.example.model.SyncState
import com.example.sync.SyncFolderInfo
import com.example.sync.SyncSettings
import com.example.ui.components.DriveSyncSheetContent
import com.example.ui.screens.MindMapLibraryScreen
import com.example.ui.theme.PetaPikirTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeAndDriveSyncUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `home screen renders priority account card and web guide card without per-card sync buttons`() {
        var openedSyncSheet = false
        var openedWebGuide = false

        val sampleSummary = MindMapSummary(
            id = "map_1",
            title = "Rencana Aplikasi",
            nodeCount = 7,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            syncState = SyncState.SYNCED,
            driveFileId = "drive_1",
            rootTitle = "Rencana Aplikasi",
            previewChildTitles = listOf("UI", "Sync", "CI/CD")
        )

        composeTestRule.setContent {
            PetaPikirTheme {
                MindMapLibraryScreen(
                    summaries = listOf(sampleSummary),
                    searchQuery = "",
                    syncStatus = GlobalSyncStatus.Synced(1_700_000_000_000L),
                    syncSettings = SyncSettings(
                        isDriveConnected = true,
                        connectedEmail = "chucklebytes482@gmail.com"
                    ),
                    onSearchQueryChange = {},
                    onOpenMap = {},
                    onCreateNewMap = {},
                    onCreateSampleMap = {},
                    onDuplicateMap = {},
                    onDeleteMap = {},
                    onOpenSyncSheet = { openedSyncSheet = true },
                    onOpenWebGuide = { openedWebGuide = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_drive_status_card").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("home_web_guide_card").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("sync_map_map_1").assertDoesNotExist()
        composeTestRule.waitForIdle()

        assertTrue(openedSyncSheet)
        assertTrue(openedWebGuide)
    }

    @Test
    fun `connected drive sync sheet displays disconnect and sync buttons side-by-side without auto-sync switch`() {
        var disconnected = false
        var synced = false

        composeTestRule.setContent {
            PetaPikirTheme {
                DriveSyncSheetContent(
                    syncSettings = SyncSettings(
                        isDriveConnected = true,
                        connectedEmail = "chucklebytes482@gmail.com"
                    ),
                    syncStatus = GlobalSyncStatus.Synced(1_700_000_000_000L),
                    activeFolderInfo = SyncFolderInfo(id = "f1", name = "PetaPikir", isSharedWithMe = false),
                    onConnectDrive = {},
                    onSyncNow = { synced = true },
                    onDisconnect = { disconnected = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drive_sync_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drive_disconnect_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("drive_sync_now_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("auto_sync_switch").assertDoesNotExist()
        composeTestRule.waitForIdle()

        assertTrue(disconnected)
        assertTrue(synced)
    }

    @Test
    fun `unauthenticated drive sync sheet shows connect button only`() {
        var connected = false

        composeTestRule.setContent {
            PetaPikirTheme {
                DriveSyncSheetContent(
                    syncSettings = SyncSettings(isDriveConnected = false),
                    syncStatus = GlobalSyncStatus.NotConnected,
                    activeFolderInfo = null,
                    onConnectDrive = { connected = true },
                    onSyncNow = {},
                    onDisconnect = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drive_connect_google_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("drive_disconnect_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("drive_sync_now_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("auto_sync_switch").assertDoesNotExist()
        composeTestRule.waitForIdle()

        assertTrue(connected)
    }
}
