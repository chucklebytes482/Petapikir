package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.R
import com.example.layout.PositionedNode
import com.example.layout.RadiantLayoutEngine
import com.example.model.MindMapNode
import com.example.model.MindMapTreeOps
import com.example.model.NodeColor
import com.example.ui.EditorUiState
import com.example.ui.components.MindMapListLayoutView
import com.example.ui.components.NodeEditorSheet
import com.example.ui.components.normalizeWebLink
import com.example.ui.icons.AppIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private sealed class NodeSheetMode {
    data class AddChild(val parentNode: MindMapNode) : NodeSheetMode()
    data class EditNode(val node: MindMapNode) : NodeSheetMode()
}

@Composable
fun MindMapEditorScreen(
    editorState: EditorUiState,
    fitTriggerFlow: SharedFlow<Unit>,
    onBack: () -> Unit,
    onSelectNode: (String?) -> Unit,
    onAddChild: (parentId: String, title: String, link: String, colorKey: String) -> Unit,
    onUpdateNode: (nodeId: String, title: String, link: String, colorKey: String) -> Unit,
    onMoveSibling: (String, Int) -> Unit,
    onDeleteNode: (nodeId: String, includeSubtree: Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val document = editorState.document ?: return
    val selectedNode = editorState.selectedNodeId?.let { id ->
        document.nodes.firstOrNull { it.id == id }
    }

    var sheetMode by remember { mutableStateOf<NodeSheetMode?>(null) }
    var deleteConfirmNode by remember { mutableStateOf<MindMapNode?>(null) }
    var isLandscapeFullscreen by rememberSaveable { mutableStateOf(false) }
    var isListLayoutMode by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var nodeSearchQuery by remember { mutableStateOf("") }
    var searchJumpCounter by remember { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }

    val nodeById = remember(document.nodes) { document.nodes.associateBy { it.id } }
    val searchMatches = remember(document, nodeSearchQuery, isSearchActive) {
        if (!isSearchActive) {
            emptyList()
        } else {
            val q = nodeSearchQuery.trim()
            val ordered = MindMapTreeOps.flattenVisibleOutline(document).map { it.node }
            if (q.isEmpty()) {
                ordered
            } else {
                ordered.filter { node ->
                    node.title.contains(q, ignoreCase = true) ||
                        node.note.contains(q, ignoreCase = true)
                }
            }
        }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val pxPerDp = with(density) { 1.dp.toPx() }
    val scope = rememberCoroutineScope()

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var hasInitializedCamera by remember(document.id) { mutableStateOf(false) }
    var cameraAnimJob by remember { mutableStateOf<Job?>(null) }

    val isDark = isSystemInDarkTheme()
    val dotGridColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.35f else 0.5f)

    fun openNodeWebLink(rawLink: String) {
        val url = normalizeWebLink(rawLink)
        if (url.isBlank()) return
        val opened = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }.isSuccess
        if (!opened) {
            onShowMessage("Tidak dapat membuka tautan: $url")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findEditorActivity()
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds()
    ) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val isLandscape = constraints.maxWidth > constraints.maxHeight
        val effectiveFullscreen = isLandscape && isLandscapeFullscreen

        val horizontalMarginDp = 28f
        val verticalReserveDp = if (effectiveFullscreen) 40f else 156f
        val availWDp = ((viewportW / pxPerDp) - horizontalMarginDp).coerceAtLeast(180f)
        val availHDp = ((viewportH / pxPerDp) - verticalReserveDp).coerceAtLeast(180f)
        val quantizedAspect = (((availWDp / availHDp).coerceIn(0.48f, 2.35f)) * 20f).roundToInt() / 20f

        val layout = remember(document, quantizedAspect) {
            RadiantLayoutEngine.compute(document, targetAspectRatio = quantizedAspect)
        }
        val selectedPositioned = editorState.selectedNodeId?.let { layout.nodeMap[it] }

        var wasLandscape by remember { mutableStateOf(isLandscape) }
        var lastFocusedOrientation by remember { mutableStateOf(isLandscape) }
        var lastFocusedFullscreen by remember { mutableStateOf(effectiveFullscreen) }
        var lastFocusedAspect by remember { mutableFloatStateOf(quantizedAspect) }

        val latestViewportW by rememberUpdatedState(viewportW)
        val latestViewportH by rememberUpdatedState(viewportH)
        val latestEffectiveFullscreen by rememberUpdatedState(effectiveFullscreen)
        val latestLayout by rememberUpdatedState(layout)

        fun exitFullscreenAndUnlockOrientation() {
            isLandscapeFullscreen = false
            context.findEditorActivity()?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        BackHandler {
            when {
                isSearchActive -> {
                    isSearchActive = false
                    nodeSearchQuery = ""
                }
                effectiveFullscreen -> exitFullscreenAndUnlockOrientation()
                else -> onBack()
            }
        }

        LaunchedEffect(isSearchActive) {
            if (isSearchActive) {
                searchFocusRequester.requestFocus()
            }
        }

        LaunchedEffect(effectiveFullscreen) {
            if (effectiveFullscreen && isSearchActive) {
                isSearchActive = false
                nodeSearchQuery = ""
            }
            val window = context.findEditorActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (effectiveFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        fun animateCameraToFit() {
            val vw = latestViewportW
            val vh = latestViewportH
            if (vw <= 0f || vh <= 0f) return
            val bounds = latestLayout.bounds
            val boundsW = (bounds.widthDp * pxPerDp).coerceAtLeast(100f)
            val boundsH = (bounds.heightDp * pxPerDp).coerceAtLeast(100f)
            val fitVerticalReserveDp = if (latestEffectiveFullscreen) 40f else 156f
            val availW = (vw - 28f * pxPerDp).coerceAtLeast(120f)
            val availH = (vh - fitVerticalReserveDp * pxPerDp).coerceAtLeast(120f)
            val targetZoom = min(availW / boundsW, availH / boundsH).coerceIn(0.28f, 1.35f)

            val cx = bounds.centerXDp * pxPerDp
            val cy = bounds.centerYDp * pxPerDp
            val targetPanX = vw / 2f - cx * targetZoom
            val targetPanY = (vh / 2f - 4f * pxPerDp) - cy * targetZoom

            cameraAnimJob?.cancel()
            cameraAnimJob = scope.launch {
                val startZoom = zoom
                val startPan = panOffset
                val anim = Animatable(0f)
                anim.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) {
                    val t = value
                    zoom = startZoom + (targetZoom - startZoom) * t
                    panOffset = Offset(
                        x = startPan.x + (targetPanX - startPan.x) * t,
                        y = startPan.y + (targetPanY - startPan.y) * t
                    )
                }
            }
        }

        fun animateCameraToNode(nodeId: String) {
            val vw = latestViewportW
            val vh = latestViewportH
            if (vw <= 0f || vh <= 0f) return
            val placed = latestLayout.nodeMap[nodeId] ?: return
            val targetZoom = zoom.coerceIn(0.85f, 1.35f)
            val cx = placed.centerXDp * pxPerDp
            val cy = placed.centerYDp * pxPerDp
            val targetPanX = vw / 2f - cx * targetZoom
            val targetPanY = vh / 2f - cy * targetZoom

            cameraAnimJob?.cancel()
            cameraAnimJob = scope.launch {
                val startZoom = zoom
                val startPan = panOffset
                val anim = Animatable(0f)
                anim.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) {
                    val t = value
                    zoom = startZoom + (targetZoom - startZoom) * t
                    panOffset = Offset(
                        x = startPan.x + (targetPanX - startPan.x) * t,
                        y = startPan.y + (targetPanY - startPan.y) * t
                    )
                }
            }
        }

        fun jumpToSearchedNode(node: MindMapNode) {
            onSelectNode(node.id)
            isSearchActive = false
            nodeSearchQuery = ""
            if (isListLayoutMode) {
                searchJumpCounter++
            } else {
                animateCameraToNode(node.id)
            }
        }

        LaunchedEffect(isLandscape) {
            if (wasLandscape && !isLandscape && isLandscapeFullscreen) {
                isLandscapeFullscreen = false
            }
            wasLandscape = isLandscape
        }

        LaunchedEffect(isLandscape, effectiveFullscreen, quantizedAspect, isListLayoutMode, viewportW, viewportH) {
            if (!hasInitializedCamera || isListLayoutMode) return@LaunchedEffect
            val orientationChanged = isLandscape != lastFocusedOrientation
            val fullscreenChanged = effectiveFullscreen != lastFocusedFullscreen
            val aspectChanged = kotlin.math.abs(quantizedAspect - lastFocusedAspect) >= 0.08f
            if (orientationChanged || fullscreenChanged || aspectChanged) {
                delay(120L)
                lastFocusedOrientation = isLandscape
                lastFocusedFullscreen = effectiveFullscreen
                lastFocusedAspect = quantizedAspect
                animateCameraToFit()
            }
        }

        LaunchedEffect(document.id, viewportW, viewportH) {
            if (!hasInitializedCamera && viewportW > 0f && viewportH > 0f) {
                hasInitializedCamera = true
                lastFocusedOrientation = isLandscape
                lastFocusedFullscreen = effectiveFullscreen
                lastFocusedAspect = quantizedAspect
                val bounds = layout.bounds
                val boundsW = (bounds.widthDp * pxPerDp).coerceAtLeast(100f)
                val boundsH = (bounds.heightDp * pxPerDp).coerceAtLeast(100f)
                val initVerticalReserveDp = if (effectiveFullscreen) 40f else 156f
                val availW = (viewportW - 28f * pxPerDp).coerceAtLeast(120f)
                val availH = (viewportH - initVerticalReserveDp * pxPerDp).coerceAtLeast(120f)
                val targetZoom = min(availW / boundsW, availH / boundsH).coerceIn(0.28f, 1.35f)
                zoom = targetZoom
                panOffset = Offset(
                    x = viewportW / 2f - bounds.centerXDp * pxPerDp * targetZoom,
                    y = (viewportH / 2f - 4f * pxPerDp) - bounds.centerYDp * pxPerDp * targetZoom
                )
            }
        }

        LaunchedEffect(fitTriggerFlow) {
            fitTriggerFlow.collect {
                animateCameraToFit()
            }
        }

        // Main Editor Content: Radiant Mind Map Canvas or Hierarchical List Layout
        AnimatedContent(
            targetState = isListLayoutMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "layoutModeContent",
            modifier = Modifier.fillMaxSize()
        ) { listMode ->
            if (listMode) {
                MindMapListLayoutView(
                    document = document,
                    selectedNodeId = editorState.selectedNodeId,
                    isFullscreen = effectiveFullscreen,
                    isLandscape = isLandscape,
                    searchJumpCounter = searchJumpCounter,
                    onSelectNode = { onSelectNode(it) },
                    onEditNode = { sheetMode = NodeSheetMode.EditNode(it) },
                    onAddChild = { sheetMode = NodeSheetMode.AddChild(it) },
                    onOpenLink = { openNodeWebLink(it) }
                )
            } else {
                // Architectural Dot-Grid + Gesture Surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onSelectNode(null) }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                val prevZoom = zoom
                                val nextZoom = (prevZoom * gestureZoom).coerceIn(0.25f, 2.5f)
                                val zoomFactor = nextZoom / prevZoom
                                zoom = nextZoom
                                panOffset = centroid - (centroid - panOffset) * zoomFactor + pan
                            }
                        }
                ) {
                    // Subtle architectural dot grid
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = (28f * pxPerDp * zoom).coerceAtLeast(14f)
                        val startX = ((panOffset.x % step) + step) % step
                        val startY = ((panOffset.y % step) + step) % step
                        val dotRadius = (1.35f * pxPerDp * zoom.coerceIn(0.7f, 1.4f))

                        var x = startX
                        while (x < size.width) {
                            var y = startY
                            while (y < size.height) {
                                drawCircle(
                                    color = dotGridColor,
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                                y += step
                            }
                            x += step
                        }
                    }

                    // World-space Radiant Mind Map Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = panOffset.x
                                translationY = panOffset.y
                                scaleX = zoom
                                scaleY = zoom
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        // Organic Radiant Cubic Bezier Connectors
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (edge in layout.edges) {
                                val sx = edge.startXDp * pxPerDp
                                val sy = edge.startYDp * pxPerDp
                                val c1x = edge.control1XDp * pxPerDp
                                val c1y = edge.control1YDp * pxPerDp
                                val c2x = edge.control2XDp * pxPerDp
                                val c2y = edge.control2YDp * pxPerDp
                                val ex = edge.endXDp * pxPerDp
                                val ey = edge.endYDp * pxPerDp

                                val branchColor = NodeColor.fromKey(edge.colorKey).accent(isDark)
                                val isHighlighted = edge.parentId == editorState.selectedNodeId ||
                                    edge.childId == editorState.selectedNodeId
                                val baseStrokeDp = if (edge.parentDepth == 0) 3.0f else 2.2f

                                val path = Path().apply {
                                    moveTo(sx, sy)
                                    cubicTo(c1x, c1y, c2x, c2y, ex, ey)
                                }

                                drawPath(
                                    path = path,
                                    color = branchColor.copy(alpha = if (isHighlighted) 0.94f else 0.54f),
                                    style = Stroke(
                                        width = (if (isHighlighted) baseStrokeDp + 1.1f else baseStrokeDp) * pxPerDp,
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }

                        // Positioned Radiant Mind Map Nodes
                        layout.nodes.forEach { placed ->
                            RadiantNodeCard(
                                placed = placed,
                                isRoot = placed.node.id == document.rootNodeId,
                                isSelected = placed.node.id == editorState.selectedNodeId,
                                isDark = isDark,
                                pxPerDp = pxPerDp,
                                onTap = { onSelectNode(placed.node.id) },
                                onDoubleTap = {
                                    onSelectNode(placed.node.id)
                                    sheetMode = NodeSheetMode.EditNode(placed.node)
                                },
                                onOpenLink = { openNodeWebLink(placed.node.note) }
                            )
                        }
                    }
                }
            }
        }

        // Top Floating Toolbar + Node Content Search (hides during landscape fullscreen)
        AnimatedVisibility(
            visible = !effectiveFullscreen,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (isSearchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    nodeSearchQuery = ""
                                },
                                modifier = Modifier.testTag("close_node_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Tutup pencarian node"
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (nodeSearchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_nodes_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = nodeSearchQuery,
                                    onValueChange = { nodeSearchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            searchMatches.firstOrNull()?.let { jumpToSearchedNode(it) }
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                        .testTag("node_search_input")
                                )
                            }

                            if (nodeSearchQuery.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(end = 2.dp)
                                ) {
                                    Text(
                                        text = "${searchMatches.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { nodeSearchQuery = "" },
                                    modifier = Modifier.testTag("clear_node_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Bersihkan pencarian node"
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("editor_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Kembali ke daftar"
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = document.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${if (isListLayoutMode) "List" else "Radiant"} • ${document.nodes.size} node",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onUndo,
                                enabled = editorState.canUndo,
                                modifier = Modifier.testTag("undo_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.Undo,
                                    contentDescription = stringResource(R.string.action_undo),
                                    tint = if (editorState.canUndo) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    }
                                )
                            }

                            IconButton(
                                onClick = onRedo,
                                enabled = editorState.canRedo,
                                modifier = Modifier.testTag("redo_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.Redo,
                                    contentDescription = stringResource(R.string.action_redo),
                                    tint = if (editorState.canRedo) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    }
                                )
                            }

                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("search_nodes_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.action_search_nodes),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (!isListLayoutMode) {
                                IconButton(
                                    onClick = { animateCameraToFit() },
                                    modifier = Modifier.testTag("fit_screen_button")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.FitScreen,
                                        contentDescription = stringResource(R.string.action_fit_screen),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (isLandscape) {
                                IconButton(
                                    onClick = { isLandscapeFullscreen = true },
                                    modifier = Modifier.testTag("fullscreen_button")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Fullscreen,
                                        contentDescription = stringResource(R.string.action_fullscreen),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isSearchActive) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        if (searchMatches.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.search_nodes_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("node_search_results_list")
                            ) {
                                items(searchMatches, key = { it.id }) { matchNode ->
                                    val accent = NodeColor.fromKey(matchNode.colorKey).accent(isDark)
                                    val parentName = matchNode.parentId?.let { nodeById[it]?.title }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .clickable { jumpToSearchedNode(matchNode) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                            .testTag("node_search_result_${matchNode.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = matchNode.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val subtitle = buildString {
                                                if (matchNode.id == document.rootNodeId) {
                                                    append("Node pusat")
                                                } else if (!parentName.isNullOrBlank()) {
                                                    append("di dalam \"$parentName\"")
                                                }
                                                if (matchNode.note.isNotBlank()) {
                                                    if (isNotEmpty()) append(" • ")
                                                    append(
                                                        matchNode.note
                                                            .removePrefix("https://")
                                                            .removePrefix("http://")
                                                            .removePrefix("www.")
                                                    )
                                                }
                                            }
                                            if (subtitle.isNotBlank()) {
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compact Top-Right Controls when in Landscape Fullscreen Mode
        AnimatedVisibility(
            visible = effectiveFullscreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.End)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isListLayoutMode) {
                        IconButton(
                            onClick = { animateCameraToFit() },
                            modifier = Modifier.testTag("fullscreen_fit_screen_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.FitScreen,
                                contentDescription = stringResource(R.string.action_fit_screen),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(
                        onClick = { exitFullscreenAndUnlockOrientation() },
                        modifier = Modifier.testTag("exit_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.FullscreenExit,
                            contentDescription = stringResource(R.string.action_exit_fullscreen),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Bottom Contextual Floating Action Bar + Layout Mode Switcher (hidden in landscape fullscreen)
        AnimatedVisibility(
            visible = !effectiveFullscreen,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f, fill = false),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    AnimatedContent(
                        targetState = selectedNode,
                        label = "bottomActionBar"
                    ) { currentSelection ->
                        if (currentSelection != null && selectedPositioned != null) {
                            val isRoot = currentSelection.id == document.rootNodeId
                            val siblings = document.nodes
                                .filter { it.parentId == currentSelection.parentId }
                                .sortedWith(compareBy({ it.orderIndex }, { it.id }))
                            val siblingIndex = siblings.indexOfFirst { it.id == currentSelection.id }
                            val canMoveLeft = !isRoot && siblingIndex > 0
                            val canMoveRight = !isRoot && siblingIndex in 0 until siblings.lastIndex

                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isLandscape) {
                                    Button(
                                        onClick = { sheetMode = NodeSheetMode.AddChild(currentSelection) },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("add_child_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_add_child))
                                    }
                                } else {
                                    FilledIconButton(
                                        onClick = { sheetMode = NodeSheetMode.AddChild(currentSelection) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("add_child_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = stringResource(R.string.action_add_child_desc),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }


                                if (currentSelection.note.isNotBlank()) {
                                    IconButton(
                                        onClick = { openNodeWebLink(currentSelection.note) },
                                        modifier = Modifier.testTag("open_node_link_button")
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.OpenInBrowser,
                                            contentDescription = "Buka tautan web",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (canMoveLeft) {
                                    IconButton(
                                        onClick = { onMoveSibling(currentSelection.id, -1) },
                                        modifier = Modifier.testTag("move_left_button")
                                    ) {
                                        Icon(AppIcons.ChevronLeft, contentDescription = stringResource(R.string.action_move_left))
                                    }
                                }

                                if (canMoveRight) {
                                    IconButton(
                                        onClick = { onMoveSibling(currentSelection.id, 1) },
                                        modifier = Modifier.testTag("move_right_button")
                                    ) {
                                        Icon(AppIcons.ChevronRight, contentDescription = stringResource(R.string.action_move_right))
                                    }
                                }

                                if (!isRoot) {
                                    IconButton(
                                        onClick = {
                                            if (selectedPositioned.hasChildren) {
                                                deleteConfirmNode = currentSelection
                                            } else {
                                                onDeleteNode(currentSelection.id, true)
                                            }
                                        },
                                        modifier = Modifier.testTag("delete_node_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isListLayoutMode) "${document.nodes.size} node" else "${(zoom * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                val rootNode = document.nodes.firstOrNull { it.id == document.rootNodeId }
                                if (rootNode != null) {
                                    if (isLandscape) {
                                        Button(
                                            onClick = { sheetMode = NodeSheetMode.AddChild(rootNode) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("quick_add_root_child_button")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.action_add_child), style = MaterialTheme.typography.labelLarge)
                                        }
                                    } else {
                                        FilledIconButton(
                                            onClick = { sheetMode = NodeSheetMode.AddChild(rootNode) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("quick_add_root_child_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = stringResource(R.string.action_add_child_desc),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LayoutModeSwitcherPill(
                    isListMode = isListLayoutMode,
                    showLabels = isLandscape || selectedNode == null,
                    onSelectRadiant = {
                        if (isListLayoutMode) {
                            isListLayoutMode = false
                            isLandscapeFullscreen = false
                            if (hasInitializedCamera) {
                                animateCameraToFit()
                            }
                        } else {
                            isLandscapeFullscreen = true
                            context.findEditorActivity()?.requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            if (isLandscape && hasInitializedCamera) {
                                animateCameraToFit()
                            }
                        }
                    },
                    onSelectList = {
                        isListLayoutMode = true
                        exitFullscreenAndUnlockOrientation()
                    }
                )
            }
        }
    }

    when (val mode = sheetMode) {
        is NodeSheetMode.AddChild -> {
            val siblingsCount = document.nodes.count { it.parentId == mode.parentNode.id }
            val defaultColor = if (mode.parentNode.id == document.rootNodeId) {
                val palette = listOf(
                    NodeColor.TEAL.key,
                    NodeColor.COBALT.key,
                    NodeColor.SAGE.key,
                    NodeColor.CORAL.key,
                    NodeColor.AMBER.key,
                    NodeColor.PLUM.key
                )
                palette[siblingsCount % palette.size]
            } else {
                mode.parentNode.colorKey
            }
            NodeEditorSheet(
                sheetTitle = "Tambah Cabang dari \"${mode.parentNode.title}\"",
                initialTitle = "",
                initialLink = "",
                initialColorKey = defaultColor,
                confirmLabel = "Tambah Cabang",
                onDismiss = { sheetMode = null },
                onConfirm = { title, link, colorKey ->
                    sheetMode = null
                    onAddChild(mode.parentNode.id, title, link, colorKey)
                }
            )
        }
        is NodeSheetMode.EditNode -> {
            NodeEditorSheet(
                sheetTitle = "Edit Node",
                initialTitle = mode.node.title,
                initialLink = mode.node.note,
                initialColorKey = mode.node.colorKey,
                confirmLabel = "Simpan",
                onDismiss = { sheetMode = null },
                onConfirm = { title, link, colorKey ->
                    sheetMode = null
                    onUpdateNode(mode.node.id, title, link, colorKey)
                }
            )
        }
        null -> Unit
    }

    deleteConfirmNode?.let { target ->
        val descendantCount = remember(document, target.id) {
            MindMapTreeOps.countDescendants(document, target.id)
        }
        val directChildrenCount = remember(document, target.id) {
            document.nodes.count { it.parentId == target.id }
        }
        val parentTitle = remember(document, target.parentId) {
            document.nodes.firstOrNull { it.id == target.parentId }?.title ?: document.title
        }

        AlertDialog(
            onDismissRequest = { deleteConfirmNode = null },
            title = {
                Text(
                    text = stringResource(R.string.delete_mode_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Cabang \"${target.title}\" memiliki $descendantCount sub-cabang. Pilih cara menghapusnya:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        onClick = {
                            deleteConfirmNode = null
                            onDeleteNode(target.id, true)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_branch_subtree_option")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.delete_mode_subtree_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Hapus \"${target.title}\" beserta seluruh $descendantCount sub-cabangnya.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            deleteConfirmNode = null
                            onDeleteNode(target.id, false)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_single_topic_option")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.DeleteSingleTopic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.delete_mode_single_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Hapus node ini saja; $directChildrenCount sub-cabang dinaikkan ke \"$parentTitle\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { deleteConfirmNode = null },
                    modifier = Modifier.testTag("delete_mode_cancel_button")
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun RadiantNodeCard(
    placed: PositionedNode,
    isRoot: Boolean,
    isSelected: Boolean,
    isDark: Boolean,
    pxPerDp: Float,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onOpenLink: () -> Unit
) {
    val node = placed.node
    val nodeColor = NodeColor.fromKey(node.colorKey)
    val accent = nodeColor.accent(isDark)
    val surfaceColor = if (isRoot) {
        accent
    } else {
        nodeColor.surface(isDark)
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

    val shape = RoundedCornerShape(if (isRoot) 24.dp else 16.dp)
    val borderColor = when {
        isSelected -> if (isRoot) MaterialTheme.colorScheme.secondary else accent
        isRoot -> accent
        else -> accent.copy(alpha = 0.48f)
    }
    val borderWidth = if (isSelected) 2.5.dp else if (isRoot) 2.dp else 1.2.dp

    Box(
        modifier = Modifier
            .zIndex(if (isSelected) 12f else 0f)
            .offset {
                IntOffset(
                    x = (placed.leftDp * pxPerDp).roundToInt(),
                    y = (placed.topDp * pxPerDp).roundToInt()
                )
            }
            .then(
                if (isSelected) {
                    Modifier
                        .width(placed.widthDp.dp)
                        .heightIn(min = placed.heightDp.dp)
                } else {
                    Modifier.size(width = placed.widthDp.dp, height = placed.heightDp.dp)
                }
            )
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (isSelected) {
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = placed.heightDp.dp)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .animateContentSize()
                .clip(shape)
                .border(borderWidth, borderColor, shape)
                .pointerInput(node.id) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { onDoubleTap() }
                    )
                }
                .testTag("mindmap_node_${node.id}"),
            shape = shape,
            color = surfaceColor,
            shadowElevation = if (isSelected) 8.dp else if (isRoot) 4.dp else 2.dp
        ) {
            Row(
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = placed.heightDp.dp)
                        } else {
                            Modifier.fillMaxSize()
                        }
                    )
                    .padding(
                        start = 12.dp,
                        end = if (node.note.isNotBlank()) 8.dp else 12.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = node.title,
                    style = if (isRoot) {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp)
                    },
                    color = titleColor,
                    textAlign = TextAlign.Center,
                    maxLines = if (isSelected) Int.MAX_VALUE else 3,
                    overflow = if (isSelected) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (node.note.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRoot) {
                                    titleColor.copy(alpha = 0.18f)
                                } else {
                                    accent.copy(alpha = 0.14f)
                                }
                            )
                            .clickable(onClick = onOpenLink)
                            .testTag("node_link_icon_${node.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Link,
                            contentDescription = "Buka tautan web",
                            tint = linkColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findEditorActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findEditorActivity()
    else -> null
}

@Composable
private fun LayoutModeSwitcherPill(
    isListMode: Boolean,
    showLabels: Boolean,
    onSelectRadiant: () -> Unit,
    onSelectList: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            LayoutModeOption(
                selected = !isListMode,
                icon = AppIcons.MindMapRadiant,
                label = stringResource(R.string.layout_mode_radiant),
                contentDescription = stringResource(R.string.action_mode_radiant),
                showLabel = showLabels,
                onClick = onSelectRadiant,
                modifier = Modifier.testTag("mode_radiant_button")
            )
            LayoutModeOption(
                selected = isListMode,
                icon = AppIcons.ListLayout,
                label = stringResource(R.string.layout_mode_list),
                contentDescription = stringResource(R.string.action_mode_list),
                showLabel = showLabels,
                onClick = onSelectList,
                modifier = Modifier.testTag("mode_list_button")
            )
        }
    }
}

@Composable
private fun LayoutModeOption(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (showLabel) 10.dp else 12.dp,
                vertical = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            if (showLabel) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}
