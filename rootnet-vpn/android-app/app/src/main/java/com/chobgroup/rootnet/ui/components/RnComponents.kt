package com.chobgroup.rootnet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.ui.icons.AppIcons

/**
 * Small reusable pieces for the redesigned UI — status badge, info rows,
 * quality dots, primary button, search field, section header.
 */

/** Pill badge with a status dot — e.g. "● Not Protected" / "● Protected". */
@Composable
fun StatusBadge(text: String, color: Color, dotOnly: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        if (!dotOnly) {
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Label-over-value row used inside compact info cards. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = RootNetColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(value, color = RootNetColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        trailing?.invoke()
    }
}

/** Chevron-in-circle trailing affordance for tappable rows. */
@Composable
fun Chevron() {
    Icon(
        AppIcons.ChevronRight,
        contentDescription = null,
        tint = RootNetColors.TextMuted,
        modifier = Modifier.size(18.dp),
    )
}

/** 4-dot connection-quality indicator (ping → Excellent/Good/Fair/Poor). */
@Composable
fun QualityDots(pingMs: Int?) {
    val filled = when {
        pingMs == null || pingMs < 0 -> 0
        pingMs < 100 -> 4
        pingMs < 200 -> 3
        pingMs < 400 -> 2
        else -> 1
    }
    val color = when (filled) {
        4, 3 -> RootNetColors.AccentNeon
        2 -> RootNetColors.Warning
        else -> if (filled == 1) RootNetColors.Error else RootNetColors.TextMuted
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(4) { i ->
            Box(
                Modifier
                    .size(5.dp)
                    .background(if (i < filled) color else color.copy(alpha = 0.25f), CircleShape),
            )
        }
    }
}

/** Filled accent button — the one obvious action on a screen. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    busy: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) RootNetColors.AccentNeon else RootNetColors.BgCard,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                busy -> Box(
                    Modifier
                        .size(16.dp)
                        .padding(2.dp)
                        .background(RootNetColors.BgDeepForest.copy(alpha = 0.3f), CircleShape),
                )
                icon != null -> Icon(icon, contentDescription = null, tint = RootNetColors.BgDeepForest, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(if (icon != null || busy) 8.dp else 0.dp))
            Text(
                label,
                color = if (enabled) RootNetColors.BgDeepForest else RootNetColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Outlined secondary button. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, RootNetColors.Divider),
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(label, color = RootNetColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Rounded search field ("Search locations"). */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = RootNetColors.BgCard,
        border = BorderStroke(1.dp, RootNetColors.Divider),
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(AppIcons.Search, contentDescription = null, tint = RootNetColors.TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = RootNetColors.TextPrimary,
                    fontSize = 14.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(RootNetColors.AccentNeon),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(hint, color = RootNetColors.TextMuted, fontSize = 14.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Uppercase-free section label. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = RootNetColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
