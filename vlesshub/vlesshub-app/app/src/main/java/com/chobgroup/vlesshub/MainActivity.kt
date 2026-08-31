package com.chobgroup.vlesshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chobgroup.vlesshub.core.theme.VlessHubTheme
import com.chobgroup.vlesshub.data.remote.VersionCheckApi
import com.chobgroup.vlesshub.data.remote.VersionStatus
import com.chobgroup.vlesshub.ui.ForceUpdateScreen
import com.chobgroup.vlesshub.ui.MainShellScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VlessHubTheme {
                var versionStatus by remember { mutableStateOf<VersionStatus?>(null) }

                // Check version on first composition
                LaunchedEffect(Unit) {
                    val config = VersionCheckApi.fetchVersionConfig()
                    if (config != null) {
                        val appVersion = try {
                            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                        } catch (_: Exception) { "0.0.0" }
                        versionStatus = VersionCheckApi.checkStatus(appVersion, config)
                    } else {
                        // Can't reach server — allow app to proceed
                        versionStatus = VersionStatus.UpToDate
                    }
                }

                when (val status = versionStatus) {
                    null -> { /* Loading — show nothing or splash */ }
                    is VersionStatus.ForceUpdate -> ForceUpdateScreen(status.config)
                    VersionStatus.UpToDate -> MainShellScreen()
                }
            }
        }
    }
}
