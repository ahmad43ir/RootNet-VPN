package com.chobgroup.vlesshub.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.vlesshub.core.theme.BackgroundGradient
import com.chobgroup.vlesshub.core.theme.VlessHubColors
import com.chobgroup.vlesshub.data.AppConstants
import com.chobgroup.vlesshub.ui.icons.AppIcons
import com.chobgroup.vlesshub.util.ChobGroupLink

/**
 * Settings tab — About, Privacy policy, Chob Group website, and the support
 * actions for Telegram channel owners ("remove my channel" / "add my channel"
 * emails with prefilled subjects).
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun sendSupportEmail(subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConstants.CONTACT_EMAIL))
            putExtra(
                Intent.EXTRA_SUBJECT,
                subject,
            )
            putExtra(
                Intent.EXTRA_TEXT,
                "Hi VlessHub team,\n\nMy Telegram channel: @\nReason:\n\nThanks,",
            )
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Send email")) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = VlessHubColors.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("About, privacy & support", color = VlessHubColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Website & privacy ────────────────────────────────────────────
        SectionLabel("GENERAL")
        SettingsRow(
            title = "More apps",
            subtitle = "Explore all Chob Group apps",
            onClick = { ChobGroupLink.open(context) },
            icon = AppIcons.Apps,
        )
        SectionDivider()
        SettingsRow(title = "Privacy Policy", subtitle = "How we handle your data") { openUrl(AppConstants.PRIVACY_POLICY_URL) }
        Spacer(Modifier.height(20.dp))

        // ── Support (channel owners) ─────────────────────────────────────
        SectionLabel("SUPPORT — CHANNEL OWNERS")
        SettingsRow(
            title = "Remove my Telegram channel",
            subtitle = "Email us — we'll purge your configs",
        ) { sendSupportEmail(AppConstants.SUBJECT_REMOVE_CHANNEL) }
        SectionDivider()
        SettingsRow(
            title = "Add my Telegram channel",
            subtitle = "Share fresh configs with VlessHub users",
        ) { sendSupportEmail(AppConstants.SUBJECT_ADD_CHANNEL) }
        Spacer(Modifier.height(20.dp))

        // ── About ────────────────────────────────────────────────────────
        SectionLabel("ABOUT")
        SettingsRow(title = "App version", subtitle = "VlessHub 0.2.0", onClick = null)
        Spacer(Modifier.height(8.dp))
        Text(
            "VlessHub is a config hub: it shares VLESS links, VPN files and MTProto proxies scraped from public Telegram channels. It never tunnels your traffic.",
            color = VlessHubColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = VlessHubColors.AccentNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = VlessHubColors.GlassBorder, thickness = 0.5.dp)
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.MailOutline,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VlessHubColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = VlessHubColors.TextSecondary, fontSize = 12.sp)
            }
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = VlessHubColors.AccentNeon.copy(alpha = 0.1f),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = VlessHubColors.AccentNeon,
                    modifier = Modifier.padding(6.dp).size(16.dp),
                )
            }
        }
    }
}
