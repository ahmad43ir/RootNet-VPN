package com.chobgroup.rootnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.BackgroundGradient
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.ui.icons.AppIcons

/**
 * Main shell — v3 VPN app. Three tabs:
 *  1. **VPN**      — one-tap connect + status + ad-funded time quota
 *  2. **Servers**  — server list with search, ping, quality, selection
 *  3. **Settings** — connection prefs + about + privacy
 */
@Composable
fun MainShellScreen() {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = RootNetColors.BgDeepForest,
        bottomBar = {
            BottomBar(
                items = listOf(
                    BottomBarItem(0, "VPN", AppIcons.Shield),
                    BottomBarItem(1, "Servers", AppIcons.Globe),
                    BottomBarItem(2, "Settings", Icons.Filled.Settings),
                ),
                currentTab = currentTab,
                onSelect = { currentTab = it },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundGradient),
        ) {
            when (currentTab) {
                0 -> ConnectionScreen(
                    onOpenServers = { currentTab = 1 },
                    onOpenSettings = { currentTab = 2 },
                )
                1 -> ServerListScreen()
                else -> SettingsScreen()
            }
        }
    }
}

private data class BottomBarItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
)

/** Modern bottom bar — single icon per destination, accent on selected. */
@Composable
private fun BottomBar(
    items: List<BottomBarItem>,
    currentTab: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(RootNetColors.BgDeepForest)) {
        HorizontalDivider(color = RootNetColors.GlassBorder)
        Row(Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val selected = item.index == currentTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(item.index) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) RootNetColors.AccentNeon else RootNetColors.TextMuted,
                        modifier = Modifier.height(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label,
                        color = if (selected) RootNetColors.AccentNeon else RootNetColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
            }
        }
    }
}
