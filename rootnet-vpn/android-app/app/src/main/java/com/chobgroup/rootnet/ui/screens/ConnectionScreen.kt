package com.chobgroup.rootnet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.data.ads.AdiveryAdsManager
import com.chobgroup.rootnet.data.repository.ServerCacheStore
import com.chobgroup.rootnet.vpn.EngineState
import com.chobgroup.rootnet.vpn.TimeQuotaManager
import com.chobgroup.rootnet.vpn.VpnEngineService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tab 1 — VPN connection. Big connect ring + the ad-funded time quota:
 * watch a video → 30 min; another one only while connected and below
 * 30 min remaining, so the total never exceeds 60 min.
 */
@Composable
fun ConnectionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = ServerCacheStore.instance

    val engineState by EngineState.state
    val connected = engineState == EngineState.ConnState.CONNECTED
    val connecting = engineState == EngineState.ConnState.CONNECTING

    var remainingSec by remember { mutableLongStateOf(TimeQuotaManager.remainingSeconds(context)) }
    var granting by remember { mutableStateOf(false) }

    // Adopt the server ledger on entry (reinstall can't refill the clock).
    LaunchedEffect(Unit) {
        TimeQuotaManager.syncWithServer(context, watchAd = false)?.let { remainingSec = it }
    }

    // Live countdown while connected.
    LaunchedEffect(connected) {
        while (connected) {
            delay(1_000)
            remainingSec = TimeQuotaManager.remainingSeconds(context)
        }
        remainingSec = TimeQuotaManager.remainingSeconds(context)
    }

    val canWatchMore = if (connected) {
        remainingSec < TimeQuotaManager.GRANT_PER_AD_SECONDS &&
            TimeQuotaManager.remainingSeconds(context) < TimeQuotaManager.GRANT_PER_AD_SECONDS
    } else {
        true
    }

    fun watchVideoAndGrant() {
        if (granting) return
        granting = true
        scope.launch {
            val ready = AdiveryAdsManager.awaitRewardedReady(45_000)
            val rewarded = ready && runCatching { AdiveryAdsManager.showRewardedAd() }.getOrDefault(false)
            granting = false
            if (rewarded) {
                remainingSec = TimeQuotaManager.syncWithServer(context, watchAd = true)
                    ?: TimeQuotaManager.applyAdGrant(context, connected)
                cache.resetActionTracking()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(RootNetColors.BgDeepForest),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Text("RootNet VPN", style = MaterialTheme.typography.headlineSmall, color = RootNetColors.TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(40.dp))

            // ── Status ring ──
            Box(contentAlignment = Alignment.Center) {
                val ringColor = when {
                    connected -> RootNetColors.AccentNeon
                    connecting -> RootNetColors.Warning
                    else -> RootNetColors.TextMuted
                }
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(190.dp).padding(14.dp),
                        color = RootNetColors.Warning,
                        strokeWidth = 5.dp,
                    )
                }
                Surface(
                    onClick = {
                        when {
                            connected -> VpnEngineService.disconnect(context)
                            connecting -> Unit
                            else -> {
                                if (!TimeQuotaManager.hasTime(context)) watchVideoAndGrant()
                                else VpnEngineService.connectLastOrFastest(context)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = RootNetColors.BgDarkEmerald,
                    border = BorderStroke(3.dp, ringColor.copy(alpha = 0.7f)),
                    modifier = Modifier.size(190.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                connected -> "TAP TO\nDISCONNECT"
                                connecting -> "CONNECTING…"
                                else -> "TAP TO\nCONNECT"
                            },
                            color = ringColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Remaining-time clock ──
            val minutes = remainingSec / 60
            val seconds = remainingSec % 60
            Text(
                String.format("%02d:%02d", minutes, seconds),
                color = if (remainingSec > 0) RootNetColors.TextPrimary else RootNetColors.TextMuted,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("connection time left", color = RootNetColors.TextMuted, fontSize = 12.sp)

            Spacer(Modifier.height(28.dp))

            // ── Watch-video grant button ──
            val showGrantButton = !granting && (
                !connected && remainingSec == 0L ||
                    (connected && canWatchMore)
                )
            if (showGrantButton || granting) {
                Surface(
                    onClick = { if (!granting) watchVideoAndGrant() },
                    shape = RoundedCornerShape(12.dp),
                    color = RootNetColors.AccentNeon,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (granting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = RootNetColors.BgDeepForest,
                                strokeWidth = 2.dp,
                            )
                        }
                        Spacer(Modifier.width(if (granting) 8.dp else 0.dp))
                        Text(
                            if (connected) "+30 min — watch video" else "Watch video for 30 min",
                            color = RootNetColors.BgDeepForest,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (connected) {
                        "One more video is available while your timer is under 30 min (max 60 min total)."
                    } else {
                        "Watching the full video grants 30 minutes of VPN time."
                    },
                    color = RootNetColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            EngineMessage(engineState, EngineState.message.value)
        }
    }
}

@Composable
private fun EngineMessage(state: EngineState.ConnState, message: String?) {
    if (state == EngineState.ConnState.ERROR && message != null) {
        Text("⚠ $message", color = RootNetColors.Error, fontSize = 12.sp, textAlign = TextAlign.Center)
    } else if (state == EngineState.ConnState.QUOTA_EXHAUSTED) {
        Text("Time is up — watch a video to continue", color = RootNetColors.Warning, fontSize = 12.sp)
    }
}
