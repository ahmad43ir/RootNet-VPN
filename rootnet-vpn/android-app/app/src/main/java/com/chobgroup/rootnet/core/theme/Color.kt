package com.chobgroup.rootnet.core.theme

import androidx.compose.ui.graphics.Color

/**
 * RootNet design tokens — dark-first, premium-minimal VPN palette.
 * Centralized here; every screen reads from this object only.
 */
object RootNetColors {
    // ── Backgrounds ──
    val BgDeepForest = Color(0xFF061A13)   // primary background
    val BgDarkEmerald = Color(0xFF0B241B)  // surface
    val BgCard = Color(0xFF102D22)         // elevated surface
    val BgSunken = Color(0xFF04120C)       // pressed / sunken

    // ── Accent ──
    val AccentNeon = Color(0xFF39F27A)     // primary accent (connect, active)
    val AccentLime = Color(0xFF7CF7A9)     // lighter accent variant
    val AccentDim = Color(0x1A39F27A)      // 10% accent fill

    // ── Text ──
    val TextPrimary = Color(0xFFF4F8F5)
    val TextSecondary = Color(0xFF91A49B)
    val TextMuted = Color(0xFF63756C)

    // ── Lines & fills ──
    val Divider = Color(0xFF19372C)
    val CardBorder = Color(0xFF19372C)
    val GlassBorder = Color(0xFF142E24)

    // ── Status ──
    val Warning = Color(0xFFFFB35C)        // restrained amber
    val WarningDim = Color(0x1AFFB35C)
    val Error = Color(0xFFFF6B5E)          // restrained red-orange
    val ErrorDim = Color(0x14FF6B5E)

    // ── Aliases (legacy names used across screens) ──
    val Success = AccentNeon

    // ── Spacing (8dp system) ──
    const val PAD_SCREEN = 24
}
