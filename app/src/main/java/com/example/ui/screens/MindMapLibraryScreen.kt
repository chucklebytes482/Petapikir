package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GlobalSyncStatus
import com.example.data.MindMapSummary
import com.example.model.SyncState
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
fun MindMapLibraryScreen(
    summaries: List<MindMapSummary>,
    searchQuery: String,
    syncStatus: GlobalSyncStatus,
    syncSettings: SyncSettings,
    onSearchQueryChange: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onCreateNewMap: (String) -> Unit,
    onCreateSampleMap: () -> Unit,
    onDuplicateMap: (String) -> Unit,
    onDeleteMap: (String) -> Unit,
    onOpenSyncSheet: () -> Unit,
    onOpenWebGuide: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<MindMapSummary?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.MindMapRadiant,
                                contentDescription = stringResource(R.string.app_name),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_map_cta)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_map_fab")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 600.dp -> 2
                else -> 1
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Priority #1: Kartu Status Akun (Google Drive)
                item(
                    key = "home_drive_status_card",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        DriveAccountStatusCard(
                            syncSettings = syncSettings,
                            syncStatus = syncStatus,
                            onClick = onOpenSyncSheet,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        )
                    }
                }

                // Priority #2: Kartu Web Panduan (GitHub Pages)
                item(
                    key = "home_web_guide_card",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        WebGuideCard(
                            onClick = onOpenWebGuide,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp)
                        )
                    }
                }

                // Search bar (when maps exist or search is active)
                if (summaries.isNotEmpty() || searchQuery.isNotEmpty()) {
                    item(
                        key = "home_search_bar",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Bersihkan pencarian")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 640.dp)
                                    .testTag("search_maps_input")
                            )
                        }
                    }
                }

                if (summaries.isEmpty()) {
                    item(
                        key = "empty_library_state",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        EmptyLibraryState(
                            isSearching = searchQuery.isNotBlank(),
                            onCreateFirst = { showCreateDialog = true },
                            onLoadSample = onCreateSampleMap,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp, bottom = 24.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                } else {
                    items(summaries, key = { it.id }) { item ->
                        MindMapCard(
                            summary = item,
                            onClick = { onOpenMap(item.id) },
                            onDuplicate = { onDuplicateMap(item.id) },
                            onDelete = { deleteCandidate = item }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateMapDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title ->
                showCreateDialog = false
                onCreateNewMap(title)
            }
        )
    }

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Hapus Peta Pikiran?") },
            text = { Text("\"${target.title}\" akan dihapus.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteCandidate = null
                        onDeleteMap(target.id)
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun DriveAccountStatusCard(
    syncSettings: SyncSettings,
    syncStatus: GlobalSyncStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val sageColor = if (isDark) DarkBrandSage else BrandSage
    val amberColor = if (isDark) SyncAmberDark else SyncAmber

    val accentColor = when {
        !syncSettings.isDriveConnected -> MaterialTheme.colorScheme.primary
        syncStatus is GlobalSyncStatus.Error -> MaterialTheme.colorScheme.error
        syncStatus is GlobalSyncStatus.OfflineQueued || syncStatus is GlobalSyncStatus.NeedsAuth -> amberColor
        else -> sageColor
    }

    val subtitleText = if (syncSettings.isDriveConnected) {
        syncSettings.connectedEmail ?: stringResource(R.string.home_drive_connected)
    } else {
        stringResource(R.string.home_drive_not_connected)
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("home_drive_status_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (syncStatus is GlobalSyncStatus.Syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = if (syncSettings.isDriveConnected) AppIcons.CloudDone else AppIcons.CloudOff,
                        contentDescription = stringResource(R.string.home_drive_card_title),
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_drive_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncSettings.isDriveConnected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun WebGuideCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("home_web_guide_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.OpenInBrowser,
                    contentDescription = stringResource(R.string.home_web_guide_title),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_web_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.home_web_guide_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MindMapCard(
    summary: MindMapSummary,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val sage = if (isDark) DarkBrandSage else BrandSage
    val amber = if (isDark) SyncAmberDark else SyncAmber

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("mind_map_card_${summary.id}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniRadiantPreview(
                branchCount = summary.previewChildTitles.size.coerceAtLeast(3),
                accentColor = MaterialTheme.colorScheme.primary,
                surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (summary.previewChildTitles.isNotEmpty()) {
                    Text(
                        text = summary.previewChildTitles.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${summary.nodeCount} node",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    val (syncLabel, syncColor) = when (summary.syncState) {
                        SyncState.SYNCED -> "Tersinkron" to sage
                        SyncState.PENDING_UPLOAD -> "Antre" to amber
                        SyncState.LOCAL_ONLY -> "Lokal" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(syncColor)
                        )
                        Text(
                            text = syncLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = syncColor
                        )
                    }

                    Text(
                        text = "• ${formatRelativeDate(summary.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_menu_${summary.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Opsi peta pikiran",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Duplikat") },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniRadiantPreview(
    branchCount: Int,
    accentColor: Color,
    surfaceColor: Color
) {
    Surface(
        modifier = Modifier.size(54.dp),
        shape = RoundedCornerShape(14.dp),
        color = surfaceColor
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val count = branchCount.coerceIn(3, 5)
            val radius = minOf(w, h) * 0.36f
            val nodeW = w * 0.24f
            val nodeH = h * 0.16f

            for (i in 0 until count) {
                val angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / count)
                val nx = cx + (kotlin.math.cos(angle) * radius).toFloat()
                val ny = cy + (kotlin.math.sin(angle) * radius).toFloat()
                val path = Path().apply {
                    moveTo(cx, cy)
                    quadraticTo(
                        (cx + nx) / 2f,
                        cy,
                        nx,
                        ny
                    )
                }
                drawPath(
                    path = path,
                    color = accentColor.copy(alpha = 0.55f),
                    style = Stroke(width = 2.6f, cap = StrokeCap.Round)
                )
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.85f),
                    topLeft = Offset(nx - nodeW / 2f, ny - nodeH / 2f),
                    size = Size(nodeW, nodeH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            val rootW = w * 0.32f
            val rootH = h * 0.22f
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(cx - rootW / 2f, cy - rootH / 2f),
                size = Size(rootW, rootH),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(
    isSearching: Boolean,
    onCreateFirst: () -> Unit,
    onLoadSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            drawCircle(color = primaryContainer.copy(alpha = 0.55f), radius = w * 0.48f)

            val branchAngles = listOf(-1.57, -0.45, 0.72, 2.38, -2.72)
            val radius = w * 0.33f
            val childW = w * 0.20f
            val childH = h * 0.12f

            for (angle in branchAngles) {
                val nx = cx + (kotlin.math.cos(angle) * radius).toFloat()
                val ny = cy + (kotlin.math.sin(angle) * radius).toFloat()
                val p = Path().apply {
                    moveTo(cx, cy)
                    quadraticTo((cx + nx) / 2f, cy + (ny - cy) * 0.15f, nx, ny)
                }
                drawPath(p, color = primary.copy(alpha = 0.5f), style = Stroke(width = 3.4f, cap = StrokeCap.Round))
                drawRoundRect(
                    color = primary.copy(alpha = 0.84f),
                    topLeft = Offset(nx - childW / 2f, ny - childH / 2f),
                    size = Size(childW, childH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            val rootW = w * 0.28f
            val rootH = h * 0.17f
            drawRoundRect(
                color = primary,
                topLeft = Offset(cx - rootW / 2f, cy - rootH / 2f),
                size = Size(rootW, rootH),
                cornerRadius = CornerRadius(12f, 12f)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = if (isSearching) "Tidak ditemukan" else stringResource(R.string.empty_library_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        if (!isSearching) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onCreateFirst,
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 320.dp)
                    .height(48.dp)
                    .testTag("empty_create_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create_first_map))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onLoadSample,
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 320.dp)
                    .height(48.dp)
                    .testTag("empty_sample_button")
            ) {
                Text(stringResource(R.string.create_sample_map))
            }
        }
    }
}

@Composable
private fun CreateMapDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Peta Pikiran Baru") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Topik utama…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_map_title_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title.ifBlank { "Peta Pikiran Baru" }) },
                modifier = Modifier.testTag("confirm_create_map_button")
            ) {
                Text("Buat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

private fun formatRelativeDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Baru saja"
        diff < 3_600_000L -> "${diff / 60_000L} mnt"
        diff < 86_400_000L -> "${diff / 3_600_000L} jam"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
