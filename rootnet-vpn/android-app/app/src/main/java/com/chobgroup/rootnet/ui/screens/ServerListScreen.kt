package com.chobgroup.rootnet.ui.screens

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chobgroup.rootnet.config.ConfigNormalizer
import com.chobgroup.rootnet.core.theme.BackgroundGradient
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.data.ads.AdiveryAdsManager
import com.chobgroup.rootnet.data.model.ConfigFormat
import com.chobgroup.rootnet.data.model.VpnServer
import com.chobgroup.rootnet.data.repository.BpbSubRepository
import com.chobgroup.rootnet.data.repository.ServerCacheStore
import com.chobgroup.rootnet.data.remote.GeoIpResolver
import com.chobgroup.rootnet.ui.components.GlassCard
import com.chobgroup.rootnet.ui.components.PulsingOrb
import com.chobgroup.rootnet.ui.components.StatusChip
import com.chobgroup.rootnet.ui.icons.AppIcons
import com.chobgroup.rootnet.vpn.EngineState
import com.chobgroup.rootnet.vpn.TimeQuotaManager
import com.chobgroup.rootnet.vpn.VpnEngineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tab 2 — Servers (v3, BPB-only).
 *
 * Refresh picks ONE of the registered BPB subscriptions at random via the
 * backend (`rootnet-api /bpb-sub`), gated by a rewarded VIDEO. Per card the
 * ONLY action is **Connect** (embedded engine, time-quota gated).
 * Overflow menu: sort by ping · remove timed-out · restore hidden.
 */
@Composable
fun ServerListScreen() {
    val context = LocalContext.current
    val cache = ServerCacheStore.instance
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pinging by remember { mutableStateOf(false) }
    var geoBusy by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var connectingConfig by remember { mutableStateOf<String?>(null) }
    var pendingConnect by remember { mutableStateOf<VpnServer?>(null) }

    // ── Video-gate state ────────────────────────────────────────────────
    var gate by remember { mutableStateOf<GateState?>(null) }
    var gatePurpose by remember { mutableStateOf(GatePurpose.CONNECT) }
    var pendingGateAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** GeoIP enrichment — replaces the placeholder flag/country on each card
     *  with the real location of the config's host (`geo-api`, per-host cache).
     *  Runs in the background; cards update as their lookups land. */
    fun enrichGeoFlags() {
        if (geoBusy || servers.isEmpty()) return
        geoBusy = true
        scope.launch {
            val updated = servers.toMutableList()
            for (i in updated.indices) {
                val info = geoLookup(updated[i]) ?: continue
                updated[i] = updated[i].copy(
                    flag = GeoIpResolver.flagEmoji(info.countryCode),
                    country = info.country,
                )
                servers = updated.toList()
            }
            cache.saveServers(updated)
            geoBusy = false
        }
    }

    // BPB-only source; old scraped-server caches are ignored.
    LaunchedEffect(Unit) {
        val hidden = cache.hiddenConfigs()
        val cached = cache.cachedServers()
            .filterNot { it.rawConfig in hidden }
            .filter { it.name.contains("BPB", ignoreCase = true) }
        if (cached.isNotEmpty()) {
            servers = cached
            loading = false
        } else {
            val fetched = BpbSubRepository.fetchRandomSub()
            if (!fetched.isNullOrEmpty()) {
                cache.saveServers(fetched)
                servers = fetched
            }
            loading = false
        }
        enrichGeoFlags()
    }

    /** Refresh — video first, then one random BPB subscription. */
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            loading = true
            val fetched = BpbSubRepository.fetchRandomSub()
            if (fetched != null) {
                val visible = fetched.filterNot { it.rawConfig in cache.hiddenConfigs() }
                cache.saveServers(visible)
                servers = visible
            } else {
                snackbar.showSnackbar("Couldn't reach the subscriptions - try again")
            }
            loading = false
            enrichGeoFlags()
        }
    }

    fun runGate(purpose: GatePurpose, onRewarded: () -> Unit) {
        if (gate != null) return
        gatePurpose = purpose
        pendingGateAction = onRewarded
        gate = GateState.FINDING
        scope.launch {
            val ready = AdiveryAdsManager.awaitRewardedReady(45_000)
            if (!ready) {
                gate = GateState.UNAVAILABLE
                return@launch
            }
            val rewarded = runCatching { AdiveryAdsManager.showRewardedAd() }.getOrDefault(false)
            if (rewarded) {
                gate = null
                pendingGateAction = null
                onRewarded()
            } else {
                gate = GateState.SKIPPED
            }
        }
    }

    fun refreshGate() = runGate(GatePurpose.REFRESH) {
        refreshKey++
        scope.launch { snackbar.showSnackbar("Refreshed - new servers loaded") }
    }

    fun cancelGate() {
        gate = null
        pendingGateAction = null
    }

    fun retryGate() {
        val action = pendingGateAction ?: return
        gate = null
        runGate(gatePurpose, action)
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        // OK only when the user accepted the VPN consent dialog.
        pendingConnect?.let { launchEngine(context, it) }
        pendingConnect = null
    }

    fun doConnect(server: VpnServer) {
        connectingConfig = server.rawConfig
        EngineState.set(EngineState.ConnState.CONNECTING)
        val consent = VpnService.prepare(context)
        if (consent != null) {
            pendingConnect = server
            vpnPermissionLauncher.launch(consent)
            return
        }
        launchEngine(context, server)
    }

    fun connectServer(server: VpnServer) {
        if (EngineState.state.value == EngineState.ConnState.CONNECTED ||
            EngineState.state.value == EngineState.ConnState.CONNECTING
        ) {
            // Already connected — tapping again disconnects.
            VpnEngineService.disconnect(context)
            connectingConfig = null
            return
        }
        if (!TimeQuotaManager.hasTime(context)) {
            // Clock dry — a full rewarded watch grants 30 min (60 min cap).
            runGate(GatePurpose.CONNECT) {
                scope.launch {
                    TimeQuotaManager.syncWithServer(context, watchAd = true)
                        ?: TimeQuotaManager.applyAdGrant(context, connected = false)
                    doConnect(server)
                }
            }
            return
        }
        doConnect(server)
    }

    // ── ⠇ menu actions ────────────────────────────────────────────────
    fun sortByPing() {
        val sorted = servers.sortedWith(
            compareBy<VpnServer> { if (it.pingMs == null || it.pingMs == -1) 1 else 0 }
                .thenBy { it.pingMs ?: Int.MAX_VALUE },
        )
        servers = sorted
        cache.saveServers(sorted)
        scope.launch { snackbar.showSnackbar("Sorted by ping - fastest first") }
    }

    fun removeTimedOut() {
        val timedOut = servers.filter { it.pingMs == -1 }
        if (timedOut.isEmpty()) {
            scope.launch { snackbar.showSnackbar("No timed-out servers to remove") }
            return
        }
        timedOut.forEach { cache.hideConfig(it.rawConfig) }
        val remaining = servers.filterNot { it.pingMs == -1 }
        cache.saveServers(remaining)
        servers = remaining
        scope.launch { snackbar.showSnackbar("Removed ${timedOut.size} timed-out server(s)") }
    }

    fun restoreHidden() {
        cache.restoreAllHidden()
        refreshKey++
        scope.launch { snackbar.showSnackbar("Hidden servers restored") }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(BackgroundGradient).padding(horizontal = 16.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Servers", style = MaterialTheme.typography.headlineSmall, color = RootNetColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Connect through a BPB service", color = RootNetColors.TextSecondary, fontSize = 12.sp)
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            pinging = true
                            val updated = servers.toMutableList()
                            for (i in updated.indices) {
                                val ms = pingServer(updated[i])
                                updated[i] = updated[i].copy(pingMs = ms)
                                servers = updated.toList()
                            }
                            cache.saveServers(updated)
                            pinging = false
                        }
                    },
                    enabled = !loading && !pinging && servers.isNotEmpty() && gate == null,
                ) {
                    if (pinging) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = RootNetColors.AccentNeon, strokeWidth = 2.dp)
                    } else {
                        Icon(AppIcons.NetworkCheck, contentDescription = "Ping servers", tint = RootNetColors.AccentNeon)
                    }
                }
                Surface(
                    onClick = { if (!loading && gate == null) refreshGate() },
                    enabled = !loading && gate == null,
                    shape = RoundedCornerShape(10.dp),
                    color = RootNetColors.AccentNeon.copy(alpha = 0.1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = RootNetColors.AccentNeon, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh servers", color = RootNetColors.AccentNeon, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = !loading && gate == null) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = RootNetColors.AccentNeon)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sort by ping") },
                            onClick = { menuOpen = false; sortByPing() },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove timed-out servers") },
                            onClick = { menuOpen = false; removeTimedOut() },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore hidden servers") },
                            onClick = { menuOpen = false; restoreHidden() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ── List (blurred while a video gate is active) ───────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (gate != null) Modifier.blur(10.dp) else Modifier),
                ) {
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = RootNetColors.AccentNeon)
                        }
                        servers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PulsingOrb(icon = AppIcons.OpenInNew, size = 56.dp, iconSize = 26.dp)
                                Spacer(Modifier.height(16.dp))
                                Text("No servers available", color = RootNetColors.TextSecondary, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Tap Refresh servers", color = RootNetColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(servers.distinctBy { it.rawConfig }, key = { it.rawConfig }) { server ->
                                ConfigCard(
                                    server = server,
                                    connectingThis = connectingConfig == server.rawConfig,
                                    onConnect = { connectServer(server) },
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }

                gate?.let { state ->
                    AdLockOverlay(
                        state = state,
                        purpose = gatePurpose,
                        onRetry = ::retryGate,
                        onCancel = ::cancelGate,
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }
}

/** Launches the engine for a server — shared by the direct path and the
 *  VPN-consent callback (file level so both can call it). */
private fun launchEngine(context: android.content.Context, server: VpnServer) {
    VpnEngineService.connect(
        context,
        raw = server.rawConfig,
        format = server.configFormat.name.lowercase(),
        protocol = server.type.wireName,
    )
}

/** The server card — the only action is Connect. */
@Composable
private fun ConfigCard(
    server: VpnServer,
    connectingThis: Boolean,
    onConnect: () -> Unit,
) {
    GlassCard(
        shape = MaterialTheme.shapes.medium,
        borderColor = RootNetColors.CardBorder.copy(alpha = 0.4f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    server.name,
                    color = RootNetColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val meta = buildList {
                    add("protocol: ${server.type.displayName}")
                    add("${server.flag} ${server.country}".trim())
                }
                Text(
                    meta.joinToString(" · "),
                    color = RootNetColors.TextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            server.pingMs?.let { ms ->
                Spacer(Modifier.size(8.dp))
                val timedOut = ms < 0
                val pingColor = when {
                    timedOut -> RootNetColors.Error
                    ms < 150 -> RootNetColors.AccentNeon
                    ms < 400 -> RootNetColors.Warning
                    else -> RootNetColors.Error
                }
                StatusChip(text = if (timedOut) "Timeout" else "${ms}ms", color = pingColor)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = if (connectingThis) "Connecting…" else "Connect",
                icon = AppIcons.SystemUpdate,
                busy = connectingThis,
                enabled = !connectingThis,
                onClick = onConnect,
            )
        }
    }
}

/**
 * Full-screen lock overlay shown while a video gate is pending. The list
 * behind it is blurred; a skipped ad keeps the screen locked until a full
 * watch — there is no "continue without ad" escape.
 */
@Composable
private fun AdLockOverlay(
    state: GateState,
    purpose: GatePurpose,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val shakePhase by rememberInfiniteTransition(label = "lockShake").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "shake",
    )
    val shaking = state == GateState.SKIPPED
    val shakeFraction = if (shaking) (shakePhase / 0.22f).coerceIn(0f, 1f) else 0f
    val shakePx = if (shaking) {
        (sin(shakeFraction * 2.0 * PI * 3.0) * (1f - shakeFraction) * 6f).toFloat()
    } else 0f

    Box(
        modifier = Modifier.fillMaxSize().background(RootNetColors.BgDeepForest.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 4.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = RootNetColors.TextMuted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            val action = when (purpose) {
                GatePurpose.REFRESH -> "load new servers"
                GatePurpose.CONNECT -> "connect for 30 minutes"
            }
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = RootNetColors.AccentNeon,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer { translationX = shakePx * density },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                when (state) {
                    GateState.FINDING -> "Finding ad…"
                    GateState.SKIPPED -> "Watch the full ad to $action"
                    GateState.UNAVAILABLE -> "Ad unavailable"
                },
                color = RootNetColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (state) {
                    GateState.FINDING -> "Please wait while we load the ad — the screen stays locked until it plays"
                    GateState.SKIPPED -> "Your screen stays locked until the ad is watched"
                    GateState.UNAVAILABLE -> "We couldn't load the ad — the screen stays locked. Try again."
                },
                color = RootNetColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            when (state) {
                GateState.FINDING -> CircularProgressIndicator(
                    color = RootNetColors.AccentNeon,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                )
                GateState.SKIPPED -> NeonGateButton("Watch ad", onClick = onRetry)
                GateState.UNAVAILABLE -> NeonGateButton("Try again", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun NeonGateButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = RootNetColors.AccentNeon,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            color = RootNetColors.BgDeepForest,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = RootNetColors.AccentNeon.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = RootNetColors.AccentNeon, strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = RootNetColors.AccentNeon)
            }
            Spacer(Modifier.width(6.dp))
            Text(label, color = RootNetColors.AccentNeon, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Real TCP connect-time ping to the config's address:port (5s timeout).
 * Returns latency ms, or **-1** on failure/timeout; `null` = not yet pinged.
 */
private suspend fun pingServer(server: VpnServer): Int = withContext(Dispatchers.IO) {
    runCatching {
        val config = ConfigNormalizer.normalize(
            raw = server.rawConfig,
            configFormat = server.configFormat.name.lowercase(),
            protocol = server.type.wireName,
        )
        val start = System.nanoTime()
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(config.address, config.port), 5000)
        } finally {
            socket.close()
        }
        ((System.nanoTime() - start) / 1_000_000).toInt()
    }.getOrDefault(-1)
}

/**
 * GeoIP lookup for a server card: extracts the config's host address and
 * resolves it to a country via `geo-api`. Returns null on any failure.
 */
private suspend fun geoLookup(server: VpnServer) = withContext(Dispatchers.IO) {
    val address = runCatching {
        ConfigNormalizer.normalize(
            raw = server.rawConfig,
            configFormat = server.configFormat.name.lowercase(),
            protocol = server.type.wireName,
        ).address
    }.getOrNull() ?: return@withContext null
    GeoIpResolver.lookupHost(address)
}

/** Video-gate overlay states. */
private enum class GateState { FINDING, SKIPPED, UNAVAILABLE }

/** What the active video gate is for. */
private enum class GatePurpose { REFRESH, CONNECT }
