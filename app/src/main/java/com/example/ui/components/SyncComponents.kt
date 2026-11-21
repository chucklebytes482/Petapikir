package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GlobalSyncStatus
import com.example.sync.SyncFolderInfo
import com.example.sync.SyncSettings
import com.example.ui.icons.AppIcons
import com.example.ui.theme.BrandSage
import com.example.ui.theme.DarkBrandSage
import com.example.ui.theme.SyncAmber
import com.example.ui.theme.SyncAmberDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSyncBottomSheet(
    syncSettings: SyncSettings,
    syncStatus: GlobalSyncStatus,
    activeFolderInfo: SyncFolderInfo?,
    onDismiss: () -> Unit,
    onConnectDrive: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        DriveSyncSheetContent(
            syncSettings = syncSettings,
            syncStatus = syncStatus,
            activeFolderInfo = activeFolderInfo,
            onConnectDrive = onConnectDrive,
            onSyncNow = onSyncNow,
            onDisconnect = onDisconnect
        )
    }
}

@Composable
fun DriveSyncSheetContent(
    syncSettings: SyncSettings,
    syncStatus: GlobalSyncStatus,
    activeFolderInfo: SyncFolderInfo?,
    onConnectDrive: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val sageColor = if (isDark) DarkBrandSage else BrandSage
    val amberColor = if (isDark) SyncAmberDark else SyncAmber

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (syncSettings.isDriveConnected) {
                            sageColor.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (syncSettings.isDriveConnected) AppIcons.CloudDone else AppIcons.CloudSync,
                    contentDescription = null,
                    tint = if (syncSettings.isDriveConnected) sageColor else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (syncSettings.isDriveConnected) {
                        stringResource(R.string.drive_sheet_connected_title)
                    } else {
                        stringResource(R.string.drive_sheet_disconnected_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (syncSettings.isDriveConnected) {
                        syncSettings.connectedEmail ?: stringResource(R.string.home_drive_connected)
                    } else {
                        stringResource(R.string.drive_status_not_connected)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (syncSettings.isDriveConnected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drive_sync_status_card"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusText = when (syncStatus) {
                        is GlobalSyncStatus.Syncing -> stringResource(R.string.drive_status_syncing)
                        is GlobalSyncStatus.Synced -> "${stringResource(R.string.drive_status_synced)} • ${formatSyncTime(syncStatus.timestamp)}"
                        is GlobalSyncStatus.OfflineQueued -> stringResource(R.string.drive_status_offline)
                        is GlobalSyncStatus.NeedsAuth -> syncStatus.message ?: stringResource(R.string.drive_status_needs_auth)
                        is GlobalSyncStatus.Error -> syncStatus.message
                        is GlobalSyncStatus.NotConnected -> stringResource(R.string.home_drive_connected)
                    }
                    val dotColor = when (syncStatus) {
                        is GlobalSyncStatus.Synced -> sageColor
                        is GlobalSyncStatus.Syncing -> MaterialTheme.colorScheme.primary
                        is GlobalSyncStatus.OfflineQueued, is GlobalSyncStatus.NeedsAuth -> amberColor
                        is GlobalSyncStatus.Error -> MaterialTheme.colorScheme.error
                        is GlobalSyncStatus.NotConnected -> sageColor
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (activeFolderInfo != null) {
                        val folderText = if (activeFolderInfo.isSharedWithMe) {
                            val owner = activeFolderInfo.ownerName
                            if (!owner.isNullOrBlank()) {
                                "Folder: ${activeFolderInfo.name} ($owner)"
                            } else {
                                "Folder: ${activeFolderInfo.name}"
                            }
                        } else {
                            "Folder: ${activeFolderInfo.name}"
                        }
                        Text(
                            text = folderText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val isSyncing = syncStatus is GlobalSyncStatus.Syncing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("drive_disconnect_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.drive_disconnect),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("drive_sync_now_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.drive_sync_now),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            val errorMessage = when (syncStatus) {
                is GlobalSyncStatus.Error -> syncStatus.message
                is GlobalSyncStatus.NeedsAuth -> syncStatus.message
                else -> null
            }
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onConnectDrive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("drive_connect_google_button")
            ) {
                Icon(
                    imageVector = AppIcons.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.drive_connect_cta))
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Baru saja"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Baru saja"
        diff < 3_600_000L -> "${diff / 60_000L} mnt lalu"
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
