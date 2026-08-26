package com.chobgroup.rootnet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.RootNetColors

/**
 * Shared "cyber-organic" primitives — the app's signature dark neon visual
 * language, factored out so every screen stays consistent. Dark theme only,
 * every color from [RootNetColors].
 */

/**
 * Signature logo orb — neon fill + hairline border rendered as a *static*
 * glow. It used to breathe on an infinite `rememberInfiniteTransition` loop
 * with a heavy 12dp shadow; both were removed in the old-device perf pass
 * (a per-frame animation with shadow elevation is a classic jank source on
 * low-end GPUs). Used for the update blocker, the server placeholder, and
 * profile headers.
 */
@Composable
fun PulsingOrb(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    iconSize: Dp = 32.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = RootNetColors.AccentNeon.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, RootNetColors.AccentNeon.copy(alpha = 0.5f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = RootNetColors.AccentNeon,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Standard translucent glass panel — hairline border + rounded corners, the
 * container used by every card in the app. Pass [onClick] for a tappable
 * card (list rows, buttons).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    borderColor: Color = RootNetColors.CardBorder.copy(alpha = 0.4f),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaceContent: @Composable () -> Unit = {
        Box(Modifier.padding(contentPadding)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = RootNetColors.BgCard.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, borderColor),
            content = surfaceContent,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = RootNetColors.BgCard.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, borderColor),
            content = surfaceContent,
        )
    }
}

/** Letter-spaced uppercase micro-label — e.g. "SESSION REMAINING". */
@Composable
fun MicroLabel(
    text: String,
    color: Color = RootNetColors.TextMuted,
    fontSize: TextUnit = 11.sp,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Rounded capsule chip — status-pill shape, in any status color.
 * `filled = true` uses the color as fill with dark text (neon button style).
 */
@Composable
fun StatusChip(
    text: String,
    color: Color = RootNetColors.AccentNeon,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (filled) color else color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (filled) color else color.copy(alpha = 0.28f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = if (filled) RootNetColors.BgDeepForest else color,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
