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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

/**
 * Main shell — v3 VPN app. Three tabs:
 *  1. **VPN**      — connection ring + ad-funded time quota
 *  2. **Servers**  — server/config list with ping, copy/export/connect
 *  3. **Settings** — about + privacy + client help
 */
@Composable
fun MainShellScreen() {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = RootNetColors.BgDeepForest,
        bottomBar = {
            BottomBar(
                items = listOf(
                    BottomBarItem(0, "VPN", Icons.Outlined.PlayArrow, Icons.Filled.PlayArrow),
                    BottomBarItem(1, "Servers", Icons.Outlined.List, Icons.Filled.List),
                    BottomBarItem(2, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
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
                0 -> ConnectionScreen()
                1 -> ServerListScreen()
                else -> SettingsScreen()
            }
        }
    }
}

private data class BottomBarItem(
    val index: Int,
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
)

/** Custom neon bottom bar (the M3 NavigationBar API isn't resolving in this BOM). */
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
                        if (selected) item.filled else item.outlined,
                        contentDescription = item.label,
                        tint = if (selected) RootNetColors.AccentNeon else RootNetColors.TextMuted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        color = if (selected) RootNetColors.AccentNeon else RootNetColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
