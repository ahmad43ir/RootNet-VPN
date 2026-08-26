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

    /** Standard Material "speed" glyph (speedometer — ping test). */
    val Speed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22,7.58H5.07A8,8 0,0 1,15.58,6.85l1.85,-1.23A10,10 0,0 0,3.35,19a2,2 0,0 0,1.72,1h13.85a2,2 0,0 0,1.74,-1 10,10 0,0 0,-0.27,-17.44z" +
                            "M10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "shield" glyph (VPN tab / protection). */
    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString("M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5L12,1z")
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "shield" with checkmark (protected state). */
    val ShieldCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5L12,1z" +
                            "M10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9l-8,8z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "bolt" glyph (connect action). */
    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M11,21h-1l1,-7L7.5,14c-0.58,0 -0.57,-0.32 -0.38,-0.66 0.19,-0.34 0.05,-0.08 0.07,-0.12C8.48,10.94 10.42,7.54 13.01,3h1l-1,7h3.51c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15C12.96,17.55 11,21 11,21z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "search" glyph. */
    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5z" +
                            "M9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "language" glyph (globe — servers tab). */
    val Globe: ImageVector by lazy {
        ImageVector.Builder(
            name = "Globe",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 1.84,0.63 3.37,1.91 4.33,3.56zM12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96zM4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2 0,0.68 0.06,1.34 0.14,2L4.26,14zM5.08,16h2.95c0.32,1.25 0.78,2.45 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56zM8.03,8L5.08,8c0.96,-1.66 2.49,-2.93 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43,1.43 -1.08,2.76 -1.91,3.96zM14.34,14L9.66,14c-0.09,-0.66 -0.16,-1.32 -0.16,-2 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32 0.16,2 0,0.68 -0.07,1.34 -0.16,2zM14.59,19.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56zM16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34 -0.14,-2h3.38c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Standard Material "chevron_right" glyph. */
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString("M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }
}
