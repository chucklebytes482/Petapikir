package com.example.layout

import com.example.model.MindMapDocument
import com.example.model.MindMapNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class PositionedNode(
    val node: MindMapNode,
    val depth: Int,
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val hasChildren: Boolean,
    val angleRad: Float = 0f,
    val radiusDp: Float = 0f
) {
    val centerXDp: Float get() = leftDp + widthDp / 2f
    val centerYDp: Float get() = topDp + heightDp / 2f
    val bottomYDp: Float get() = topDp + heightDp
    val rightDp: Float get() = leftDp + widthDp
}

data class LayoutEdge(
    val parentId: String,
    val childId: String,
    val parentDepth: Int,
    val startXDp: Float,
    val startYDp: Float,
    val control1XDp: Float,
    val control1YDp: Float,
    val control2XDp: Float,
    val control2YDp: Float,
    val endXDp: Float,
    val endYDp: Float,
    val colorKey: String
)

data class LayoutBounds(
    val minXDp: Float,
    val minYDp: Float,
    val maxXDp: Float,
    val maxYDp: Float
) {
    val widthDp: Float get() = max(1f, maxXDp - minXDp)
    val heightDp: Float get() = max(1f, maxYDp - minYDp)
    val centerXDp: Float get() = (minXDp + maxXDp) / 2f
    val centerYDp: Float get() = (minYDp + maxYDp) / 2f
}

data class RadiantLayoutResult(
    val nodes: List<PositionedNode>,
    val nodeMap: Map<String, PositionedNode>,
    val edges: List<LayoutEdge>,
    val bounds: LayoutBounds
)

/**
 * Radiant (radial starburst) mind map layout engine with subtle orientation tuning.
 * Places the root topic at (0, 0), allocates weighted 360° angular sectors around it,
 * keeps the classic radial starburst silhouette, applies gentle padding/proportion tuning
 * for portrait and landscape screens, and guarantees zero card overlaps.
 */
object RadiantLayoutEngine {

    private const val ROOT_MIN_WIDTH = 160f
    private const val ROOT_MAX_WIDTH = 224f
    private const val NODE_MIN_WIDTH = 122f
    private const val NODE_MAX_WIDTH = 186f
    private const val RING_CLEARANCE_DP = 36f
    private const val PAD_X_DP = 16f
    private const val PAD_Y_DP = 14f
    private const val RADIAL_STEP_DP = 10f
    private const val MAX_RADIUS_DP = 6000f

    fun compute(
        document: MindMapDocument,
        targetAspectRatio: Float = 1f
    ): RadiantLayoutResult {
        val rawNodeMap = document.nodes.associateBy { it.id }
        val root = rawNodeMap[document.rootNodeId] ?: document.nodes.firstOrNull()
            ?: return RadiantLayoutResult(
                nodes = emptyList(),
                nodeMap = emptyMap(),
                edges = emptyList(),
                bounds = LayoutBounds(0f, 0f, 1f, 1f)
            )

        val aspectRatio = targetAspectRatio.coerceIn(0.50f, 2.20f)
        val isPortrait = aspectRatio < 0.88f
        val isLandscape = aspectRatio > 1.15f

        // Subtle proportion nudge that preserves the true circular Radiant starburst look
        val scaleX = when {
            isPortrait -> 0.90f
            isLandscape -> 1.12f
            else -> 1f
        }
        val scaleY = when {
            isPortrait -> 1.12f
            isLandscape -> 0.90f
            else -> 1f
        }
        val rootMaxWidth = if (isPortrait) 208f else ROOT_MAX_WIDTH
        val nodeMaxWidth = if (isPortrait) 172f else NODE_MAX_WIDTH

        val childrenByParent = document.nodes
            .filter { it.parentId != null && rawNodeMap.containsKey(it.parentId) }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy({ it.orderIndex }, { it.id }))
            }

        val nodeSizes = mutableMapOf<String, Pair<Float, Float>>()
        val subtreeWeights = mutableMapOf<String, Float>()
        val nodeDepths = mutableMapOf<String, Int>()
        val nodesByDepth = mutableMapOf<Int, MutableList<MindMapNode>>()

        fun measure(node: MindMapNode, depth: Int): Float {
            nodeDepths[node.id] = depth
            nodesByDepth.getOrPut(depth) { mutableListOf() }.add(node)
            nodeSizes[node.id] = estimateNodeSize(
                node = node,
                isRoot = depth == 0,
                rootMaxWidth = rootMaxWidth,
                nodeMaxWidth = nodeMaxWidth
            )
            val children = childrenByParent[node.id].orEmpty()
            val weight = if (children.isEmpty()) {
                1f
            } else {
                var sum = 0f
                for (child in children) {
                    sum += measure(child, depth + 1)
                }
                max(1f, sum)
            }
            subtreeWeights[node.id] = weight
            return weight
        }

        measure(root, depth = 0)

        val nodeAngles = mutableMapOf<String, Float>()
        val nodeSpans = mutableMapOf<String, Float>()
        val twoPi = (2.0 * PI).toFloat()

        fun assignWedges(node: MindMapNode, theta: Float, span: Float, depth: Int) {
            nodeAngles[node.id] = theta
            nodeSpans[node.id] = span

            val children = childrenByParent[node.id].orEmpty()
            if (children.isEmpty()) return

            if (depth == 0) {
                when (children.size) {
                    1 -> assignWedges(children[0], 0f, PI.toFloat(), 1)
                    2 -> {
                        assignWedges(children[0], 0f, PI.toFloat(), 1)
                        assignWedges(children[1], PI.toFloat(), PI.toFloat(), 1)
                    }
                    else -> {
                        val totalW = children.sumOf { (subtreeWeights[it.id] ?: 1f).toDouble() }
                            .toFloat().coerceAtLeast(1f)
                        val countF = children.size.toFloat()
                        val shares = children.map { child ->
                            val w = subtreeWeights[child.id] ?: 1f
                            0.72f * (w / totalW) + 0.28f * (1f / countF)
                        }
                        val firstSpan = twoPi * shares.first()
                        var cursor = (-PI / 2.0).toFloat() - firstSpan / 2f
                        children.forEachIndexed { index, child ->
                            val childSpan = twoPi * shares[index]
                            val childTheta = cursor + childSpan / 2f
                            assignWedges(child, childTheta, childSpan, 1)
                            cursor += childSpan
                        }
                    }
                }
            } else {
                val maxFan = if (depth == 1) (PI * 0.82).toFloat() else (PI * 0.62).toFloat()
                val fanSpan = min(span * 0.94f, maxFan)
                if (children.size == 1) {
                    assignWedges(children[0], theta, fanSpan, depth + 1)
                } else {
                    val totalW = children.sumOf { (subtreeWeights[it.id] ?: 1f).toDouble() }
                        .toFloat().coerceAtLeast(1f)
                    val countF = children.size.toFloat()
                    var cursor = theta - fanSpan / 2f
                    children.forEach { child ->
                        val w = subtreeWeights[child.id] ?: 1f
                        val share = 0.72f * (w / totalW) + 0.28f * (1f / countF)
                        val childSpan = fanSpan * share
                        val childTheta = cursor + childSpan / 2f
                        assignWedges(child, childTheta, childSpan, depth + 1)
                        cursor += childSpan
                    }
                }
            }
        }

        assignWedges(root, theta = 0f, span = twoPi, depth = 0)

        val positioned = mutableListOf<PositionedNode>()
        val positionedById = mutableMapOf<String, PositionedNode>()

        val maxDepth = nodesByDepth.keys.maxOrNull() ?: 0
        var prevRingMaxRadius = 0f

        for (depth in 0..maxDepth) {
            val ringNodes = nodesByDepth[depth].orEmpty()
            if (depth == 0) {
                for (node in ringNodes) {
                    val (w, h) = nodeSizes[node.id] ?: (ROOT_MIN_WIDTH to 58f)
                    val placed = PositionedNode(
                        node = node,
                        depth = 0,
                        leftDp = -w / 2f,
                        topDp = -h / 2f,
                        widthDp = w,
                        heightDp = h,
                        hasChildren = childrenByParent[node.id].orEmpty().isNotEmpty(),
                        angleRad = 0f,
                        radiusDp = 0f
                    )
                    positioned.add(placed)
                    positionedById[node.id] = placed
                }
                continue
            }

            val prevHalfExtent = nodesByDepth[depth - 1].orEmpty().maxOfOrNull { prev ->
                val (pw, ph) = nodeSizes[prev.id] ?: (NODE_MIN_WIDTH to 48f)
                hypot(pw / 2f, ph / 2f)
            } ?: 58f
            val currHalfExtent = ringNodes.maxOfOrNull { curr ->
                val (cw, ch) = nodeSizes[curr.id] ?: (NODE_MIN_WIDTH to 48f)
                hypot(cw / 2f, ch / 2f)
            } ?: 58f

            var desiredRingRadius = prevRingMaxRadius + (prevHalfExtent + currHalfExtent + RING_CLEARANCE_DP) * 0.88f
            if (depth == 1) {
                var idealByArc = desiredRingRadius
                for (node in ringNodes) {
                    val (cw, ch) = nodeSizes[node.id] ?: (NODE_MIN_WIDTH to 48f)
                    val span = (nodeSpans[node.id] ?: 0.5f).coerceAtLeast(0.25f)
                    val diag = hypot(cw, ch) * 0.62f + PAD_X_DP
                    idealByArc = max(idealByArc, diag / span)
                }
                desiredRingRadius = min(idealByArc, prevRingMaxRadius + 270f)
            }

            var currentRingMaxR = desiredRingRadius
            for (node in ringNodes) {
                val (w, h) = nodeSizes[node.id] ?: (NODE_MIN_WIDTH to 48f)
                val theta = nodeAngles[node.id] ?: 0f
                val dirX = cos(theta) * scaleX
                val dirY = sin(theta) * scaleY
                val rayScale = hypot(dirX, dirY).coerceAtLeast(0.001f)
                val unitX = dirX / rayScale
                val unitY = dirY / rayScale

                val parentPlaced = node.parentId?.let { positionedById[it] }
                val minParentClearance = if (parentPlaced != null) {
                    val pProj = radialHalfExtent(parentPlaced.widthDp, parentPlaced.heightDp, unitX, unitY)
                    val cProj = radialHalfExtent(w, h, unitX, unitY)
                    val parentDist = hypot(parentPlaced.centerXDp, parentPlaced.centerYDp)
                    (parentDist + pProj + cProj + 30f) / rayScale
                } else {
                    0f
                }

                var r = max(desiredRingRadius, minParentClearance)
                while (r < MAX_RADIUS_DP) {
                    val cx = r * dirX
                    val cy = r * dirY
                    val left = cx - w / 2f
                    val top = cy - h / 2f
                    val right = left + w
                    val bottom = top + h

                    val collides = positioned.any { existing ->
                        left < existing.rightDp + PAD_X_DP &&
                            right + PAD_X_DP > existing.leftDp &&
                            top < existing.bottomYDp + PAD_Y_DP &&
                            bottom + PAD_Y_DP > existing.topDp
                    }
                    if (!collides) {
                        val placed = PositionedNode(
                            node = node,
                            depth = depth,
                            leftDp = left,
                            topDp = top,
                            widthDp = w,
                            heightDp = h,
                            hasChildren = childrenByParent[node.id].orEmpty().isNotEmpty(),
                            angleRad = theta,
                            radiusDp = r * rayScale
                        )
                        positioned.add(placed)
                        positionedById[node.id] = placed
                        currentRingMaxR = max(currentRingMaxR, r)
                        break
                    }
                    r += RADIAL_STEP_DP / rayScale
                }

                if (!positionedById.containsKey(node.id)) {
                    val cx = r * dirX
                    val cy = r * dirY
                    val placed = PositionedNode(
                        node = node,
                        depth = depth,
                        leftDp = cx - w / 2f,
                        topDp = cy - h / 2f,
                        widthDp = w,
                        heightDp = h,
                        hasChildren = childrenByParent[node.id].orEmpty().isNotEmpty(),
                        angleRad = theta,
                        radiusDp = r * rayScale
                    )
                    positioned.add(placed)
                    positionedById[node.id] = placed
                    currentRingMaxR = max(currentRingMaxR, r)
                }
            }
            prevRingMaxRadius = currentRingMaxR
        }

        val edges = mutableListOf<LayoutEdge>()
        for (placedChild in positioned) {
            val parentId = placedChild.node.parentId ?: continue
            val placedParent = positionedById[parentId] ?: continue
            val sx = placedParent.centerXDp
            val sy = placedParent.centerYDp
            val ex = placedChild.centerXDp
            val ey = placedChild.centerYDp
            val dx = ex - sx
            val dy = ey - sy
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val ux = dx / dist
            val uy = dy / dist

            val c1x: Float
            val c1y: Float
            val c2x: Float
            val c2y: Float
            if (placedParent.depth == 0) {
                c1x = sx + ux * (dist * 0.35f)
                c1y = sy + uy * (dist * 0.35f)
                c2x = ex - ux * (dist * 0.30f)
                c2y = ey - uy * (dist * 0.30f)
            } else {
                val pCos = cos(placedParent.angleRad) * scaleX
                val pSin = sin(placedParent.angleRad) * scaleY
                val pLen = hypot(pCos, pSin).coerceAtLeast(0.001f)
                c1x = sx + (pCos / pLen) * (dist * 0.44f)
                c1y = sy + (pSin / pLen) * (dist * 0.44f)
                c2x = ex - ux * (dist * 0.38f)
                c2y = ey - uy * (dist * 0.38f)
            }

            edges.add(
                LayoutEdge(
                    parentId = placedParent.node.id,
                    childId = placedChild.node.id,
                    parentDepth = placedParent.depth,
                    startXDp = sx,
                    startYDp = sy,
                    control1XDp = c1x,
                    control1YDp = c1y,
                    control2XDp = c2x,
                    control2YDp = c2y,
                    endXDp = ex,
                    endYDp = ey,
                    colorKey = placedChild.node.colorKey
                )
            )
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (item in positioned) {
            minX = min(minX, item.leftDp)
            minY = min(minY, item.topDp)
            maxX = max(maxX, item.rightDp)
            maxY = max(maxY, item.bottomYDp)
        }

        val bounds = if (positioned.isEmpty()) {
            LayoutBounds(0f, 0f, 1f, 1f)
        } else {
            LayoutBounds(minX, minY, maxX, maxY)
        }

        return RadiantLayoutResult(
            nodes = positioned,
            nodeMap = positionedById,
            edges = edges,
            bounds = bounds
        )
    }

    private fun radialHalfExtent(w: Float, h: Float, unitX: Float, unitY: Float): Float {
        return abs(unitX) * (w / 2f) + abs(unitY) * (h / 2f)
    }

    private fun estimateNodeSize(
        node: MindMapNode,
        isRoot: Boolean,
        rootMaxWidth: Float,
        nodeMaxWidth: Float
    ): Pair<Float, Float> {
        val trailingIconBonus = if (node.note.isNotBlank()) 28f else 0f
        val minW = (if (isRoot) ROOT_MIN_WIDTH else NODE_MIN_WIDTH) + trailingIconBonus
        val maxW = (if (isRoot) rootMaxWidth else nodeMaxWidth) + trailingIconBonus
        val charWidth = if (isRoot) 9.3f else 8.3f
        val horizontalPadding = 32f + trailingIconBonus
        val rawWidth = node.title.length * charWidth + horizontalPadding
        val width = rawWidth.coerceIn(minW, maxW)

        val usableTextWidth = width - horizontalPadding
        val charsPerLine = max(10, (usableTextWidth / charWidth).toInt())
        val lineCount = max(1, min(3, ceil(node.title.length / charsPerLine.toFloat()).toInt()))

        val basePadding = if (isRoot) 28f else 22f
        val lineHeight = if (isRoot) 20.5f else 18.2f
        val height = max(if (isRoot) 58f else 46f, basePadding + lineCount * lineHeight)
        return width to height
    }
}
