package com.chobgroup.rootnet.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.BackgroundGradient
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.data.AppConstants
import com.chobgroup.rootnet.ui.components.GlassCard
import com.chobgroup.rootnet.ui.components.MicroLabel
import com.chobgroup.rootnet.ui.components.PulsingOrb
import com.chobgroup.rootnet.ui.components.StatusChip
import com.chobgroup.rootnet.util.ConfigActions

/**
 * Settings / About — v2.0 config launcher.
 * No accounts, no premium, no preferences to manage. Just: how the app works,
 * recommended client apps (with install links), and the privacy policy.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "2.0.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingOrb(icon = Icons.Filled.Info, size = 56.dp, iconSize = 26.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = RootNetColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text("About RootNet & how to use configs.", color = RootNetColors.TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(24.dp))

        MicroLabel(text = "HOW IT WORKS")
        Spacer(Modifier.height(10.dp))
        GlassCard(
            shape = MaterialTheme.shapes.medium,
            borderColor = RootNetColors.CardBorder.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StepRow(number = "1", text = "Pick a server and tap Export — the config opens in your installed VPN client (v2rayNG, NekoBox, Hiddify…).")
            StepRow(number = "2", text = "Every 3rd different config you Copy/Export shows a short picture ad first — once it closes, your action completes. Tapping the same config again doesn't count.")
            StepRow(number = "3", text = "No client installed? The app will offer to copy the config so you can import it anywhere.")
        }
        Spacer(Modifier.height(24.dp))

        MicroLabel(text = "RECOMMENDED CLIENTS")
        Spacer(Modifier.height(10.dp))
        GlassCard(
            shape = MaterialTheme.shapes.medium,
            borderColor = RootNetColors.CardBorder.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                ClientRow(
                    name = "v2rayNG",
                    desc = "The classic VLESS/VMess client",
                    onInstall = { ConfigActions.openPlayStore(context, AppConstants.CLIENT_V2RAYNG_PACKAGE) },
                )
                HorizontalDivider(color = RootNetColors.GlassBorder)
                ClientRow(
                    name = "NekoBox",
                    desc = "Multi-protocol, modern UI",
                    onInstall = { ConfigActions.openPlayStore(context, AppConstants.CLIENT_NEKOBOX_PACKAGE) },
                )
                HorizontalDivider(color = RootNetColors.GlassBorder)
                ClientRow(
                    name = "Hiddify",
                    desc = "Easy import, all protocols",
                    onInstall = { ConfigActions.openPlayStore(context, AppConstants.CLIENT_HIDDIFY_PACKAGE) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        MicroLabel(text = "ABOUT")
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = RootNetColors.BgCard.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(1.dp, RootNetColors.CardBorder.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Privacy Policy", color = RootNetColors.TextPrimary) },
                    supportingContent = { Text("How we handle your data", color = RootNetColors.TextSecondary) },
                    leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null, tint = RootNetColors.AccentNeon) },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.PRIVACY_POLICY_URL)))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        StatusChip(
            text = "RootNet $versionName · ${Build.VERSION.RELEASE}",
            color = RootNetColors.TextMuted,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Configs are fetched live from Supabase. No account needed — your traffic never passes through RootNet servers.",
            color = RootNetColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = RootNetColors.AccentNeon.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, RootNetColors.AccentNeon.copy(alpha = 0.25f)),
        ) {
            Text(
                number,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = RootNetColors.AccentNeon,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            color = RootNetColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ClientRow(name: String, desc: String, onInstall: () -> Unit) {
    ListItem(
        headlineContent = { Text(name, color = RootNetColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(desc, color = RootNetColors.TextSecondary) },
        leadingContent = { Icon(Icons.Filled.Phone, contentDescription = null, tint = RootNetColors.AccentNeon) },
        trailingContent = {
            Surface(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                color = RootNetColors.AccentNeon.copy(alpha = 0.12f),
            ) {
                Text(
                    "Install",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = RootNetColors.AccentNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}