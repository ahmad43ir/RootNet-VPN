package com.chobgroup.rootnet.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Full-screen background gradient (near-black green → primary background). */
val BackgroundGradient: Brush = Brush.verticalGradient(
    colors = listOf(Color(0xFF04120C), RootNetColors.BgDeepForest),
)

private val DarkColors = darkColorScheme(
    primary = RootNetColors.AccentNeon,
    onPrimary = RootNetColors.BgDeepForest,
    secondary = RootNetColors.AccentLime,
    onSecondary = RootNetColors.BgDeepForest,
    background = RootNetColors.BgDeepForest,
    onBackground = RootNetColors.TextPrimary,
    surface = RootNetColors.BgDarkEmerald,
    onSurface = RootNetColors.TextPrimary,
    surfaceVariant = RootNetColors.BgCard,
    onSurfaceVariant = RootNetColors.TextSecondary,
    outline = RootNetColors.CardBorder,
    error = RootNetColors.Error,
    onError = Color.White,
)

private val RootNetShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun RootNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        shapes = RootNetShapes,
        content = content,
    )
}
