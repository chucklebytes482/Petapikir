package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.MindMapDocument
import com.example.model.MindMapNode
import com.example.model.MindMapTreeOps
import com.example.model.NodeColor
import com.example.model.OutlineListItem
import com.example.ui.icons.AppIcons

@Composable
fun MindMapListLayoutView(
    document: MindMapDocument,
    selectedNodeId: String?,
    isFullscreen: Boolean,
    isLandscape: Boolean = false,
    searchJumpCounter: Int = 0,
    onSelectNode: (String) -> Unit,
    onEditNode: (MindMapNode) -> Unit,
    onAddChild: (MindMapNode) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(document) {
        MindMapTreeOps.flattenVisibleOutline(document)
    }
    val listState = rememberLazyListState()
    var lastOrientation by remember { mutableStateOf(isLandscape) }
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(searchJumpCounter) {
        if (searchJumpCounter > 0 && selectedNodeId != null) {
            val targetIdx = items.indexOfFirst { it.node.id == selectedNodeId }
            if (targetIdx >= 0) {
                listState.animateScrollToItem(targetIdx)
            }
        }
    }

    LaunchedEffect(isLandscape) {
        if (lastOrientation != isLandscape) {
            lastOrientation = isLandscape
            if (selectedNodeId != null) {
                val targetIdx = items.indexOfFirst { it.node.id == selectedNodeId }
                if (targetIdx >= 0) {
                    listState.animateScrollToItem(targetIdx)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("mindmap_list_layout"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (isFullscreen) 68.dp else 102.dp,
                bottom = if (isFullscreen) 28.dp else 108.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = items,
                key = { it.node.id }
            ) { item ->
                OutlineNodeRow(
                    item = item,
                    isRoot = item.node.id == document.rootNodeId,
                    isSelected = item.node.id == selectedNodeId,
                    isDark = isDark,
                    onTap = { onSelectNode(item.node.id) },
                    onDoubleTap = {
                        onSelectNode(item.node.id)
                        onEditNode(item.node)
                    },
                    onAddChild = {
                        onSelectNode(item.node.id)
                        onAddChild(item.node)
                    },
                    onOpenLink = { onOpenLink(item.node.note) }
                )
            }
        }
    }
}

@Composable
private fun OutlineNodeRow(
    item: OutlineListItem,
    isRoot: Boolean,
    isSelected: Boolean,
    isDark: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onAddChild: () -> Unit,
    onOpenLink: () -> Unit
) {
    val node = item.node
    val nodeColor = NodeColor.fromKey(node.colorKey)
    val accent = nodeColor.accent(isDark)
    val surfaceColor = when {
        isRoot -> accent
        isSelected -> nodeColor.surface(isDark)
        else -> MaterialTheme.colorScheme.surface
    }
    val titleColor = if (isRoot) {
        if (isDark) Color(0xFF0A1219) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val linkColor = if (isRoot) {
        titleColor.copy(alpha = 0.90f)
    } else {
        accent
    }
    val borderColor = when {
        isSelected -> if (isRoot) MaterialTheme.colorScheme.secondary else accent
        isRoot -> accent
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val indentDp = (item.depth.coerceAtMost(5) * 18).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.depth > 0) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(width = 3.dp, height = 28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.65f))
            )
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .animateContentSize()
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(node.id) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { onDoubleTap() }
                    )
                }
                .testTag("list_node_${node.id}"),
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            tonalElevation = if (isSelected) 3.dp else 1.dp,
            shadowElevation = if (isSelected) 3.dp else 0.dp,
            border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (item.hasChildren || isRoot) 12.dp else 9.dp)
                        .clip(CircleShape)
                        .background(if (isRoot) titleColor.copy(alpha = 0.85f) else accent)
                )

                Text(
                    text = node.title,
                    style = if (isRoot) {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    color = titleColor,
                    maxLines = if (isSelected) Int.MAX_VALUE else 2,
                    overflow = if (isSelected) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (node.note.isNotBlank()) {
                    IconButton(
                        onClick = onOpenLink,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("list_open_link_${node.id}")
                    ) {
                        Icon(
                            imageVector = AppIcons.Link,
                            contentDescription = "Buka tautan web",
                            tint = linkColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onAddChild,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("list_add_child_${node.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Tambah sub-cabang",
                            tint = if (isRoot) titleColor else accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
