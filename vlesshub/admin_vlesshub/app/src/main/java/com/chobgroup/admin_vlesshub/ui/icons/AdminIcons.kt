package com.chobgroup.admin_vlesshub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-built icons for the admin app — minimal set, no extended library.
 * The Delete icon is unique to this admin app.
 */
object AdminIcons {

    /** Standard Material "delete" glyph — the admin-only remove button. */
    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser()
                    .parsePathString(
                        "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2L16,7L6,7v12zM19,4h-3.5l-1,-1h-5l-1,1L5,4v2h14L19,4z",
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

    /** Standard Material "folder" glyph. */
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

    /** Clean speedometer gauge — ping button. */
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
                        "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58L5.07,18A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1 10,10 0,0 0,-0.27 -10.44z",
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
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
