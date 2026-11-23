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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.backup.BackupPathValidator
import com.example.backup.BackupSettings
import com.example.data.LocalBackupStatus
import com.example.ui.icons.AppIcons
import com.example.ui.theme.BrandSage
import com.example.ui.theme.DarkBrandSage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBackupBottomSheet(
    backupSettings: BackupSettings,
    backupStatus: LocalBackupStatus,
    onDismiss: () -> Unit,
    onPickDocumentsFolder: () -> Unit,
    onSaveSubPath: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LocalBackupSheetContent(
            backupSettings = backupSettings,
            backupStatus = backupStatus,
            onPickDocumentsFolder = onPickDocumentsFolder,
            onSaveSubPath = onSaveSubPath
        )
    }
}

@Composable
fun LocalBackupSheetContent(
    backupSettings: BackupSettings,
    backupStatus: LocalBackupStatus,
    onPickDocumentsFolder: () -> Unit,
    onSaveSubPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val sageColor = if (isDark) DarkBrandSage else BrandSage

    var subPathInput by remember { mutableStateOf(backupSettings.relativeSubPath) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backupSettings.relativeSubPath) {
        subPathInput = backupSettings.relativeSubPath
        validationError = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                    .background(sageColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.FolderBackup,
                    contentDescription = null,
                    tint = sageColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.backup_sheet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("local_backup_status_card"),
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
                val statusText = when (backupStatus) {
                    is LocalBackupStatus.Ready -> stringResource(R.string.backup_status_ready)
                    is LocalBackupStatus.BackingUp -> stringResource(R.string.backup_status_backing_up)
                    is LocalBackupStatus.BackedUp ->
                        "${stringResource(R.string.backup_status_backed_up)} (${backupStatus.count}) • ${formatBackupTime(backupStatus.timestamp)}"
                    is LocalBackupStatus.Error -> backupStatus.message
                }
                val dotColor = when (backupStatus) {
                    is LocalBackupStatus.Ready, is LocalBackupStatus.BackedUp -> sageColor
                    is LocalBackupStatus.BackingUp -> MaterialTheme.colorScheme.primary
                    is LocalBackupStatus.Error -> MaterialTheme.colorScheme.error
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

                Text(
                    text = "${stringResource(R.string.backup_active_path_label)}: ${backupSettings.displayPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("local_backup_active_path_text")
                )
            }
        }

        OutlinedButton(
            onClick = onPickDocumentsFolder,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("backup_pick_folder_button")
        ) {
            Icon(
                imageVector = AppIcons.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.backup_pick_folder_cta),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = subPathInput,
                onValueChange = {
                    subPathInput = it
                    validationError = null
                },
                label = { Text(stringResource(R.string.backup_subpath_label)) },
                placeholder = { Text(stringResource(R.string.backup_subpath_placeholder)) },
                prefix = { Text("Documents/") },
                isError = validationError != null,
                supportingText = {
                    val previewText = validationError ?: run {
                        BackupPathValidator.normalizeSubPath(subPathInput)
                            .map { BackupPathValidator.toDisplayPath(it) }
                            .getOrElse { it.message ?: "" }
                    }
                    Text(previewText)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_subpath_input")
            )

            Button(
                onClick = {
                    val check = BackupPathValidator.normalizeSubPath(subPathInput)
                    check.onSuccess {
                        validationError = null
                        onSaveSubPath(subPathInput)
                    }.onFailure { err ->
                        validationError = err.message ?: "Jalur tidak valid."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("backup_save_path_button")
            ) {
                Text(
                    text = stringResource(R.string.backup_save_path_cta),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatBackupTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Baru saja"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Baru saja"
        diff < 3_600_000L -> "${diff / 60_000L} mnt lalu"
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
