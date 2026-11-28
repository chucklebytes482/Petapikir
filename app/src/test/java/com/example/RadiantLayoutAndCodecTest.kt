package com.example

import com.example.data.MindMapJsonCodec
import com.example.layout.RadiantLayoutEngine
import com.example.model.MindMapTreeOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RadiantLayoutAndCodecTest {

    @Test
    fun `radiant layout centers root at origin and avoids all bounding box overlaps`() {
        val doc = MindMapTreeOps.createSampleProject()
        val result = RadiantLayoutEngine.compute(doc)

        assertEquals(doc.nodes.size, result.nodes.size)
        val root = result.nodeMap[doc.rootNodeId]
        assertNotNull(root)
        assertEquals(0f, root!!.centerXDp, 0.01f)
        assertEquals(0f, root.centerYDp, 0.01f)

        for (i in result.nodes.indices) {
            for (j in i + 1 until result.nodes.size) {
                val a = result.nodes[i]
                val b = result.nodes[j]
                val overlaps = a.leftDp < b.rightDp &&
                    a.rightDp > b.leftDp &&
                    a.topDp < b.bottomYDp &&
                    a.bottomYDp > b.topDp
                assertTrue(
                    "Overlap detected between ${a.node.title} and ${b.node.title}",
                    !overlaps
                )
            }
        }

        for (edge in result.edges) {
            val parent = result.nodeMap[edge.parentId]!!
            val child = result.nodeMap[edge.childId]!!
            val pDist = hypot(parent.centerXDp, parent.centerYDp)
            val cDist = hypot(child.centerXDp, child.centerYDp)
            assertTrue(
                "Child ${child.node.title} ($cDist) should radiate outward from parent ${parent.node.title} ($pDist)",
                cDist > pDist
            )
        }
    }

    @Test
    fun `radiant layout handles uneven dense branches cleanly without overlaps`() {
        var doc = MindMapTreeOps.createEmpty("Pusat Ide")
        val branches = (1..5).map { idx ->
            val (next, node) = MindMapTreeOps.addChild(doc, doc.rootNodeId, "Cabang $idx")
            doc = next
            node
        }
        // Add 6 leaves to the first branch to stress-test dense angular sectors
        for (leafIdx in 1..6) {
            val (next, _) = MindMapTreeOps.addChild(doc, branches.first().id, "Sub Topik Panjang Nomor $leafIdx")
            doc = next
        }

        val result = RadiantLayoutEngine.compute(doc)
        assertEquals(doc.nodes.size, result.nodes.size)
        assertEquals(doc.nodes.size - 1, result.edges.size)
        assertTrue(result.nodeMap[branches.first().id]!!.hasChildren)

        for (i in result.nodes.indices) {
            for (j in i + 1 until result.nodes.size) {
                val a = result.nodes[i]
                val b = result.nodes[j]
                val overlaps = a.leftDp < b.rightDp &&
                    a.rightDp > b.leftDp &&
                    a.topDp < b.bottomYDp &&
                    a.bottomYDp > b.topDp
                assertTrue(
                    "Overlap detected in dense radiant tree between ${a.node.title} and ${b.node.title}",
                    !overlaps
                )
            }
        }
    }

    @Test
    fun `json codec round trips document accurately`() {
        val codec = MindMapJsonCodec()
        val sample = MindMapTreeOps.createSampleProject()
        val json = codec.encodeDocument(sample)
        val decoded = codec.decodeDocument(json)

        assertNotNull(decoded)
        assertEquals(sample.id, decoded!!.id)
        assertEquals(sample.title, decoded.title)
        assertEquals(sample.nodes.size, decoded.nodes.size)
        assertTrue(json.contains("\n  \"outline\""))
        assertTrue(json.contains("\"tree\""))
        assertTrue(json.contains("\"children\""))
    }

    @Test
    fun `json codec decodes tree-only json authored externally`() {
        val codec = MindMapJsonCodec()
        val treeOnlyJson = """
            {
              "title": "Ide Konten",
              "tree": {
                "title": "Ide Konten",
                "children": [
                  {
                    "title": "YouTube",
                    "note": "Tutorial Mingguan",
                    "children": [
                      { "title": "Skrip" },
                      { "title": "Rekam" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val decoded = codec.decodeDocument(treeOnlyJson)
        assertNotNull(decoded)
        assertEquals("Ide Konten", decoded!!.title)
        assertEquals(4, decoded.nodes.size)
        val root = decoded.nodes.first { it.id == decoded.rootNodeId }
        val yt = decoded.nodes.first { it.parentId == root.id }
        assertEquals("YouTube", yt.title)
        assertEquals("Tutorial Mingguan", yt.note)
        assertEquals(2, decoded.nodes.count { it.parentId == yt.id })
    }

    @Test
    fun `web link normalization and tree link field decode properly`() {
        assertEquals("", com.example.ui.components.normalizeWebLink("   "))
        assertEquals("https://contoh.com", com.example.ui.components.normalizeWebLink("contoh.com"))
        assertEquals("https://contoh.com/peta", com.example.ui.components.normalizeWebLink("  https://contoh.com/peta  "))
        assertEquals("http://localhost:8080", com.example.ui.components.normalizeWebLink("http://localhost:8080"))

        val codec = MindMapJsonCodec()
        val treeWithLink = """
            {
              "title": "Referensi",
              "tree": {
                "title": "Referensi",
                "link": "https://developer.android.com",
                "children": []
              }
            }
        """.trimIndent()
        val decoded = codec.decodeDocument(treeWithLink)
        assertNotNull(decoded)
        val root = decoded!!.nodes.first { it.id == decoded.rootNodeId }
        assertEquals("https://developer.android.com", root.note)

        val encoded = codec.encodeDocument(decoded)
        val encodedJson = org.json.JSONObject(encoded)
        assertEquals(
            "https://developer.android.com",
            encodedJson.getJSONObject("tree").getString("link")
        )
        assertEquals(
            "Referensi [https://developer.android.com]",
            encodedJson.getJSONArray("outline").getString(0)
        )
    }

    @Test
    fun `two-mode delete supports both subtree deletion and single-topic promotion`() {
        val sample = MindMapTreeOps.createSampleProject()
        val firstBranch = sample.nodes.first { it.parentId == sample.rootNodeId && it.orderIndex == 0 }
        val secondBranch = sample.nodes.first { it.parentId == sample.rootNodeId && it.orderIndex == 1 }
        val thirdBranch = sample.nodes.first { it.parentId == sample.rootNodeId && it.orderIndex == 2 }
        val childA = sample.nodes.first { it.parentId == firstBranch.id && it.orderIndex == 0 }
        val childB = sample.nodes.first { it.parentId == firstBranch.id && it.orderIndex == 1 }

        assertEquals(2, MindMapTreeOps.countDescendants(sample, firstBranch.id))

        val afterSubtreeDelete = MindMapTreeOps.deleteNodeAndSubtree(sample, firstBranch.id)
        assertEquals(sample.nodes.size - 3, afterSubtreeDelete.nodes.size)
        assertTrue(afterSubtreeDelete.nodes.none { it.id == firstBranch.id || it.id == childA.id || it.id == childB.id })

        val afterSingleDelete = MindMapTreeOps.deleteSingleNode(sample, firstBranch.id)
        assertEquals(sample.nodes.size - 1, afterSingleDelete.nodes.size)
        assertTrue(afterSingleDelete.nodes.none { it.id == firstBranch.id })

        val rootChildren = afterSingleDelete.nodes
            .filter { it.parentId == sample.rootNodeId }
            .sortedBy { it.orderIndex }
            .map { it.id }
        assertEquals(
            listOf(childA.id, childB.id, secondBranch.id, thirdBranch.id),
            rootChildren
        )

        assertEquals(sample, MindMapTreeOps.deleteSingleNode(sample, sample.rootNodeId))
        assertEquals(sample, MindMapTreeOps.deleteNodeAndSubtree(sample, sample.rootNodeId))
    }

    @Test
    fun `flattenVisibleOutline preserves full hierarchy depth and order`() {
        val sample = MindMapTreeOps.createSampleProject()
        val outline = MindMapTreeOps.flattenVisibleOutline(sample)

        assertEquals(sample.nodes.size, outline.size)
        assertEquals(0, outline.first().depth)
        assertEquals(sample.rootNodeId, outline.first().node.id)
        assertEquals(1, outline[1].depth)
        assertEquals(2, outline[2].depth)
        assertTrue(outline[1].hasChildren)
    }

    @Test
    fun `radiant layout adapts bounding box ratio to portrait and landscape screens without overlaps`() {
        val doc = MindMapTreeOps.createSampleProject()

        val portrait = RadiantLayoutEngine.compute(doc, targetAspectRatio = 0.52f)
        val landscape = RadiantLayoutEngine.compute(doc, targetAspectRatio = 2.05f)

        assertEquals(doc.nodes.size, portrait.nodes.size)
        assertEquals(doc.nodes.size, landscape.nodes.size)

        val portraitRatio = portrait.bounds.widthDp / portrait.bounds.heightDp
        val landscapeRatio = landscape.bounds.widthDp / landscape.bounds.heightDp

        assertTrue(
            "Landscape layout should be wider than tall (width=${landscape.bounds.widthDp}, height=${landscape.bounds.heightDp})",
            landscape.bounds.widthDp > landscape.bounds.heightDp
        )
        assertTrue(
            "Landscape bounding ratio ($landscapeRatio) should exceed Portrait ratio ($portraitRatio) while keeping balanced Radiant proportions",
            landscapeRatio > portraitRatio * 1.15f
        )

        listOf(portrait, landscape).forEach { result ->
            for (i in result.nodes.indices) {
                for (j in i + 1 until result.nodes.size) {
                    val a = result.nodes[i]
                    val b = result.nodes[j]
                    val overlaps = a.leftDp < b.rightDp &&
                        a.rightDp > b.leftDp &&
                        a.topDp < b.bottomYDp &&
                        a.bottomYDp > b.topDp
                    assertTrue(
                        "Overlap detected between ${a.node.title} and ${b.node.title}",
                        !overlaps
                    )
                }
            }
            for (edge in result.edges) {
                val parent = result.nodeMap[edge.parentId]!!
                val child = result.nodeMap[edge.childId]!!
                val pDist = hypot(parent.centerXDp, parent.centerYDp)
                val cDist = hypot(child.centerXDp, child.centerYDp)
                assertTrue(
                    "Child ${child.node.title} ($cDist) should radiate outward from parent ${parent.node.title} ($pDist)",
                    cDist > pDist
                )
            }
        }
    }

    @Test
    fun `json codec decodes multi-branch indonesian mindmap tree cleanly`() {
        val multiBranchJson = """
            {
              "title": "Alur Rilis Aplikasi Android",
              "tree": {
                "title": "Alur Rilis Aplikasi Android",
                "children": [
                  {
                    "title": "Desain & UI",
                    "children": [
                      { "title": "Tema Ikut Sistem" },
                      { "title": "Prioritas Beranda" }
                    ]
                  },
                  {
                    "title": "Cadangan Perangkat",
                    "children": [
                      { "title": "Documents Default" },
                      { "title": "Anti-Timpa Kosong" }
                    ]
                  },
                  {
                    "title": "Rilis Dual-Repo",
                    "link": "https://chucklebytes482.github.io/petapikir-site/",
                    "children": [
                      { "title": "R8 arm64-v8a < 10 MB" },
                      { "title": "Robolectric Offline" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val codec = MindMapJsonCodec()
        val decoded = codec.decodeDocument(multiBranchJson)
        assertNotNull(decoded)
        assertEquals(10, decoded!!.nodes.size)
        assertEquals("Alur Rilis Aplikasi Android", decoded.title)
        val layout = RadiantLayoutEngine.compute(decoded)
        assertEquals(10, layout.nodes.size)
    }
}
