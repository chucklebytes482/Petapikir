package com.example.data

import com.example.model.MindMapDocument
import com.example.model.MindMapNode
import com.example.model.NodeColor
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Zero-dependency JSON codec backed by Android's built-in org.json.
 * Outputs pretty-printed, AI-friendly JSON containing both human/LLM-readable
 * `outline` + nested `tree` representations and lossless flat `nodes` for local backup & import.
 */
class MindMapJsonCodec {

    fun encodeDocument(document: MindMapDocument): String {
        val nodesById = document.nodes.associateBy { it.id }
        val childrenByParent = document.nodes.groupBy { it.parentId }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.orderIndex }, { it.id })) }

        val outlineArray = JSONArray()
        val outlineVisited = mutableSetOf<String>()
        fun appendOutline(nodeId: String, depth: Int) {
            if (!outlineVisited.add(nodeId)) return
            val node = nodesById[nodeId] ?: return
            val prefix = if (depth == 0) "" else "  ".repeat(depth) + "- "
            val noteSuffix = if (node.note.isNotBlank()) " [${node.note}]" else ""
            outlineArray.put("$prefix${node.title}$noteSuffix")
            for (child in childrenByParent[nodeId].orEmpty()) {
                appendOutline(child.id, depth + 1)
            }
        }
        appendOutline(document.rootNodeId, 0)

        val treeVisited = mutableSetOf<String>()
        fun buildTreeObject(nodeId: String): JSONObject? {
            if (!treeVisited.add(nodeId)) return null
            val node = nodesById[nodeId] ?: return null
            return JSONObject().apply {
                put("id", node.id)
                put("title", node.title)
                if (node.note.isNotBlank()) {
                    put("link", node.note)
                }
                put("color", node.colorKey)
                val children = childrenByParent[nodeId].orEmpty()
                if (children.isNotEmpty()) {
                    val childArr = JSONArray()
                    for (child in children) {
                        buildTreeObject(child.id)?.let(childArr::put)
                    }
                    if (childArr.length() > 0) {
                        put("children", childArr)
                    }
                }
            }
        }

        val nodesArray = JSONArray()
        for (node in document.nodes) {
            nodesArray.put(
                JSONObject().apply {
                    put("id", node.id)
                    if (node.parentId != null) put("parentId", node.parentId) else put("parentId", JSONObject.NULL)
                    put("title", node.title)
                    put("note", node.note)
                    put("colorKey", node.colorKey)
                    put("orderIndex", node.orderIndex)
                }
            )
        }

        return JSONObject().apply {
            put("title", document.title)
            put("layoutMode", "top-down")
            put("nodeCount", document.nodes.size)
            put("outline", outlineArray)
            buildTreeObject(document.rootNodeId)?.let { put("tree", it) }
            put("id", document.id)
            put("rootNodeId", document.rootNodeId)
            put("createdAt", document.createdAt)
            put("updatedAt", document.updatedAt)
            put("schemaVersion", document.schemaVersion)
            put("nodes", nodesArray)
        }.toString(2)
    }

    fun decodeDocument(json: String, fallbackId: String? = null): MindMapDocument? = runCatching {
        val obj = JSONObject(json)
        val nodesArray = obj.optJSONArray("nodes")

        val nodes: List<MindMapNode>
        var resolvedRootId = obj.optString("rootNodeId", "")

        if (nodesArray != null && nodesArray.length() > 0) {
            nodes = buildList(nodesArray.length()) {
                for (i in 0 until nodesArray.length()) {
                    val n = nodesArray.getJSONObject(i)
                    val linkOrNote = n.optString("note", "").ifBlank { n.optString("link", "") }
                    add(
                        MindMapNode(
                            id = n.getString("id"),
                            parentId = if (n.isNull("parentId")) null else n.optString("parentId", "").ifBlank { null },
                            title = n.optString("title", "Tanpa Judul"),
                            note = linkOrNote,
                            colorKey = n.optString("colorKey", NodeColor.TEAL.key),
                            orderIndex = n.optInt("orderIndex", i)
                        )
                    )
                }
            }
            if (resolvedRootId.isBlank()) {
                resolvedRootId = nodes.firstOrNull { it.parentId == null }?.id ?: nodes.first().id
            }
        } else {
            val treeObj = obj.optJSONObject("tree") ?: return@runCatching null
            val flattened = mutableListOf<MindMapNode>()
            fun flattenTree(nodeObj: JSONObject, parentId: String?, orderIndex: Int, pathSeed: String): String {
                val id = nodeObj.optString("id", "").ifBlank {
                    UUID.nameUUIDFromBytes("${fallbackId ?: "tree"}:$pathSeed".toByteArray()).toString()
                }
                val colorKey = nodeObj.optString("colorKey", "").ifBlank {
                    nodeObj.optString("color", if (parentId == null) NodeColor.SLATE.key else NodeColor.TEAL.key)
                }
                val linkOrNote = nodeObj.optString("link", "").ifBlank { nodeObj.optString("note", "") }
                flattened.add(
                    MindMapNode(
                        id = id,
                        parentId = parentId,
                        title = nodeObj.optString("title", "Tanpa Judul"),
                        note = linkOrNote,
                        colorKey = colorKey,
                        orderIndex = orderIndex
                    )
                )
                val childrenArr = nodeObj.optJSONArray("children")
                if (childrenArr != null) {
                    for (i in 0 until childrenArr.length()) {
                        val childObj = childrenArr.optJSONObject(i) ?: continue
                        flattenTree(childObj, id, i, "$pathSeed/$i")
                    }
                }
                return id
            }
            val parsedRootId = flattenTree(treeObj, null, 0, "root")
            if (flattened.isEmpty()) return@runCatching null
            nodes = flattened
            if (resolvedRootId.isBlank()) {
                resolvedRootId = parsedRootId
            }
        }

        val now = System.currentTimeMillis()
        MindMapDocument(
            id = obj.optString("id", "").ifBlank { fallbackId ?: UUID.randomUUID().toString() },
            title = obj.optString("title", "Peta Pikiran"),
            rootNodeId = resolvedRootId,
            nodes = nodes,
            createdAt = obj.optLong("createdAt", now),
            updatedAt = obj.optLong("updatedAt", now),
            schemaVersion = obj.optInt("schemaVersion", 1)
        )
    }.getOrNull()
}
