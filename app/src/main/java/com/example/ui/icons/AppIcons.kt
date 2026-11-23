package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight inline vector icons to avoid bundling the heavy material-icons-extended artifact,
 * keeping total APK size well under 10 MB.
 */
object AppIcons {

    val FolderBackup: ImageVector by lazy {
        ImageVector.Builder(
            name = "FolderBackup",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 5f)
                horizontalLineTo(9.5f)
                lineTo(11.5f, 7.5f)
                horizontalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 21f, 9.5f)
                verticalLineTo(17.5f)
                arcTo(2f, 2f, 0f, false, true, 19f, 19.5f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 17.5f)
                close()
                moveTo(9f, 13.5f)
                lineTo(11.2f, 15.7f)
                lineTo(15.5f, 11.4f)
            }
        }.build()
    }

    val FolderOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "FolderOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 5f)
                horizontalLineTo(9.2f)
                lineTo(11f, 7.5f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 9.5f)
                verticalLineTo(11f)
                moveTo(3f, 7f)
                verticalLineTo(17.5f)
                arcTo(2f, 2f, 0f, false, false, 5f, 19.5f)
                horizontalLineTo(18.2f)
                arcTo(2f, 2f, 0f, false, false, 20.1f, 18.1f)
                lineTo(21.6f, 13f)
                arcTo(1.5f, 1.5f, 0f, false, false, 20.15f, 11f)
                horizontalLineTo(6.5f)
                arcTo(2f, 2f, 0f, false, false, 4.6f, 12.4f)
                lineTo(3.2f, 17.5f)
            }
        }.build()
    }

    val Undo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Undo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 14f)
                lineTo(4f, 9f)
                lineTo(9f, 4f)
                moveTo(4f, 9f)
                horizontalLineTo(14.5f)
                curveTo(17.8f, 9f, 20f, 11.2f, 20f, 14.5f)
                curveTo(20f, 17.8f, 17.8f, 20f, 14.5f, 20f)
                horizontalLineTo(11f)
            }
        }.build()
    }

    val Redo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Redo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 14f)
                lineTo(20f, 9f)
                lineTo(15f, 4f)
                moveTo(20f, 9f)
                horizontalLineTo(9.5f)
                curveTo(6.2f, 9f, 4f, 11.2f, 4f, 14.5f)
                curveTo(4f, 17.8f, 6.2f, 20f, 9.5f, 20f)
                horizontalLineTo(13f)
            }
        }.build()
    }

    val FitScreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "FitScreen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 8f)
                verticalLineTo(5f)
                horizontalLineTo(7f)
                moveTo(17f, 5f)
                horizontalLineTo(20f)
                verticalLineTo(8f)
                moveTo(20f, 16f)
                verticalLineTo(19f)
                horizontalLineTo(17f)
                moveTo(7f, 19f)
                horizontalLineTo(4f)
                verticalLineTo(16f)
                moveTo(8f, 10f)
                horizontalLineTo(16f)
                verticalLineTo(14f)
                horizontalLineTo(8f)
                close()
            }
        }.build()
    }

    val MindMapRadiant: ImageVector by lazy {
        ImageVector.Builder(
            name = "MindMapRadiant",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Central hub circle (approximated cleanly with 2 arcs)
                moveTo(15f, 12f)
                arcTo(3f, 3f, 0f, true, true, 9f, 12f)
                arcTo(3f, 3f, 0f, true, true, 15f, 12f)
                // Four radiating spokes + outer nodes
                moveTo(12f, 9f)
                lineTo(12f, 5.2f)
                moveTo(13.6f, 3.6f)
                arcTo(1.6f, 1.6f, 0f, true, true, 10.4f, 3.6f)
                arcTo(1.6f, 1.6f, 0f, true, true, 13.6f, 3.6f)

                moveTo(14.6f, 10.5f)
                lineTo(18.5f, 8.2f)
                moveTo(21.6f, 7.2f)
                arcTo(1.6f, 1.6f, 0f, true, true, 18.4f, 7.2f)
                arcTo(1.6f, 1.6f, 0f, true, true, 21.6f, 7.2f)

                moveTo(14.6f, 13.5f)
                lineTo(18.5f, 15.8f)
                moveTo(21.6f, 16.8f)
                arcTo(1.6f, 1.6f, 0f, true, true, 18.4f, 16.8f)
                arcTo(1.6f, 1.6f, 0f, true, true, 21.6f, 16.8f)

                moveTo(9.4f, 10.5f)
                lineTo(5.5f, 8.2f)
                moveTo(5.6f, 7.2f)
                arcTo(1.6f, 1.6f, 0f, true, true, 2.4f, 7.2f)
                arcTo(1.6f, 1.6f, 0f, true, true, 5.6f, 7.2f)

                moveTo(9.4f, 13.5f)
                lineTo(5.5f, 15.8f)
                moveTo(5.6f, 16.8f)
                arcTo(1.6f, 1.6f, 0f, true, true, 2.4f, 16.8f)
                arcTo(1.6f, 1.6f, 0f, true, true, 5.6f, 16.8f)

                moveTo(12f, 15f)
                lineTo(12f, 18.8f)
                moveTo(13.6f, 20.4f)
                arcTo(1.6f, 1.6f, 0f, true, true, 10.4f, 20.4f)
                arcTo(1.6f, 1.6f, 0f, true, true, 13.6f, 20.4f)
            }
        }.build()
    }

    val ChevronLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 6f)
                lineTo(9f, 12f)
                lineTo(15f, 18f)
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }.build()
    }

    val Link: ImageVector by lazy {
        ImageVector.Builder(
            name = "Link",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 13f)
                arcTo(5f, 5f, 0f, false, false, 17.54f, 13.54f)
                lineTo(20.54f, 10.54f)
                arcTo(5f, 5f, 0f, false, false, 13.47f, 3.47f)
                lineTo(11.75f, 5.18f)
                moveTo(14f, 11f)
                arcTo(5f, 5f, 0f, false, false, 6.46f, 10.46f)
                lineTo(3.46f, 13.46f)
                arcTo(5f, 5f, 0f, false, false, 10.53f, 20.53f)
                lineTo(12.24f, 18.82f)
            }
        }.build()
    }

    val OpenInBrowser: ImageVector by lazy {
        ImageVector.Builder(
            name = "OpenInBrowser",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 13f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 16f, 21f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                verticalLineTo(8f)
                arcTo(2f, 2f, 0f, false, true, 5f, 6f)
                horizontalLineTo(11f)
                moveTo(15f, 3f)
                horizontalLineTo(21f)
                verticalLineTo(9f)
                moveTo(10f, 14f)
                lineTo(21f, 3f)
            }
        }.build()
    }

    val DeleteSingleTopic: ImageVector by lazy {
        ImageVector.Builder(
            name = "DeleteSingleTopic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 15f)
                verticalLineTo(4f)
                moveTo(8f, 8f)
                lineTo(12f, 4f)
                lineTo(16f, 8f)
                moveTo(5f, 19f)
                horizontalLineTo(19f)
            }
        }.build()
    }

    val Fullscreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "Fullscreen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 9f)
                verticalLineTo(4f)
                horizontalLineTo(9f)
                moveTo(15f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(9f)
                moveTo(20f, 15f)
                verticalLineTo(20f)
                horizontalLineTo(15f)
                moveTo(9f, 20f)
                horizontalLineTo(4f)
                verticalLineTo(15f)
                moveTo(9f, 9f)
                lineTo(4.5f, 4.5f)
                moveTo(15f, 9f)
                lineTo(19.5f, 4.5f)
                moveTo(9f, 15f)
                lineTo(4.5f, 19.5f)
                moveTo(15f, 15f)
                lineTo(19.5f, 19.5f)
            }
        }.build()
    }

    val FullscreenExit: ImageVector by lazy {
        ImageVector.Builder(
            name = "FullscreenExit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 9f)
                horizontalLineTo(9f)
                verticalLineTo(5f)
                moveTo(15f, 5f)
                verticalLineTo(9f)
                horizontalLineTo(19f)
                moveTo(19f, 15f)
                horizontalLineTo(15f)
                verticalLineTo(19f)
                moveTo(9f, 19f)
                verticalLineTo(15f)
                horizontalLineTo(5f)
            }
        }.build()
    }

    val ListLayout: ImageVector by lazy {
        ImageVector.Builder(
            name = "ListLayout",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 6f)
                horizontalLineTo(5f)
                moveTo(9f, 6f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(5f)
                moveTo(9f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 18f)
                horizontalLineTo(5f)
                moveTo(9f, 18f)
                horizontalLineTo(20f)
            }
        }.build()
    }
}
