package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
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
import com.example.ui.MindMapViewModel
import com.example.ui.UiEvent
import com.example.ui.components.DriveSyncBottomSheet
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
    val activity = remember(context) { context.findActivity() }
    val webGuideUrl = stringResource(R.string.web_guide_url)

    val summaries by viewModel.filteredSummaries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeMapId by viewModel.activeMapId.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val activeFolderInfo by viewModel.activeFolderInfo.collectAsStateWithLifecycle()
    val syncSettings by viewModel.syncSettings.collectAsStateWithLifecycle()

    var showSyncSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val fitTriggerFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.onAuthIntentResult(activity, activityResult.data)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.LaunchIntentSender -> {
                    val request = IntentSenderRequest.Builder(event.result.pendingIntent.intentSender).build()
                    authResolutionLauncher.launch(request)
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
                    syncStatus = syncStatus,
                    syncSettings = syncSettings,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onOpenMap = viewModel::openMap,
                    onCreateNewMap = viewModel::createNewMap,
                    onCreateSampleMap = viewModel::createSampleMap,
                    onDuplicateMap = viewModel::duplicateMap,
                    onDeleteMap = viewModel::deleteMap,
                    onOpenSyncSheet = { showSyncSheet = true },
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

    if (showSyncSheet) {
        DriveSyncBottomSheet(
            syncSettings = syncSettings,
            syncStatus = syncStatus,
            activeFolderInfo = activeFolderInfo,
            onDismiss = { showSyncSheet = false },
            onConnectDrive = {
                showSyncSheet = false
                viewModel.connectGoogleDrive(activity)
            },
            onSyncNow = viewModel::syncNow,
            onDisconnect = {
                showSyncSheet = false
                viewModel.disconnectDrive()
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
