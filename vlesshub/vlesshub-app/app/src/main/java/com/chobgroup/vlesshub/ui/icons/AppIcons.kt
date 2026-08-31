package com.chobgroup.vlesshub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-built Material-style icons â€” replaces the few `material-icons-extended`
 * glyphs the app used, so the ~40 MB extended library can be dropped entirely
 * (major APK-size / startup / old-device win). Fill color is ignored â€” Icon()
 * tints via ColorFilter.
 */
object AppIcons {

    /** Standard Material "apps" glyph (3×3 dot grid) — "More apps" entry. */
    val Apps: ImageVector by lazy {
        ImageVector.Builder(
            name = "Apps",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M4,8h4L8,4L4,4v4zM10,20h4v-4h-4v4zM4,14h4v-4L4,10v4zM4,20h4v-4L4,16v4z" +
                            "M10,14h4v-4h-4v4zM16,4v4h4L20,4h-4zM10,8h4L14,4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "folder" glyph (bottom-bar Files tab; core set has none). */
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M10,4L4,4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2l16,0c1.1,0 2,-0.9 2,-2L22,8c0,-1.1 -0.9,-2 -2,-2l-8,0 -2,-2z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "content_copy" glyph. */
    val ContentCopy: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContentCopy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1z" +
                            "M19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2z" +
                            "M19,21L8,21L8,7h11v14z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "open_in_new" glyph. */
    val OpenInNew: ImageVector by lazy {
        ImageVector.Builder(
            name = "OpenInNew",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M19,19L5,19L5,5h7L12,3L5,3c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2L19,19z" +
                            "M14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41L19,10h2L21,3h-7z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "file_download" glyph (File-tab download button). */
    val FileDownload: ImageVector by lazy {
        ImageVector.Builder(
            name = "FileDownload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M19,9h-4L15,3L9,3v6L5,9l7,7 7,-7zM5,18v2h14v-2L5,18z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "folder_open" glyph (open a downloaded file). */
    val FolderOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "FolderOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M20,6h-8l-2,-2L4,4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,8c0,-1.1 -0.9,-2 -2,-2zM20,18L4,18L4,8h16v10z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "system_update" glyph (update screen orb). */
    val SystemUpdate: ImageVector by lazy {
        ImageVector.Builder(
            name = "SystemUpdate",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M17,1.01L7,1c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2L19,3c0,-1.1 -0.9,-1.99 -2,-1.99z" +
                            "M17,19L7,19L7,5h10v14z" +
                            "M16,13h-3L13,8h-2v5L8,13l4,4 4,-4z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Clean speedometer gauge — ping / speed test button. */
    val Speed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Gauge arc (semicircle)
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58L5.07,18A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1 10,10 0,0 0,-0.27 -10.44z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
            // Needle / pointer
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}
