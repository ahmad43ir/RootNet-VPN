package com.chobgroup.proxybox.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ProxyBoxDarkColors = darkColorScheme(
    primary = ProxyBoxColors.AccentNeon,
    onPrimary = Color(0xFF052E16),
    secondary = ProxyBoxColors.AccentLime,
    background = ProxyBoxColors.BgDeepForest,
    onBackground = ProxyBoxColors.TextPrimary,
    surface = ProxyBoxColors.BgDarkEmerald,
    onSurface = ProxyBoxColors.TextPrimary,
    surfaceVariant = ProxyBoxColors.BgDeepForest,
    error = ProxyBoxColors.ErrorRed,
    onError = Color.White,
)

@Composable
fun ProxyBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProxyBoxDarkColors,
        content = content,
    )
}
