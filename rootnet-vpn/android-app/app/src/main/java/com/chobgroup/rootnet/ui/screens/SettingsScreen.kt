package com.chobgroup.rootnet.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.BackgroundGradient
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.data.AppConstants
import com.chobgroup.rootnet.data.AppPreferences
import com.chobgroup.rootnet.ui.components.SectionTitle
import com.chobgroup.rootnet.ui.icons.AppIcons
import com.chobgroup.rootnet.util.ChobGroupLink
import com.chobgroup.rootnet.util.ConfigActions

/**
 * Settings — clean, sectioned, no dashboard clutter:
 * Connection (auto-connect, kill switch, protocol, reconnect) ·
 * Appearance · General (notifications, clients, about, privacy).
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "3.0.0"
    }

    var autoConnect by remember { mutableStateOf(AppPreferences.autoConnect(context)) }
    var reconnect by remember { mutableStateOf(AppPreferences.reconnectAutomatically(context)) }
    var killSwitchDialog by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RootNetColors.PAD_SCREEN.dp, vertical = 16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = RootNetColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // ── Connection ────────────────────────────────────────────────────
        SectionTitle("Connection")
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = RootNetColors.BgDarkEmerald, modifier = Modifier.fillMaxWidth()) {
            Column {
                ToggleRow(
                    title = "Auto-connect",
                    subtitle = "Connect when the app opens",
                    checked = autoConnect,
                ) {
                    autoConnect = it
                    AppPreferences.setAutoConnect(context, it)
                }
                HorizontalDivider(color = RootNetColors.Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { killSwitchDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Kill Switch", color = RootNetColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Block internet if the VPN drops — always-on VPN",
                            color = RootNetColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Text("System", color = RootNetColors.TextMuted, fontSize = 12.sp)
                }
                HorizontalDivider(color = RootNetColors.Divider)
                ToggleRow(
                    title = "Reconnect automatically",
                    subtitle = "Retry once after a dropped session",
                    checked = reconnect,
                ) {
                    reconnect = it
                    AppPreferences.setReconnectAutomatically(context, it)
                }
                HorizontalDivider(color = RootNetColors.Divider)
                InfoLine(title = "Protocol", value = "VLESS · Xray core")
            }
        }
        Spacer(Modifier.height(24.dp))

        // ── General ───────────────────────────────────────────────────────
        SectionTitle("General")
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = RootNetColors.BgDarkEmerald, modifier = Modifier.fillMaxWidth()) {
            Column {
                ActionRow(title = "Notifications", subtitle = "Manage system notifications") {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
                HorizontalDivider(color = RootNetColors.Divider)
                ActionRow(title = "More apps", subtitle = "Explore all Chob Group apps") { ChobGroupLink.open(context) }
                HorizontalDivider(color = RootNetColors.Divider)
                ActionRow(title = "Privacy Policy", subtitle = "How we handle your data") { openUrl(AppConstants.PRIVACY_POLICY_URL) }
                HorizontalDivider(color = RootNetColors.Divider)
                InfoLine(title = "About", value = "RootNet VPN $versionName · Android ${Build.VERSION.RELEASE}")
            }
        }
        Spacer(Modifier.height(24.dp))

        Spacer(Modifier.height(16.dp))
        Text(
            "Configs are fetched live from Supabase. No account needed — your traffic never passes through RootNet servers.",
            color = RootNetColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
    }

    // ── Kill-switch explainer → system Always-on VPN ─────────────────────
    if (killSwitchDialog) {
        AlertDialog(
            onDismissRequest = { killSwitchDialog = false },
            title = { Text("Kill Switch") },
            text = {
                Text(
                    "A real kill switch blocks ALL internet access if the VPN connection drops. " +
                        "Android provides this as \"Always-on VPN\" + \"Block connections without VPN\". " +
                        "Continue to the system VPN settings to enable it for RootNet?",
                    color = RootNetColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    killSwitchDialog = false
                    AppPreferences.setKillSwitchHintShown(context)
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                }) { Text("Open settings", color = RootNetColors.AccentNeon, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { killSwitchDialog = false }) { Text("Cancel", color = RootNetColors.TextSecondary) }
            },
            containerColor = RootNetColors.BgCard,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = RootNetColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = RootNetColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RootNetColors.BgDeepForest,
                checkedTrackColor = RootNetColors.AccentNeon,
                uncheckedThumbColor = RootNetColors.TextSecondary,
                uncheckedTrackColor = RootNetColors.BgCard,
            ),
        )
    }
}

@Composable
private fun InfoLine(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = RootNetColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = RootNetColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = RootNetColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = RootNetColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = RootNetColors.TextMuted, modifier = Modifier.height(18.dp))
    }
}
