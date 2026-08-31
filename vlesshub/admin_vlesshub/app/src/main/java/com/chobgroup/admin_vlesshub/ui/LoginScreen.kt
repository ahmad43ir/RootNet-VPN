package com.chobgroup.admin_vlesshub.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.admin_vlesshub.core.theme.AdminBackgroundGradient
import com.chobgroup.admin_vlesshub.core.theme.AdminColors
import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.AppConstants
import com.chobgroup.admin_vlesshub.data.remote.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val digits = remember { mutableStateListOf(*Array(6) { "" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var codeSent by remember { mutableStateOf(false) }
    var codeRequesting by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf(false) }

    // Countdown timer (5 min = 300 sec)
    var cooldownEnd by remember { mutableLongStateOf(0L) }
    val now = System.currentTimeMillis()
    val remaining = ((cooldownEnd - now) / 1000).coerceAtLeast(0)
    val canResend = remaining == 0L && !codeRequesting

    // Tick the countdown every second
    LaunchedEffect(cooldownEnd) {
        while (cooldownEnd > System.currentTimeMillis()) {
            delay(1000)
        }
    }

    // No auto-send — user must tap "Send code" first

    fun requestNewCode() {
        codeRequesting = true
        frozen = false
        error = null
        for (i in 0 until 6) digits[i] = ""
        scope.launch {
            val ok = requestCode(context)
            codeRequesting = false
            codeSent = ok
            if (ok) {
                cooldownEnd = System.currentTimeMillis() + 5 * 60 * 1000
                focusRequester.requestFocus()
            } else {
                error = "Failed to send code"
            }
        }
    }

    fun attemptVerify() {
        val code = digits.joinToString("")
        if (code.length < 6 || digits.any { it.isEmpty() }) {
            error = "Enter all 6 digits"
            return
        }
        sending = true
        error = null
        frozen = true // freeze while verifying
        scope.launch {
            val result = verifyCode(code, context)
            sending = false
            when {
                result.ok && result.adminKey != null -> {
                    AdminKeyStore.instance.setAdminKey(result.adminKey)
                    onLogin()
                }
                result.error == "expired" -> {
                    error = "Code expired — request a new one"
                    frozen = true // stay frozen
                }
                result.error == "already_used" -> {
                    error = "Code already used — request a new one"
                    frozen = true
                }
                else -> {
                    error = "Wrong code — try again"
                    frozen = true // freeze boxes
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(AdminBackgroundGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(AdminColors.AccentRed.copy(alpha = 0.15f), shape = RoundedCornerShape(40)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "Login", tint = AdminColors.AccentRed, modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text("Admin VlessHub", color = AdminColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            when {
                codeRequesting -> {
                    Text("Sending code to admin…", color = AdminColors.TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = AdminColors.AccentRed, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
                codeSent -> {
                    Text("6-digit code sent to admin", color = AdminColors.TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(32.dp))

                    // 6-digit code input
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val firstEmpty = if (frozen) -1 else digits.indexOfFirst { it.isEmpty() }.coerceAtLeast(0)
                        digits.forEachIndexed { index, value ->
                            CodeDigitBox(
                                digit = value,
                                isFocused = index == firstEmpty && !sending,
                                isError = error != null,
                                onClick = { if (!frozen && !sending) focusRequester.requestFocus() },
                            )
                        }
                    }

                    // Hidden input
                    HiddenCodeInput(digits = digits, focusRequester = focusRequester, frozen = frozen, onAllEntered = { attemptVerify() })

                    // Auto-focus on load
                    LaunchedEffect(codeSent) {
                        if (codeSent && !frozen) focusRequester.requestFocus()
                    }

                    Spacer(Modifier.height(12.dp))
                    error?.let { Text(it, color = AdminColors.ErrorRed, fontSize = 13.sp); Spacer(Modifier.height(8.dp)) }

                    Surface(
                        onClick = { if (!sending && !frozen) attemptVerify() },
                        shape = RoundedCornerShape(14.dp),
                        color = AdminColors.AccentRed,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sending && !frozen,
                    ) {
                        if (sending) {
                            Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AdminColors.BgDeepForest, strokeWidth = 2.5.dp)
                            }
                        } else {
                            Text("Verify", modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), color = AdminColors.BgDeepForest, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 16.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Resend with countdown
                    if (canResend) {
                        Surface(
                            onClick = { requestNewCode() },
                            shape = RoundedCornerShape(10.dp),
                            color = AdminColors.AccentRed.copy(alpha = 0.1f),
                        ) {
                            Text("Resend code", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = AdminColors.AccentRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        val min = remaining / 60
                        val sec = remaining % 60
                        Text(
                            "Resend in %d:%02d".format(min, sec),
                            color = AdminColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
                else -> {
                    // Not sent yet — show send button
                    Spacer(Modifier.height(32.dp))
                    Surface(
                        onClick = { requestNewCode() },
                        shape = RoundedCornerShape(14.dp),
                        color = AdminColors.AccentRed,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !codeRequesting,
                    ) {
                        if (codeRequesting) {
                            Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AdminColors.BgDeepForest, strokeWidth = 2.5.dp)
                            }
                        } else {
                            Text("Send code", modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), color = AdminColors.BgDeepForest, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 16.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Code expires in 5 minutes", color = AdminColors.TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

// ── Code digit box ──────────────────────────────────────────────

@Composable
private fun CodeDigitBox(digit: String, isFocused: Boolean, isError: Boolean, onClick: () -> Unit = {}) {
    val borderColor = when {
        isError -> AdminColors.ErrorRed
        isFocused -> AdminColors.AccentRed
        digit.isNotEmpty() -> AdminColors.AccentRed.copy(alpha = 0.5f)
        else -> AdminColors.CardBorder
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(if (digit.isNotEmpty()) AdminColors.AccentRed.copy(alpha = 0.08f) else AdminColors.BgCard.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(digit, color = if (digit.isNotEmpty()) AdminColors.TextPrimary else AdminColors.TextMuted.copy(alpha = 0.3f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Hidden keyboard input ───────────────────────────────────────

@Composable
private fun HiddenCodeInput(digits: MutableList<String>, focusRequester: FocusRequester, frozen: Boolean, onAllEntered: () -> Unit) {
    val text = remember { mutableStateOf(digits.joinToString("")) }

    LaunchedEffect(digits.toList()) {
        val current = digits.joinToString("")
        if (text.value != current) text.value = current
        if (current.length == 6 && digits.all { it.isNotEmpty() }) onAllEntered()
    }

    if (frozen) {
        // Frozen — clear focus, don't accept input
        Box(Modifier.fillMaxWidth().height(1.dp))
    } else {
        BasicTextField(
            value = text.value,
            onValueChange = { newValue ->
                val filtered = newValue.filter { it.isDigit() }.take(6)
                text.value = filtered
                for (i in 0 until 6) digits[i] = if (i < filtered.length) filtered.substring(i, i + 1) else ""
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(AdminColors.AccentRed),
            singleLine = true,
            decorationBox = {},
        )
    }
}

// ── API calls ───────────────────────────────────────────────────

private data class VerifyResult(val ok: Boolean, val adminKey: String? = null, val error: String? = null)

private suspend fun requestCode(context: android.content.Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        val client = PinnedHttpClient.newClient(callTimeoutMillis = 10_000)
        val body = JSONObject().put("device", deviceInfo)
        val request = Request.Builder()
            .url("${AppConstants.VLESSHUB_API_URL}/request-code")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    } catch (_: Exception) { false }
}

private suspend fun verifyCode(code: String, context: android.content.Context): VerifyResult = withContext(Dispatchers.IO) {
    try {
        val client = PinnedHttpClient.newClient(callTimeoutMillis = 10_000)
        val body = JSONObject().put("code", code)
        val request = Request.Builder()
            .url("${AppConstants.VLESSHUB_API_URL}/verify-code")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val respBody = response.body?.string().orEmpty()
            val json = if (respBody.isNotBlank()) JSONObject(respBody) else JSONObject()
            if (response.isSuccessful && json.optBoolean("ok", false)) {
                VerifyResult(ok = true, adminKey = json.optString("adminKey").ifBlank { null })
            } else {
                VerifyResult(ok = false, error = json.optString("error", "unknown"))
            }
        }
    } catch (_: Exception) {
        VerifyResult(ok = false, error = "network")
    }
}
