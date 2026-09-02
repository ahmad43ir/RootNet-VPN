package com.chobgroup.rootnet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.data.model.VersionInfo
import com.chobgroup.rootnet.data.remote.VersionCheckService
import com.chobgroup.rootnet.ui.screens.MainShellScreen
import com.chobgroup.rootnet.ui.screens.UpdateRequiredScreen

private sealed interface BootState {
    data object Loading : BootState
    data class Blocked(val versionInfo: VersionInfo) : BootState
    data object Ready : BootState
}

/**
 * Root navigation + boot version gate — v2.0 config launcher.
 * Boot sequence: version check (blocks outdated builds) → straight into the
 * server/config list. No accounts, no login, no guest mode.
 */
@Composable
fun RootNetApp() {
    val context = LocalContext.current

    var boot by remember { mutableStateOf<BootState>(BootState.Loading) }

    LaunchedEffect(Unit) {
        val versionInfo = VersionCheckService.check(context)
        boot = if (versionInfo.isBelowMinimum) BootState.Blocked(versionInfo) else BootState.Ready
    }

    when (val b = boot) {
        BootState.Loading -> Box(
            Modifier.fillMaxSize().background(RootNetColors.BgDeepForest),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = RootNetColors.AccentNeon)
        }
        is BootState.Blocked -> UpdateRequiredScreen(
            updateUrl = b.versionInfo.updateUrl,
            releaseNotes = b.versionInfo.releaseNotes,
        )
        BootState.Ready -> MainShellScreen()
    }
}
