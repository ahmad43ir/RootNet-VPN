package com.chobgroup.admin_vlesshub.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AdminBackgroundGradient: Brush = Brush.verticalGradient(
    colors = listOf(AdminColors.BgDeepForest, Color(0xFF07120C)),
)

private val DarkColors = darkColorScheme(
    primary = AdminColors.AccentRed,
    onPrimary = AdminColors.BgDeepForest,
    secondary = AdminColors.AccentOrange,
    onSecondary = AdminColors.BgDeepForest,
    background = AdminColors.BgDeepForest,
    onBackground = AdminColors.TextPrimary,
    surface = AdminColors.BgDarkEmerald,
    onSurface = AdminColors.TextPrimary,
    surfaceVariant = AdminColors.BgCard,
    onSurfaceVariant = AdminColors.TextSecondary,
    outline = AdminColors.CardBorder,
    error = AdminColors.ErrorRed,
    onError = Color.White,
)

private val AdminShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun AdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        shapes = AdminShapes,
        content = content,
    )
}
