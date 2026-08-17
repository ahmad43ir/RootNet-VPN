package com.chobgroup.proxybox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chobgroup.proxybox.ads.AdiveryAdsManager
import com.chobgroup.proxybox.core.theme.ProxyBoxTheme
import com.chobgroup.proxybox.ui.HomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Adivery — the only ad network (rewarded refresh gate + banner).
        AdiveryAdsManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            ProxyBoxTheme {
                HomeScreen()
            }
        }
    }
}
