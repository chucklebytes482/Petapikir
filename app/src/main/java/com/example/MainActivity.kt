package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.backup.BackupPathValidator
import com.example.ui.MindMapViewModel
import com.example.ui.UiEvent
import com.example.ui.components.LocalBackupBottomSheet
import com.example.ui.screens.MindMapEditorScreen
import com.example.ui.screens.MindMapLibraryScreen
import com.example.ui.theme.PetaPikirTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PetaPikirTheme {
                PetaPikirApp()
            }
        }
    }
}

@Composable
fun PetaPikirApp(viewModel: MindMapViewModel = viewModel()) {
    val context = LocalContext.current
    val webGuideUrl = stringResource(R.string.web_guide_url)

    val summaries by viewModel.filteredSummaries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeMapId by viewModel.activeMapId.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val backupSettings by viewModel.backupSettings.collectAsStateWithLifecycle()

    var showBackupSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val fitTriggerFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val documentsFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            viewModel.selectBackupTreeUri(treeUri.toString(), treeDocId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.FitCanvasToScreen -> {
                    fitTriggerFlow.tryEmit(Unit)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = activeMapId,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screenTransition"
        ) { currentMapId ->
            if (currentMapId == null || editorState.document == null) {
                MindMapLibraryScreen(
                    summaries = summaries,
                    searchQuery = searchQuery,
                    backupStatus = backupStatus,
                    backupSettings = backupSettings,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onOpenMap = viewModel::openMap,
                    onCreateNewMap = viewModel::createNewMap,
                    onCreateSampleMap = viewModel::createSampleMap,
                    onDuplicateMap = viewModel::duplicateMap,
                    onDeleteMap = viewModel::deleteMap,
                    onOpenBackupSheet = { showBackupSheet = true },
                    onOpenWebGuide = {
                        val opened = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webGuideUrl)))
                        }.isSuccess
                        if (!opened) {
                            uiScope.launch {
                                snackbarHostState.showSnackbar("Tidak dapat membuka $webGuideUrl")
                            }
                        }
                    }
                )
            } else {
                MindMapEditorScreen(
                    editorState = editorState,
                    fitTriggerFlow = fitTriggerFlow,
                    onBack = viewModel::closeMap,
                    onSelectNode = viewModel::selectNode,
                    onAddChild = viewModel::addChildNode,
                    onUpdateNode = viewModel::updateNode,
                    onMoveSibling = viewModel::moveSibling,
                    onDeleteNode = viewModel::deleteNode,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onShowMessage = { msg ->
                        uiScope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (activeMapId != null) 78.dp else 16.dp)
        )
    }

    if (showBackupSheet) {
        LocalBackupBottomSheet(
            backupSettings = backupSettings,
            backupStatus = backupStatus,
            onDismiss = { showBackupSheet = false },
            onPickDocumentsFolder = {
                val initialDocumentsUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    BackupPathValidator.PRIMARY_DOCUMENTS_TREE_ID
                )
                documentsFolderPickerLauncher.launch(initialDocumentsUri)
            },
            onSaveSubPath = viewModel::saveCustomBackupSubPath
        )
    }
}
