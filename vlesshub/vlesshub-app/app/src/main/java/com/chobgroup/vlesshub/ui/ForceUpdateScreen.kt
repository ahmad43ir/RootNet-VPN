package com.chobgroup.vlesshub.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.vlesshub.core.theme.VlessHubColors
import com.chobgroup.vlesshub.data.remote.VersionConfig

/**
 * Full-screen force-update lock overlay.
 *
 * Shown when the app version is below the server's minimum_required_version
 * or when force_update is ON and a newer version exists. The user cannot
 * dismiss this screen — the only action is to tap "Update Now" which opens
 * the download link from the server's update_url.
 */
@Composable
fun ForceUpdateScreen(config: VersionConfig) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VlessHubColors.BgDarkEmerald, VlessHubColors.BgDeepForest),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // Update icon orb
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        VlessHubColors.AccentNeon.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Update",
                    tint = VlessHubColors.AccentNeon,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Update Required",
                color = VlessHubColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "A new version of VlessHub is available.\nPlease update to continue.",
                color = VlessHubColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            // Version info
            if (config.latestVersion.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Latest: v${config.latestVersion}",
                    color = VlessHubColors.AccentNeon,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Release notes
            if (config.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    config.releaseNotes,
                    color = VlessHubColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                )
            }

            Spacer(Modifier.height(32.dp))

            // Update button — full width, neon style
            Button(
                onClick = { openUpdateUrl(context, config.updateUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VlessHubColors.AccentNeon,
                    contentColor = VlessHubColors.BgDeepForest,
                ),
                enabled = config.updateUrl.isNotBlank(),
            ) {
                Text(
                    "Update Now",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Tap to download the latest version",
                color = VlessHubColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

private fun openUpdateUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // No browser available — nothing we can do
    }
}
