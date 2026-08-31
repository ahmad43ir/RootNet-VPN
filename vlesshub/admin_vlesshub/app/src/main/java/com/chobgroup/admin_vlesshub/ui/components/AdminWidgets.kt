package com.chobgroup.admin_vlesshub.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.admin_vlesshub.core.theme.AdminColors

/** Simple translucent glass card — no animation, no blur. */
@Composable
fun AdminGlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AdminColors.BgCard.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, AdminColors.CardBorder.copy(alpha = 0.4f)),
    ) {
        Box(Modifier.padding(contentPadding)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** Rounded capsule chip for ping status / file format labels. */
@Composable
fun AdminStatusChip(
    text: String,
    color: Color = AdminColors.AccentRed,
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
            color = if (filled) AdminColors.BgDeepForest else color,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
