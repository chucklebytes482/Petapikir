package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.AndroidLocalBackupManager
import com.example.backup.BackupPreferences
import com.example.backup.BackupSettings
import com.example.data.LocalBackupStatus
import com.example.data.MindMapJsonCodec
import com.example.data.MindMapRepository
import com.example.data.MindMapSummary
import com.example.data.local.AppDatabase
import com.example.layout.RadiantLayoutEngine
import com.example.layout.RadiantLayoutResult
import com.example.model.MindMapDocument
import com.example.model.MindMapNode
import com.example.model.MindMapTreeOps
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiEvent {
    data class ShowMessage(val message: String) : UiEvent()
    data object FitCanvasToScreen : UiEvent()
}

data class EditorUiState(
    val document: MindMapDocument? = null,
    val layout: RadiantLayoutResult = RadiantLayoutResult(
        nodes = emptyList(),
        nodeMap = emptyMap(),
        edges = emptyList(),
        bounds = com.example.layout.LayoutBounds(0f, 0f, 1f, 1f)
    ),
    val selectedNodeId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class MindMapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MindMapRepository = run {
        val db = AppDatabase.getInstance(application)
        val codec = MindMapJsonCodec()
        MindMapRepository(
            dao = db.mindMapDao(),
            codec = codec,
            backupPreferences = BackupPreferences(application),
            backupStorage = AndroidLocalBackupManager(application, codec)
        )
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeMapId = MutableStateFlow<String?>(null)
    val activeMapId: StateFlow<String?> = _activeMapId.asStateFlow()

    private val _activeDocument = MutableStateFlow<MindMapDocument?>(null)
    private val _selectedNodeId = MutableStateFlow<String?>(null)
    private val _undoStack = MutableStateFlow<List<MindMapDocument>>(emptyList())
    private val _redoStack = MutableStateFlow<List<MindMapDocument>>(emptyList())

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val backupStatus: StateFlow<LocalBackupStatus> = repository.backupStatus
    val backupSettings: StateFlow<BackupSettings> = repository.backupSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BackupSettings()
    )
    val pendingCount: StateFlow<Int> = repository.pendingCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val filteredSummaries: StateFlow<List<MindMapSummary>> = combine(
        repository.summaries,
        _searchQuery
    ) { items, query ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            items
        } else {
            items.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.rootTitle.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val editorState: StateFlow<EditorUiState> = combine(
        _activeDocument,
        _selectedNodeId,
        _undoStack,
        _redoStack
    ) { doc, selectedId, undo, redo ->
        val layout = if (doc != null) {
            RadiantLayoutEngine.compute(doc)
        } else {
            RadiantLayoutResult(
                nodes = emptyList(),
                nodeMap = emptyMap(),
                edges = emptyList(),
                bounds = com.example.layout.LayoutBounds(0f, 0f, 1f, 1f)
            )
        }
        val validSelection = if (doc != null && selectedId != null && doc.nodes.any { it.id == selectedId }) {
            selectedId
        } else {
            null
        }
        EditorUiState(
            document = doc,
            layout = layout,
            selectedNodeId = validSelection,
            canUndo = undo.isNotEmpty(),
            canRedo = redo.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditorUiState()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openMap(id: String) {
        viewModelScope.launch {
            val doc = repository.getDocument(id)
            if (doc != null) {
                _undoStack.value = emptyList()
                _redoStack.value = emptyList()
                _activeDocument.value = doc
                _selectedNodeId.value = doc.rootNodeId
                _activeMapId.value = doc.id
                _events.emit(UiEvent.FitCanvasToScreen)
            } else {
                _events.emit(UiEvent.ShowMessage("Peta pikiran tidak ditemukan."))
            }
        }
    }

    fun closeMap() {
        _activeMapId.value = null
        _activeDocument.value = null
        _selectedNodeId.value = null
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
    }

    fun createNewMap(title: String) {
        viewModelScope.launch {
            val clean = title.trim().ifEmpty { "Peta Pikiran Baru" }
            val created = repository.createNewMap(clean)
            _undoStack.value = emptyList()
            _redoStack.value = emptyList()
            _activeDocument.value = created
            _selectedNodeId.value = created.rootNodeId
            _activeMapId.value = created.id
            _events.emit(UiEvent.FitCanvasToScreen)
        }
    }

    fun createSampleMap() {
        viewModelScope.launch {
            val sample = repository.createSampleMap()
            _undoStack.value = emptyList()
            _redoStack.value = emptyList()
            _activeDocument.value = sample
            _selectedNodeId.value = sample.rootNodeId
            _activeMapId.value = sample.id
            _events.emit(UiEvent.FitCanvasToScreen)
        }
    }

    fun duplicateMap(id: String) {
        viewModelScope.launch {
            val duplicated = repository.duplicateMap(id)
            if (duplicated != null) {
                _events.emit(UiEvent.ShowMessage("Salinan \"${duplicated.title}\" dibuat & dicadangkan."))
            }
        }
    }

    fun deleteMap(id: String) {
        viewModelScope.launch {
            if (_activeMapId.value == id) {
                closeMap()
            }
            repository.deleteMap(id)
            _events.emit(UiEvent.ShowMessage("Peta pikiran dihapus."))
        }
    }

    fun selectNode(nodeId: String?) {
        _selectedNodeId.value = nodeId
    }

    fun addChildNode(
        parentId: String,
        title: String,
        note: String = "",
        colorKey: String? = null
    ) {
        val current = _activeDocument.value ?: return
        val cleanTitle = title.trim().ifEmpty { "Cabang Baru" }
        val (withChild, newNode) = MindMapTreeOps.addChild(
            doc = current,
            parentId = parentId,
            title = cleanTitle,
            colorKey = colorKey
        )
        val finalDoc = if (note.isNotBlank()) {
            MindMapTreeOps.updateNode(
                doc = withChild,
                nodeId = newNode.id,
                title = cleanTitle,
                note = note,
                colorKey = newNode.colorKey
            )
        } else {
            withChild
        }
        commitDocumentChange(current, finalDoc, selectNodeId = newNode.id)
    }

    fun updateNode(nodeId: String, title: String, link: String, colorKey: String) {
        val current = _activeDocument.value ?: return
        val next = MindMapTreeOps.updateNode(current, nodeId, title, link, colorKey)
        commitDocumentChange(current, next, selectNodeId = nodeId)
    }

    fun moveSibling(nodeId: String, delta: Int) {
        val current = _activeDocument.value ?: return
        val next = MindMapTreeOps.moveSibling(current, nodeId, delta)
        if (next != current) {
            commitDocumentChange(current, next, selectNodeId = nodeId)
        }
    }

    fun deleteNode(nodeId: String, includeSubtree: Boolean = true) {
        val current = _activeDocument.value ?: return
        if (nodeId == current.rootNodeId) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowMessage("Node akar tidak dapat dihapus."))
            }
            return
        }
        val parentId = current.nodes.firstOrNull { it.id == nodeId }?.parentId ?: current.rootNodeId
        val next = if (includeSubtree) {
            MindMapTreeOps.deleteNodeAndSubtree(current, nodeId)
        } else {
            MindMapTreeOps.deleteSingleNode(current, nodeId)
        }
        commitDocumentChange(current, next, selectNodeId = parentId)
    }

    fun undo() {
        val current = _activeDocument.value ?: return
        val stack = _undoStack.value
        if (stack.isEmpty()) return
        val previous = stack.last()
        _undoStack.value = stack.dropLast(1)
        _redoStack.value = (_redoStack.value + current).takeLast(25)
        _activeDocument.value = previous
        if (_selectedNodeId.value != null && previous.nodes.none { it.id == _selectedNodeId.value }) {
            _selectedNodeId.value = previous.rootNodeId
        }
        viewModelScope.launch { repository.saveDocument(previous) }
    }

    fun redo() {
        val current = _activeDocument.value ?: return
        val stack = _redoStack.value
        if (stack.isEmpty()) return
        val next = stack.last()
        _redoStack.value = stack.dropLast(1)
        _undoStack.value = (_undoStack.value + current).takeLast(25)
        _activeDocument.value = next
        if (_selectedNodeId.value != null && next.nodes.none { it.id == _selectedNodeId.value }) {
            _selectedNodeId.value = next.rootNodeId
        }
        viewModelScope.launch { repository.saveDocument(next) }
    }

    private fun commitDocumentChange(
        previous: MindMapDocument,
        next: MindMapDocument,
        selectNodeId: String?
    ) {
        _undoStack.value = (_undoStack.value + previous).takeLast(25)
        _redoStack.value = emptyList()
        _activeDocument.value = next
        _selectedNodeId.value = selectNodeId
        viewModelScope.launch {
            repository.saveDocument(next)
        }
    }

    fun getSelectedNode(): MindMapNode? {
        val doc = _activeDocument.value ?: return null
        val id = _selectedNodeId.value ?: return null
        return doc.nodes.firstOrNull { it.id == id }
    }

    fun selectBackupTreeUri(uriString: String, treeDocumentId: String?) {
        viewModelScope.launch {
            repository.updateBackupTreeUri(uriString, treeDocumentId)
                .onSuccess { displayPath ->
                    _events.emit(UiEvent.ShowMessage("Jalur cadangan diubah ke $displayPath"))
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ShowMessage(err.message ?: "Folder harus di dalam /storage/emulated/0/Documents/"))
                }
        }
    }

    fun saveCustomBackupSubPath(rawSubPath: String) {
        viewModelScope.launch {
            repository.updateBackupSubPath(rawSubPath)
                .onSuccess { displayPath ->
                    _events.emit(UiEvent.ShowMessage("Jalur cadangan disimpan: $displayPath"))
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ShowMessage(err.message ?: "Jalur cadangan tidak valid."))
                }
        }
    }
}
