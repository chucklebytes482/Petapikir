package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.NodeColor
import com.example.ui.icons.AppIcons

fun normalizeWebLink(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorSheet(
    sheetTitle: String,
    initialTitle: String,
    initialLink: String,
    initialColorKey: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, link: String, colorKey: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var link by remember(initialLink) { mutableStateOf(initialLink) }
    var colorKey by remember(initialColorKey) { mutableStateOf(initialColorKey) }
    val focusRequester = remember { FocusRequester() }
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = sheetTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Judul Node") },
                placeholder = { Text("Tulis ide atau subtopik…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("node_title_input")
            )

            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Tautan Web (opsional)") },
                placeholder = { Text("https://contoh.com") },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Link,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (link.isNotEmpty()) {
                        IconButton(onClick = { link = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Hapus tautan"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (title.isNotBlank()) {
                            onConfirm(title, normalizeWebLink(link), colorKey)
                        }
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("node_link_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Warna Cabang",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NodeColor.entries.forEach { option ->
                        val accent = option.accent(isDark)
                        val isSelected = colorKey == option.key
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    }
                                )
                                .clickable { colorKey = option.key }
                                .testTag("color_swatch_${option.key}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = option.label,
                                    tint = if (isDark) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        onConfirm(
                            title.ifBlank { "Cabang Baru" },
                            normalizeWebLink(link),
                            colorKey
                        )
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("save_node_button")
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}
