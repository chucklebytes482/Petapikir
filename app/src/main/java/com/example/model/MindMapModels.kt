package com.example.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class NodeColor(
    val key: String,
    val label: String,
    val accentLight: Color,
    val surfaceLight: Color,
    val accentDark: Color,
    val surfaceDark: Color
) {
    SLATE(
        key = "slate",
        label = "Batu",
        accentLight = Color(0xFF1E293B),
        surfaceLight = Color(0xFFF1F5F9),
        accentDark = Color(0xFF94A3B8),
        surfaceDark = Color(0xFF1E2633)
    ),
    TEAL(
        key = "teal",
        label = "Toska",
        accentLight = Color(0xFF0D5466),
        surfaceLight = Color(0xFFE6F4F7),
        accentDark = Color(0xFF58C4DC),
        surfaceDark = Color(0xFF0D333D)
    ),
    COBALT(
        key = "cobalt",
        label = "Biru",
        accentLight = Color(0xFF1D4ED8),
        surfaceLight = Color(0xFFE8F0FE),
        accentDark = Color(0xFF60A5FA),
        surfaceDark = Color(0xFF15284B)
    ),
    SAGE(
        key = "sage",
        label = "Hijau",
        accentLight = Color(0xFF1E7A4E),
        surfaceLight = Color(0xFFE5F6ED),
        accentDark = Color(0xFF52C78E),
        surfaceDark = Color(0xFF113524)
    ),
    AMBER(
        key = "amber",
        label = "Kuning",
        accentLight = Color(0xFFB45309),
        surfaceLight = Color(0xFFFEF3C7),
        accentDark = Color(0xFFFBBF24),
        surfaceDark = Color(0xFF3D2A0B)
    ),
    CORAL(
        key = "coral",
        label = "Koral",
        accentLight = Color(0xFFC85A32),
        surfaceLight = Color(0xFFFFEDE6),
        accentDark = Color(0xFFF08B67),
        surfaceDark = Color(0xFF471F11)
    ),
    PLUM(
        key = "plum",
        label = "Ungu",
        accentLight = Color(0xFF7C3AED),
        surfaceLight = Color(0xFFF1EAFE),
        accentDark = Color(0xFFA78BFA),
        surfaceDark = Color(0xFF2B1C4E)
    );

    fun accent(darkTheme: Boolean): Color = if (darkTheme) accentDark else accentLight
    fun surface(darkTheme: Boolean): Color = if (darkTheme) surfaceDark else surfaceLight

    companion object {
        fun fromKey(key: String?): NodeColor =
            entries.firstOrNull { it.key == key } ?: TEAL
    }
}

enum class SyncState {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED
}

data class MindMapNode(
    val id: String,
    val parentId: String? = null,
    val title: String,
    val note: String = "",
    val colorKey: String = NodeColor.TEAL.key,
    val orderIndex: Int = 0
)

data class MindMapDocument(
    val id: String,
    val title: String,
    val rootNodeId: String,
    val nodes: List<MindMapNode>,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int = 1
)

object MindMapTreeOps {

    fun createEmpty(title: String = "Peta Pikiran Baru"): MindMapDocument {
        val now = System.currentTimeMillis()
        val rootId = UUID.randomUUID().toString()
        val root = MindMapNode(
            id = rootId,
            parentId = null,
            title = title,
            colorKey = NodeColor.SLATE.key,
            orderIndex = 0
        )
        return MindMapDocument(
            id = UUID.randomUUID().toString(),
            title = title,
            rootNodeId = rootId,
            nodes = listOf(root),
            createdAt = now,
            updatedAt = now
        )
    }

    fun createSampleProject(): MindMapDocument {
        val now = System.currentTimeMillis()
        val mapId = UUID.randomUUID().toString()
        val rootId = UUID.randomUUID().toString()
        val rId = UUID.randomUUID().toString()
        val dId = UUID.randomUUID().toString()
        val lId = UUID.randomUUID().toString()

        val nodes = listOf(
            MindMapNode(
                id = rootId,
                parentId = null,
                title = "Peluncuran Produk",
                note = "",
                colorKey = NodeColor.SLATE.key,
                orderIndex = 0
            ),
            MindMapNode(
                id = rId,
                parentId = rootId,
                title = "Riset & Strategi",
                colorKey = NodeColor.TEAL.key,
                orderIndex = 0
            ),
            MindMapNode(
                id = UUID.randomUUID().toString(),
                parentId = rId,
                title = "Wawancara Pengguna",
                colorKey = NodeColor.TEAL.key,
                orderIndex = 0
            ),
            MindMapNode(
                id = UUID.randomUUID().toString(),
                parentId = rId,
                title = "Analisis Kompetitor",
                colorKey = NodeColor.TEAL.key,
                orderIndex = 1
            ),
            MindMapNode(
                id = dId,
                parentId = rootId,
                title = "Desain & Bangun",
                colorKey = NodeColor.COBALT.key,
                orderIndex = 1
            ),
            MindMapNode(
                id = UUID.randomUUID().toString(),
                parentId = dId,
                title = "Prototipe UI",
                colorKey = NodeColor.COBALT.key,
                orderIndex = 0
            ),
            MindMapNode(
                id = UUID.randomUUID().toString(),
                parentId = dId,
                title = "Uji Kecepatan",
                colorKey = NodeColor.COBALT.key,
                orderIndex = 1
            ),
            MindMapNode(
                id = lId,
                parentId = rootId,
                title = "Pemasaran",
                colorKey = NodeColor.CORAL.key,
                orderIndex = 2
            ),
            MindMapNode(
                id = UUID.randomUUID().toString(),
                parentId = lId,
                title = "Siaran Pers",
                colorKey = NodeColor.CORAL.key,
                orderIndex = 0
            )
        )
        return MindMapDocument(
            id = mapId,
            title = "Peluncuran Produk",
            rootNodeId = rootId,
            nodes = nodes,
            createdAt = now,
            updatedAt = now
        )
    }

    fun addChild(
        doc: MindMapDocument,
        parentId: String,
        title: String = "Cabang Baru",
        colorKey: String? = null
    ): Pair<MindMapDocument, MindMapNode> {
        val parent = doc.nodes.firstOrNull { it.id == parentId }
        val siblings = doc.nodes.filter { it.parentId == parentId }
        val nextOrder = (siblings.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val inheritedColor = colorKey ?: run {
            if (parent?.parentId == null) {
                val palette = listOf(
                    NodeColor.TEAL.key,
                    NodeColor.COBALT.key,
                    NodeColor.SAGE.key,
                    NodeColor.CORAL.key,
                    NodeColor.AMBER.key,
                    NodeColor.PLUM.key
                )
                palette[siblings.size % palette.size]
            } else {
                parent.colorKey
            }
        }
        val newNode = MindMapNode(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            title = title,
            colorKey = inheritedColor,
            orderIndex = nextOrder
        )
        return doc.copy(nodes = doc.nodes + newNode, updatedAt = System.currentTimeMillis()) to newNode
    }

    fun updateNode(
        doc: MindMapDocument,
        nodeId: String,
        title: String,
        note: String,
        colorKey: String
    ): MindMapDocument {
        val cleanTitle = title.trim().ifEmpty { "Tanpa Judul" }
        val cleanNote = note.trim()
        val updatedNodes = doc.nodes.map { node ->
            if (node.id == nodeId) {
                node.copy(title = cleanTitle, note = cleanNote, colorKey = colorKey)
            } else {
                node
            }
        }
        val updatedDocTitle = if (nodeId == doc.rootNodeId && doc.title == doc.nodes.firstOrNull { it.id == doc.rootNodeId }?.title) {
            cleanTitle
        } else {
            doc.title
        }
        return doc.copy(
            title = updatedDocTitle,
            nodes = updatedNodes,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun renameDocument(doc: MindMapDocument, newTitle: String): MindMapDocument {
        val clean = newTitle.trim().ifEmpty { doc.title }
        return doc.copy(title = clean, updatedAt = System.currentTimeMillis())
    }

    fun moveSibling(doc: MindMapDocument, nodeId: String, delta: Int): MindMapDocument {
        val target = doc.nodes.firstOrNull { it.id == nodeId } ?: return doc
        val parentId = target.parentId ?: return doc
        val siblings = doc.nodes.filter { it.parentId == parentId }
            .sortedWith(compareBy({ it.orderIndex }, { it.id }))
            .toMutableList()
        val index = siblings.indexOfFirst { it.id == nodeId }
        val newIndex = (index + delta).coerceIn(0, siblings.lastIndex)
        if (index < 0 || index == newIndex) return doc
        val item = siblings.removeAt(index)
        siblings.add(newIndex, item)
        val reindexed = siblings.mapIndexed { idx, node -> node.id to idx }.toMap()
        val updated = doc.nodes.map { node ->
            val updatedOrder = reindexed[node.id]
            if (updatedOrder != null) node.copy(orderIndex = updatedOrder) else node
        }
        return doc.copy(nodes = updated, updatedAt = System.currentTimeMillis())
    }

    fun countDescendants(doc: MindMapDocument, nodeId: String): Int {
        val visited = mutableSetOf(nodeId)
        val queue = ArrayDeque<String>()
        queue.add(nodeId)
        var count = 0
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            doc.nodes.filter { it.parentId == current }.forEach { child ->
                if (visited.add(child.id)) {
                    count++
                    queue.add(child.id)
                }
            }
        }
        return count
    }

    fun deleteNodeAndSubtree(doc: MindMapDocument, nodeId: String): MindMapDocument {
        if (nodeId == doc.rootNodeId) return doc
        val toRemove = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(nodeId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (toRemove.add(current)) {
                doc.nodes.filter { it.parentId == current }.forEach { queue.add(it.id) }
            }
        }
        val remaining = doc.nodes.filterNot { it.id in toRemove }
        return doc.copy(nodes = remaining, updatedAt = System.currentTimeMillis())
    }

    fun deleteSingleNode(doc: MindMapDocument, nodeId: String): MindMapDocument {
        if (nodeId == doc.rootNodeId) return doc
        val target = doc.nodes.firstOrNull { it.id == nodeId } ?: return doc
        val targetParentId = target.parentId ?: return doc

        val parentSiblings = doc.nodes
            .filter { it.parentId == targetParentId }
            .sortedWith(compareBy({ it.orderIndex }, { it.id }))
        val directChildren = doc.nodes
            .filter { it.parentId == nodeId }
            .sortedWith(compareBy({ it.orderIndex }, { it.id }))

        val newOrderIds = mutableListOf<String>()
        for (sibling in parentSiblings) {
            if (sibling.id == nodeId) {
                directChildren.forEach { newOrderIds.add(it.id) }
            } else {
                newOrderIds.add(sibling.id)
            }
        }
        val reindexed = newOrderIds.mapIndexed { idx, id -> id to idx }.toMap()

        val updatedNodes = doc.nodes
            .filterNot { it.id == nodeId }
            .map { node ->
                when {
                    node.parentId == nodeId -> node.copy(
                        parentId = targetParentId,
                        orderIndex = reindexed[node.id] ?: node.orderIndex
                    )
                    node.parentId == targetParentId -> node.copy(
                        orderIndex = reindexed[node.id] ?: node.orderIndex
                    )
                    else -> node
                }
            }

        return doc.copy(nodes = updatedNodes, updatedAt = System.currentTimeMillis())
    }

    fun toMarkdownOutline(doc: MindMapDocument): String {
        val byParent = doc.nodes.groupBy { it.parentId }
        val sb = StringBuilder()
        sb.append("# ").append(doc.title).append("\n")

        fun appendNode(nodeId: String, depth: Int) {
            val node = doc.nodes.firstOrNull { it.id == nodeId } ?: return
            if (depth > 0) {
                sb.append("  ".repeat(depth - 1)).append("- ").append(node.title)
                if (node.note.isNotBlank()) {
                    sb.append(" (").append(node.note).append(")")
                }
                sb.append("\n")
            } else if (node.note.isNotBlank()) {
                sb.append("> ").append(node.note).append("\n\n")
            }
            val children = byParent[nodeId].orEmpty()
                .sortedWith(compareBy({ it.orderIndex }, { it.id }))
            for (child in children) {
                appendNode(child.id, depth + 1)
            }
        }

        appendNode(doc.rootNodeId, 0)
        return sb.toString().trimEnd()
    }

    fun flattenVisibleOutline(doc: MindMapDocument): List<OutlineListItem> {
        val nodesById = doc.nodes.associateBy { it.id }
        val byParent = doc.nodes.groupBy { it.parentId }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.orderIndex }, { it.id })) }
        val result = mutableListOf<OutlineListItem>()
        val visited = mutableSetOf<String>()

        fun traverse(nodeId: String, depth: Int, siblingIndex: Int, siblingCount: Int) {
            if (!visited.add(nodeId)) return
            val node = nodesById[nodeId] ?: return
            val children = byParent[nodeId].orEmpty()
            result += OutlineListItem(
                node = node,
                depth = depth,
                hasChildren = children.isNotEmpty(),
                siblingIndex = siblingIndex,
                siblingCount = siblingCount
            )
            children.forEachIndexed { idx, child ->
                traverse(child.id, depth + 1, idx, children.size)
            }
        }

        traverse(doc.rootNodeId, depth = 0, siblingIndex = 0, siblingCount = 1)
        return result
    }
}

data class OutlineListItem(
    val node: MindMapNode,
    val depth: Int,
    val hasChildren: Boolean,
    val siblingIndex: Int,
    val siblingCount: Int
)
