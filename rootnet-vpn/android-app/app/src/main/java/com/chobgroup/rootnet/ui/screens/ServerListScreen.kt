package com.chobgroup.rootnet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.material3.TextButton
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
import com.chobgroup.rootnet.ui.components.QualityDots
import com.chobgroup.rootnet.ui.components.SecondaryButton
import com.chobgroup.rootnet.ui.components.SearchField
import com.chobgroup.rootnet.ui.components.StatusChip
import com.chobgroup.rootnet.ui.icons.AppIcons
import com.chobgroup.rootnet.vpn.EngineState
import com.chobgroup.rootnet.vpn.VpnEngineService
import com.chobgroup.rootnet.vpn.XrayConfigBuilder
import libXray.LibXray
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
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

    // Live engine state — which config is connecting/connected right now.
    val engineState by EngineState.state
    val activeConfigRaw by EngineState.activeConfig

    // ── Video-gate state ────────────────────────────────────────────────
    var gate by remember { mutableStateOf<GateState?>(null) }
    var gatePurpose by remember { mutableStateOf(GatePurpose.CONNECT) }
    var pendingGateAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Server picker
    var query by remember { mutableStateOf("") }
    var selectedConfig by remember { mutableStateOf(cache.selectedConfig()) }
    var pendingSwitch by remember { mutableStateOf<VpnServer?>(null) }
    var twoColumns by remember { mutableStateOf(cache.twoColumnMode()) }

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

    // BPB-only source. Show the cached list INSTANTLY — never auto-fetch.
    // Servers load ONLY when the user presses the refresh icon.
    LaunchedEffect(Unit) {
        val hidden = cache.hiddenConfigs()
        val cached = cache.cachedServers()
            .filterNot { it.rawConfig in hidden }
            .filter { it.rawConfig.startsWith("vless://") || it.rawConfig.startsWith("trojan://") }
        if (cached.isNotEmpty()) {
            servers = cached
        }
        loading = false
        enrichGeoFlags()
    }

    /** Refresh — every 3rd press plays a picture ad; refetches a random sub. */
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            loading = true
            val fetched = BpbSubRepository.fetchRandomSub()
            if (fetched != null) {
                val visible = fetched.filterNot { it.rawConfig in cache.hiddenConfigs() }
                cache.saveServers(visible)
                cache.setLastSubFetch(System.currentTimeMillis())
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

    /** Refresh — every 3rd press (persisted across app restarts) shows a
     *  PICTURE ad first. If the ad can't load, the refresh still completes. */
    fun refreshGate() {
        val count = cache.refreshCount() + 1
        cache.setRefreshCount(count)
        val doRefresh: () -> Unit = {
            refreshKey++
            scope.launch { snackbar.showSnackbar("Refreshed — new servers loaded") }
            Unit
        }
        if (count % 3 == 0) {
            AdiveryAdsManager.maybeShowInterstitial(onFinished = doRefresh)
                .also { shown -> if (!shown) doRefresh() }
        } else {
            doRefresh()
        }
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
                        if (EngineState.state.value == EngineState.ConnState.CONNECTED ||
                            EngineState.state.value == EngineState.ConnState.CONNECTING
                        ) {
                            scope.launch { snackbar.showSnackbar("Disconnect first — pinging uses the VPN engine") }
                            return@IconButton
                        }
                        scope.launch {
                            pinging = true
                            val updated = servers.toMutableList()
                            // Real config ping via libXray pingBatch (5 per batch).
                            val results = realPingServers(updated)
                            for (i in updated.indices) {
                                results[updated[i].rawConfig]?.let { ms ->
                                    updated[i] = updated[i].copy(pingMs = ms)
                                    servers = updated.toList()
                                }
                            }
                            cache.saveServers(updated)
                            pinging = false
                            scope.launch { snackbar.showSnackbar("Ping test finished") }
                        }
                    },
                    enabled = !loading && !pinging && servers.isNotEmpty() && gate == null,
                ) {
                    if (pinging) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = RootNetColors.AccentNeon, strokeWidth = 2.dp)
                    } else {
                        Icon(AppIcons.Speed, contentDescription = "Ping servers", tint = RootNetColors.AccentNeon)
                    }
                }
                // Refresh — icon only; spins while the list reloads.
                IconButton(
                    onClick = { if (!loading && gate == null) refreshGate() },
                    enabled = !loading && gate == null,
                ) {
                    val spin = rememberInfiniteTransition(label = "refreshSpin").animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                        label = "refreshAngle",
                    )
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh servers",
                        tint = if (loading) RootNetColors.TextMuted else RootNetColors.AccentNeon,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = if (loading) spin.value else 0f },
                    )
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
                        DropdownMenuItem(
                            text = { Text(if (twoColumns) "Layout: 2 columns ✓" else "Layout: 2 columns") },
                            onClick = {
                                menuOpen = false
                                twoColumns = !twoColumns
                                cache.setTwoColumnMode(twoColumns)
                            },
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
                                Text("Tap the refresh icon above to load servers", color = RootNetColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            val all = servers.distinctBy { it.rawConfig }
                            // While pinging, keep the list order FROZEN so each
                            // result visibly lands on its row, one by one.
                            val visible = all
                                .filter {
                                    query.isBlank() ||
                                        it.name.contains(query, true) ||
                                        it.country.contains(query, true)
                                }
                                .let {
                                    if (pinging) it
                                    else it.sortedWith(
                                        compareBy<VpnServer> { s -> if (s.pingMs == null || s.pingMs < 0) 1 else 0 }
                                            .thenBy { s -> s.pingMs ?: Int.MAX_VALUE },
                                    )
                                }

                            @Composable fun HeaderItem(modifier: Modifier = Modifier) {
                                SearchField(
                                    value = query,
                                    onValueChange = { query = it },
                                    hint = "Search locations",
                                    modifier = modifier.padding(bottom = 4.dp),
                                )
                            }
                            @Composable fun EmptyItem(modifier: Modifier = Modifier) {
                                Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (query.isBlank()) "No servers available" else "No matches for \"$query\"",
                                        color = RootNetColors.TextSecondary,
                                        fontSize = 14.sp,
                                    )
                                    if (query.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("Try a different country or name", color = RootNetColors.TextMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                            @Composable fun SectionItem(modifier: Modifier = Modifier) {
                                Text(
                                    "Recommended — fastest first",
                                    color = RootNetColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = modifier.padding(top = 2.dp, bottom = 2.dp, start = 4.dp),
                                )
                            }
                            @Composable fun CardItem(server: VpnServer, modifier: Modifier = Modifier) {
                                val isActive = activeConfigRaw == server.rawConfig &&
                                    (engineState == EngineState.ConnState.CONNECTED ||
                                        engineState == EngineState.ConnState.CONNECTING)
                                ConfigCard(
                                    server = server,
                                    selected = selectedConfig == server.rawConfig,
                                    active = isActive && engineState == EngineState.ConnState.CONNECTED,
                                    connectingThis = isActive && engineState == EngineState.ConnState.CONNECTING,
                                    onSelect = {
                                        if (isActive) {
                                            scope.launch { snackbar.showSnackbar("This is your current tunnel") }
                                        } else if (engineState == EngineState.ConnState.CONNECTED ||
                                            engineState == EngineState.ConnState.CONNECTING
                                        ) {
                                            pendingSwitch = server
                                        } else {
                                            cache.selectServer(server)
                                            selectedConfig = server.rawConfig
                                            scope.launch { snackbar.showSnackbar("Selected ${server.country} — connect from the VPN tab") }
                                        }
                                    },
                                    modifier = modifier,
                                )
                            }

                            if (twoColumns) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { HeaderItem() }
                                    if (visible.isEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyItem() }
                                    } else {
                                        item(span = { GridItemSpan(maxLineSpan) }) { SectionItem() }
                                    }
                                    gridItems(visible, key = { it.rawConfig }) { server -> CardItem(server) }
                                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item { HeaderItem() }
                                    if (visible.isEmpty()) {
                                        item { EmptyItem() }
                                    } else {
                                        item { SectionItem() }
                                    }
                                    items(visible, key = { it.rawConfig }) { server -> CardItem(server) }
                                    item { Spacer(Modifier.height(8.dp)) }
                                }
                            }
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

        // Confirm before silently dropping an active connection.
        pendingSwitch?.let { server ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingSwitch = null },
                title = { Text("Switch server?") },
                text = {
                    Text(
                        "This will disconnect the current session and select ${server.country} as your server.",
                        color = RootNetColors.TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        cache.selectServer(server)
                        selectedConfig = server.rawConfig
                        VpnEngineService.disconnect(context)
                        pendingSwitch = null
                        scope.launch { snackbar.showSnackbar("Switched to ${server.country}") }
                    }) { Text("Switch", color = RootNetColors.AccentNeon, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingSwitch = null }) {
                        Text("Cancel", color = RootNetColors.TextSecondary)
                    }
                },
                containerColor = RootNetColors.BgCard,
            )
        }
    }
}


/** The server card — tap to SELECT (highlight); connecting happens on the VPN tab. */
@Composable
private fun ConfigCard(
    server: VpnServer,
    selected: Boolean,
    active: Boolean,
    connectingThis: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        active -> RootNetColors.AccentNeon
        selected -> RootNetColors.AccentNeon.copy(alpha = 0.55f)
        else -> RootNetColors.CardBorder.copy(alpha = 0.4f)
    }
    GlassCard(
        shape = MaterialTheme.shapes.medium,
        borderColor = borderColor,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Flag inline at text size — never bigger than the label.
                    Text(
                        server.flag.ifBlank { "" },
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                    if (server.flag.isNotBlank()) Spacer(Modifier.width(6.dp))
                    Text(
                        server.country,
                        color = RootNetColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active || selected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            AppIcons.ShieldCheck,
                            contentDescription = if (active) "Active" else "Selected",
                            tint = RootNetColors.AccentNeon,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${server.name} · ${server.type.displayName}",
                    color = RootNetColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(8.dp))
            QualityDots(server.pingMs)
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
        if (connectingThis) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = RootNetColors.Warning, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Connecting…", color = RootNetColors.Warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (active) {
            Spacer(Modifier.height(8.dp))
            Text("Connected — this is your tunnel", color = RootNetColors.AccentNeon, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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


/**
 * REAL config ping via libXray `pingBatch`: spins a temporary Xray instance
 * and pushes an actual request through each outbound — same test v2rayNG
 * does. A TCP-reachable edge with a dead config now correctly shows Timeout.
 * Batches of 5 (libXray limit); delay 10000 = error, 11000 = timeout.
 */
private suspend fun realPingServers(servers: List<VpnServer>): Map<String, Int> =
    withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Int>()
        data class Entry(val rawConfig: String, val xrayJson: String)
        val entries = servers.mapNotNull { s ->
            runCatching {
                Entry(
                    s.rawConfig,
                    XrayConfigBuilder.buildOutboundJson(
                        ConfigNormalizer.normalize(
                            raw = s.rawConfig,
                            configFormat = s.configFormat.name.lowercase(),
                            protocol = s.type.wireName,
                        ),
                    ),
                )
            }.getOrNull()
        }
        entries.chunked(5).forEach { chunk ->
            val configs = JSONArray()
            chunk.forEach { configs.put(JSONObject().put("xrayJson", it.xrayJson)) }
            val request = JSONObject()
                .put("apiVersion", 2)
                .put("method", "pingBatch")
                .put(
                    "payload",
                    JSONObject()
                        .put("configs", configs)
                        .put("timeout", 5)
                        .put("url", "https://cp.cloudflare.com/"),
                )
            val response = runCatching { libXray.LibXray.invoke(request.toString()) }.getOrNull()
                ?: return@forEach
            runCatching {
                val obj = JSONObject(response)
                if (!obj.optBoolean("success", false)) return@runCatching
                val arr = obj.optJSONObject("data")?.optJSONArray("results") ?: return@runCatching
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val entry = chunk.getOrNull(i) ?: continue
                    results[entry.rawConfig] = when {
                        item.optBoolean("success", false) -> {
                            val ms = item.optInt("delay", -1)
                            if (ms in 1..9999) ms else -1
                        }
                        else -> -1
                    }
                }
            }.getOrNull()
        }
        results
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
