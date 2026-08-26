package com.chobgroup.vlesshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chobgroup.vlesshub.core.theme.VlessHubTheme
import com.chobgroup.vlesshub.ui.MainShellScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Adivery + cache are initialized once in VlessHubApplication.
        enableEdgeToEdge()
        setContent {
            VlessHubTheme {
                MainShellScreen()
            }
        }
    }
}
