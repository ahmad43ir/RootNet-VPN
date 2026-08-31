package com.chobgroup.vlesshub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import com.chobgroup.vlesshub.core.theme.VlessHubColors
import com.chobgroup.vlesshub.ui.icons.AppIcons

/**
 * VlessHub main shell — 3 tabs:
 *  - **Links**   — VLESS/V2Ray config launcher (ported from RootNet v2)
 *  - **MTProto** — Telegram proxy batches (the original ProxyBox feature)
 *  - **Files**   — VPN config files (ported from RootNet v2)
 */
@Composable
fun MainShellScreen() {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = VlessHubColors.BgDeepForest,
        bottomBar = {
            BottomBar(
                items = listOf(
                    BottomBarItem(0, "Links", Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Filled.List),
                    BottomBarItem(1, "MTProto", Icons.AutoMirrored.Outlined.Send, Icons.AutoMirrored.Filled.Send),
                    BottomBarItem(2, "Files", AppIcons.Folder, AppIcons.Folder),
                    BottomBarItem(3, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
                ),
                currentTab = currentTab,
                onSelect = { currentTab = it },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (currentTab) {
                0 -> LinksScreen()
                1 -> HomeScreen()
                2 -> FilesScreen()
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

/** Custom neon bottom bar (same pattern as RootNet's MainShellScreen). */
@Composable
private fun BottomBar(
    items: List<BottomBarItem>,
    currentTab: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(VlessHubColors.BgDeepForest)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = VlessHubColors.CardBorder)
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
                        tint = if (selected) VlessHubColors.AccentNeon else VlessHubColors.TextMuted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        color = if (selected) VlessHubColors.AccentNeon else VlessHubColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
