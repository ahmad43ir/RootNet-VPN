package com.chobgroup.rootnet.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-built Material-style icons — replaces the few `material-icons-extended`
 * glyphs the app used, so the ~40 MB extended library can be dropped entirely
 * (major APK-size / startup / old-device win). Fill color is ignored — Icon()
 * tints via ColorFilter.
 */
object AppIcons {

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

    /** Standard Material "network_check" glyph (ping button). */
    val NetworkCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "NetworkCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M15.9,5c-4.67,0 -8.48,2.9 -10.03,6.93l2.09,2.09C9.05,10.86 12.22,9 15.9,9c0.72,0 1.41,0.1 2.07,0.27l1.66,-1.66C18.26,6.6 17.14,6 15.9,6z" +
                            "M22.92,7.03l-1.66,1.66c0.7,1.02 1.14,2.19 1.25,3.45h2.02c-0.1,-1.78 -0.82,-3.4 -1.61,-5.11z" +
                            "M9.68,17.76l2.47,2.47 2.47,-2.47c-0.68,-0.68 -1.56,-1.1 -2.47,-1.1 -0.91,0 -1.79,0.42 -2.47,1.1z" +
                            "M5.14,13.35l1.99,1.99c0.18,-0.12 0.38,-0.22 0.58,-0.31C6.94,14.49 5.89,13.93 5.14,13.35z" +
                            "M20.31,17.76c0.74,-0.58 1.79,-1.14 2.57,-1.68l-2.09,-2.09c-0.2,0.09 -0.4,0.19 -0.58,0.31L20.31,17.76z" +
                            "M16.92,14.35c-0.76,0.61 -1.8,1.17 -2.58,1.68l2.48,2.48 2.47,-2.47c-0.67,-0.68 -1.55,-1.1 -2.46,-1.1L16.92,14.35z" +
                            "M2.06,11.09v2.02c1.71,0.11 3.32,0.63 4.73,1.46l0.66,-2.38C5.65,11.15 3.93,10.99 2.06,11.09z" +
                            "M3.02,13.35v2.01c0.47,0 0.92,0.05 1.36,0.13l0.58,-2.02C4.4,13.42 3.71,13.35 3.02,13.35z" +
                            "M12,13c-0.55,0 -1,0.45 -1,1s0.45,1 1,1 1,-0.45 1,-1S12.55,13 12,13z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}
