package com.chobgroup.admin_vlesshub.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.admin_vlesshub.core.theme.AdminBackgroundGradient
import com.chobgroup.admin_vlesshub.core.theme.AdminColors
import com.chobgroup.admin_vlesshub.data.AdminKeyStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen() {
    var adminKey by remember { mutableStateOf(AdminKeyStore.instance.getAdminKey()) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AdminBackgroundGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("Admin key & app info", color = AdminColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Admin key section
        SectionLabel("ADMIN KEY")
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your admin API key to enable Remove operations on servers, proxies, and files.",
            color = AdminColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = adminKey,
            onValueChange = { adminKey = it; saved = false },
            label = { Text("Admin API Key", color = AdminColors.TextMuted) },
            placeholder = { Text("X-Admin-Key value", color = AdminColors.TextMuted.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AdminColors.TextPrimary,
                unfocusedTextColor = AdminColors.TextPrimary,
                focusedBorderColor = AdminColors.AccentRed,
                unfocusedBorderColor = AdminColors.CardBorder,
                cursorColor = AdminColors.AccentRed,
                focusedContainerColor = AdminColors.BgCard.copy(alpha = 0.4f),
                unfocusedContainerColor = AdminColors.BgCard.copy(alpha = 0.4f),
            ),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = {
                AdminKeyStore.instance.setAdminKey(adminKey.trim())
                saved = true
            },
            shape = RoundedCornerShape(12.dp),
            color = AdminColors.AccentRed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (saved) "\u2713 Key saved" else "Save admin key",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                color = AdminColors.BgDeepForest,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (adminKey.isNotBlank()) {
            Surface(shape = RoundedCornerShape(8.dp), color = AdminColors.SuccessGreen.copy(alpha = 0.1f)) {
                Text(
                    "Remove buttons are visible — key is set",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = AdminColors.SuccessGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Surface(shape = RoundedCornerShape(8.dp), color = AdminColors.AccentOrange.copy(alpha = 0.1f)) {
                Text(
                    "Enter your admin key to enable Remove functionality",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = AdminColors.AccentOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // About
        SectionLabel("ABOUT")
        SettingsRow(title = "App version", subtitle = "Admin VlessHub 1.0.0", onClick = null)
        Spacer(Modifier.height(8.dp))
        Text(
            "Admin app for managing VlessHub servers, proxies, and files. Remove operations require a valid admin key configured above.",
            color = AdminColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AdminColors.AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AdminColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = AdminColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
