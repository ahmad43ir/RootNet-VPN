package com.chobgroup.vlesshub.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Full-screen background gradient (deep forest â†’ darker emerald). */
val BackgroundGradient: Brush = Brush.verticalGradient(
    colors = listOf(VlessHubColors.BgDeepForest, Color(0xFF07120C)),
)

private val DarkColors = darkColorScheme(
    primary = VlessHubColors.AccentNeon,
    onPrimary = VlessHubColors.BgDeepForest,
    secondary = VlessHubColors.AccentLime,
    onSecondary = VlessHubColors.BgDeepForest,
    background = VlessHubColors.BgDeepForest,
    onBackground = VlessHubColors.TextPrimary,
    surface = VlessHubColors.BgDarkEmerald,
    onSurface = VlessHubColors.TextPrimary,
    surfaceVariant = VlessHubColors.BgCard,
    onSurfaceVariant = VlessHubColors.TextSecondary,
    outline = VlessHubColors.CardBorder,
    error = VlessHubColors.Error,
    onError = Color.White,
)

private val RootNetShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun VlessHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        shapes = RootNetShapes,
        content = content,
    )
}
