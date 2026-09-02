package com.chobgroup.rootnet.ui.screens

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.data.AppPreferences
import com.chobgroup.rootnet.data.ads.AdiveryAdsManager
import com.chobgroup.rootnet.data.repository.ServerCacheStore
import com.chobgroup.rootnet.data.model.VpnServer
import com.chobgroup.rootnet.ui.components.Chevron
import com.chobgroup.rootnet.ui.components.InfoRow
import com.chobgroup.rootnet.ui.components.PrimaryButton
import com.chobgroup.rootnet.ui.components.SecondaryButton
import com.chobgroup.rootnet.ui.components.StatusBadge
import com.chobgroup.rootnet.ui.icons.AppIcons
import com.chobgroup.rootnet.vpn.EngineState
import com.chobgroup.rootnet.vpn.TimeQuotaManager
import com.chobgroup.rootnet.vpn.VpnEngineService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tab 1 — VPN home. Answers three questions instantly:
 * am I protected · which server · how do I connect.
 * Keeps the ad-funded time quota (video → 30 min, cap 60 min).
 */
@Composable
fun ConnectionScreen(
    onOpenServers: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = ServerCacheStore.instance

    val engineState by EngineState.state
    val engineMessage by EngineState.message
    val connected = engineState == EngineState.ConnState.CONNECTED
    val connecting = engineState == EngineState.ConnState.CONNECTING
    val failed = engineState == EngineState.ConnState.ERROR
    val offline = remember { mutableStateOf(false) }

    var remainingSec by remember { mutableLongStateOf(TimeQuotaManager.remainingSeconds(context)) }
    var granting by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableStateOf(cache.selectedServer()) }

    // ── Live throughput (bytes/sec, sampled from device counters) ──
    var lastCounters by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var speed by remember { mutableStateOf(Pair(0L, 0L)) }
    var sessionBytes by remember { mutableLongStateOf(0L) }
    var connectedTicks by remember { mutableIntStateOf(0) }

    // Honest-state check: Xray may start fine but pass no data (dead config).
    var noTraffic by remember { mutableStateOf(false) }

    // Adopt the server ledger on entry (reinstall can't refill the clock).
    LaunchedEffect(Unit) {
        TimeQuotaManager.syncWithServer(context, watchAd = false)?.let { remainingSec = it }
        if (AppPreferences.autoConnect(context) &&
            !connected && !connecting &&
            TimeQuotaManager.hasTime(context)
        ) {
            VpnEngineService.connectLastOrFastest(context)
        }
    }

    // Live countdown + traffic + connectivity while connected.
    LaunchedEffect(connected) {
        if (connected) {
            lastCounters =
                android.net.TrafficStats.getTotalRxBytes() to android.net.TrafficStats.getTotalTxBytes()
            connectedTicks = 0
            noTraffic = false
        } else {
            lastCounters = null
            speed = 0L to 0L
            sessionBytes = 0L
            noTraffic = false
        }
        while (connected) {
            delay(1_000)
            remainingSec = TimeQuotaManager.remainingSeconds(context)
            val now = android.net.TrafficStats.getTotalRxBytes() to android.net.TrafficStats.getTotalTxBytes()
            lastCounters?.let { (rx0, tx0) ->
                val dRx = (now.first - rx0).coerceAtLeast(0)
                val dTx = (now.second - tx0).coerceAtLeast(0)
                speed = dRx to dTx              // per-second delta = live speed
                sessionBytes += dRx + dTx
            }
            lastCounters = now
            connectedTicks++
            // No meaningful bytes at all after ~10s → tunnel isn't really passing data.
            if (connectedTicks == 10 && sessionBytes < 10_000) noTraffic = true
        }
        remainingSec = TimeQuotaManager.remainingSeconds(context)
        selectedServer = cache.selectedServer()
    }

    // Connectivity watch — disable Connect while offline.
    LaunchedEffect(Unit) {
        while (true) {
            offline.value = !isOnline(context)
            delay(5_000)
        }
    }

    fun refreshSelection() {
        selectedServer = cache.selectedServer()
    }

    fun watchVideoAndGrant(onRewarded: () -> Unit = {}) {
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
                onRewarded()
            }
        }
    }

    fun onMainAction() {
        when {
            // Connected OR mid-connection — tap always cancels/tears down.
            connected || connecting -> VpnEngineService.disconnect(context)
            failed || engineState == EngineState.ConnState.QUOTA_EXHAUSTED -> {
                if (remainingSec <= 0) watchVideoAndGrant() else VpnEngineService.connectLastOrFastest(context)
            }
            else -> {
                if (offline.value) return
                if (!TimeQuotaManager.hasTime(context)) watchVideoAndGrant()
                else VpnEngineService.connectLastOrFastest(context)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = RootNetColors.PAD_SCREEN.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(RootNetColors.AccentDim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Shield, contentDescription = null, tint = RootNetColors.AccentNeon, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "RootNet VPN",
                style = MaterialTheme.typography.titleMedium,
                color = RootNetColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = RootNetColors.TextSecondary, modifier = Modifier.size(22.dp))
            }
        }

        val statusColor = when {
            connected -> RootNetColors.AccentNeon
            connecting -> RootNetColors.Warning
            failed -> RootNetColors.Error
            else -> RootNetColors.Warning
        }
        val statusText = when {
            connected -> "Protected"
            connecting -> "Connecting…"
            failed -> "Not Protected"
            else -> "Not Protected"
        }

        Spacer(Modifier.weight(0.6f))

        // ── Status ──
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (connected) AppIcons.ShieldCheck else AppIcons.Shield,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(statusText, color = statusColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                when {
                    connected -> "Your traffic is encrypted"
                    connecting -> "Establishing secure connection"
                    failed -> "Connection failed"
                    else -> "Tap below to secure your connection"
                },
                color = RootNetColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Connection circle ──
        ConnectCircle(
            connected = connected,
            connecting = connecting,
            disabled = offline.value,
            onClick = ::onMainAction,
        )

        Spacer(Modifier.height(24.dp))

        // ── Timer (compact, never dominant) ──
        val minutes = remainingSec / 60
        val seconds = remainingSec % 60
        val lowTime = remainingSec in 1..300
        Text(
            String.format("%02d:%02d", minutes, seconds),
            color = when {
                remainingSec <= 0 -> RootNetColors.TextMuted
                lowTime -> RootNetColors.Warning
                else -> RootNetColors.TextPrimary
            },
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("connection time", color = RootNetColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = {
                (remainingSec.toFloat() / TimeQuotaManager.MAX_TOTAL_SECONDS.toFloat()).coerceIn(0f, 1f)
            },
            color = if (lowTime) RootNetColors.Warning else RootNetColors.AccentNeon,
            trackColor = RootNetColors.BgCard,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).height(4.dp),
        )

        Spacer(Modifier.height(14.dp))

        // ── Watch-video grant ──
        val canWatchMore = if (connected) {
            remainingSec < TimeQuotaManager.GRANT_PER_AD_SECONDS
        } else true
        val showGrantButton = !granting && (!connected && remainingSec == 0L || (connected && canWatchMore))
        if (showGrantButton) {
            PrimaryButton(
                label = "+30 min — watch video",
                onClick = { watchVideoAndGrant() },
                enabled = !offline.value,
            )
        } else if (granting) {
            PrimaryButton(label = "Loading ad…", onClick = {}, busy = true, enabled = false)
        }

        // ── Live speed ──
        if (connected) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TrafficStat("↓", speed.first, RootNetColors.AccentNeon)
                TrafficStat("↑", speed.second, RootNetColors.TextSecondary)
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Server selector ──
        Surface(
            onClick = onOpenServers,
            shape = RoundedCornerShape(14.dp),
            color = RootNetColors.BgDarkEmerald,
            border = BorderStroke(1.dp, RootNetColors.Divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedServer?.flag ?: "", fontSize = 15.sp)
                if (!selectedServer?.flag.isNullOrBlank()) Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedServer?.country ?: "Automatic",
                        color = RootNetColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        selectedServer?.name ?: "Fastest available server",
                        color = RootNetColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                Chevron()
            }
        }

        // ── Failure details ──
        if (failed && engineMessage != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = RootNetColors.ErrorDim,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "RootNet couldn't establish a secure connection.",
                        color = RootNetColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Try again, or choose another server.",
                        color = RootNetColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        if (offline.value) {
            Spacer(Modifier.height(10.dp))
            Text(
                "No internet connection — check your network",
                color = RootNetColors.Warning,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        } else if (noTraffic && connected) {
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = onOpenServers,
                shape = RoundedCornerShape(12.dp),
                color = RootNetColors.WarningDim,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Connected, but no data is flowing",
                        color = RootNetColors.Warning,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "This server looks unreachable — tap to pick another one.",
                        color = RootNetColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** The one obvious action — large circular connect/disconnect button. */
@Composable
private fun ConnectCircle(
    connected: Boolean,
    connecting: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    val ringColor by animateColorAsState(
        targetValue = when {
            connected -> RootNetColors.AccentNeon
            connecting -> RootNetColors.Warning
            disabled -> RootNetColors.Divider
            else -> RootNetColors.TextSecondary.copy(alpha = 0.55f)
        },
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "ringColor",
    )

    // Subtle breathing glow while protected — restrained by design.
    val glow = if (connected) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0.25f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowAlpha",
        ).value
    } else 0f

    Box(contentAlignment = Alignment.Center) {
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(184.dp).padding(10.dp),
                color = RootNetColors.Warning,
                strokeWidth = 3.dp,
            )
        }
        Surface(
            onClick = onClick,
            enabled = !disabled || connected || connecting,
            shape = CircleShape,
            color = RootNetColors.BgCard,
            border = BorderStroke(2.dp, ringColor),
            modifier = Modifier
                .size(176.dp)
                .drawBehind {
                    if (glow > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(RootNetColors.AccentNeon.copy(alpha = glow), Color.Transparent),
                            ),
                            radius = size.minDimension * 0.75f,
                        )
                    }
                },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    when {
                        connected -> AppIcons.ShieldCheck
                        connecting -> AppIcons.Bolt
                        else -> AppIcons.Bolt
                    },
                    contentDescription = null,
                    tint = ringColor,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        connected -> "DISCONNECT"
                        connecting -> "CANCEL"
                        else -> "CONNECT"
                    },
                    color = if (disabled) RootNetColors.TextMuted else ringColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}

@Composable
private fun TrafficStat(arrow: String, bytesPerSec: Long, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(arrow, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(
            formatSpeed(bytesPerSec),
            color = RootNetColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec < 1024 -> "$bytesPerSec B/s"
    bytesPerSec < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
    else -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
}

private fun isOnline(context: android.content.Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
    val net = cm.activeNetwork ?: return@runCatching false
    val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(true)
