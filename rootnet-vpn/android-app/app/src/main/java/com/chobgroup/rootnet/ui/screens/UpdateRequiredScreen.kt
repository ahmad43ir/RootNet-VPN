package com.chobgroup.rootnet.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.BackgroundGradient
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.ui.components.GlassCard
import com.chobgroup.rootnet.ui.components.MicroLabel
import com.chobgroup.rootnet.ui.components.PulsingOrb
import com.chobgroup.rootnet.ui.icons.AppIcons

/**
 * Full-screen version block — spec §5.6. Reached when the installed app is
 * below `minimumVersion` (boot gate in RootNetApp + connect-time gate).
 */
@Composable
fun UpdateRequiredScreen(updateUrl: String, releaseNotes: String?) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundGradient).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulsingOrb(icon = AppIcons.SystemUpdate, size = 88.dp, iconSize = 40.dp)
        Spacer(Modifier.height(24.dp))
        Text("Update Required", color = RootNetColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "This version of RootNet is no longer supported. Please update to continue.",
            color = RootNetColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        releaseNotes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(20.dp))
            GlassCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MicroLabel(text = "WHAT'S NEW")
                Spacer(Modifier.height(8.dp))
                Text(it, color = RootNetColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))) } },
            colors = ButtonDefaults.buttonColors(containerColor = RootNetColors.AccentNeon, contentColor = RootNetColors.BgDeepForest),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
        ) {
            Text("Update Now", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}
