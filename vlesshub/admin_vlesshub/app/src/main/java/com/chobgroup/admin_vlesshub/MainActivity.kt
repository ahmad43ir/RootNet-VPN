package com.chobgroup.admin_vlesshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chobgroup.admin_vlesshub.core.theme.AdminTheme
import com.chobgroup.admin_vlesshub.ui.AdminMainShellScreen
import com.chobgroup.admin_vlesshub.ui.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdminTheme {
                var loggedIn by remember { mutableStateOf(false) }

                if (loggedIn) {
                    AdminMainShellScreen()
                } else {
                    LoginScreen(onLogin = { loggedIn = true })
                }
            }
        }
    }
}
