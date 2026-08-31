package com.chobgroup.vlesshub.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chobgroup.vlesshub.ads.AdiveryAdsManager
import com.chobgroup.vlesshub.config.ConfigNormalizer
import com.chobgroup.vlesshub.core.theme.BackgroundGradient
import com.chobgroup.vlesshub.core.theme.VlessHubColors
import com.chobgroup.vlesshub.data.model.ConfigFormat
import com.chobgroup.vlesshub.data.model.VpnFile
import com.chobgroup.vlesshub.data.model.VpnServer
import com.chobgroup.vlesshub.data.remote.GeoIpResolver
import com.chobgroup.vlesshub.data.repository.RemoteServerRepository
import com.chobgroup.vlesshub.data.repository.RemoteVpnFileRepository
import com.chobgroup.vlesshub.data.repository.ServerCacheStore
import com.chobgroup.vlesshub.ui.components.GlassCard
import com.chobgroup.vlesshub.ui.components.PulsingOrb
import com.chobgroup.vlesshub.ui.components.StatusChip
import com.chobgroup.vlesshub.ui.icons.AppIcons
import com.chobgroup.vlesshub.util.ConfigActions
import com.chobgroup.vlesshub.util.DownloadStorage
import com.chobgroup.vlesshub.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.sin

/**
 * The **Links** tab — VLESS/V2Ray link/server configs with Copy / Export,
 * TCP ping, and the Adivery ad gates (interstitial every 3rd copy/export,
 * rewarded-video refresh gate, persistent banner).
 *
 * The **Files** tab lives in [FilesScreen] below; both share the ad-gate
 * helpers at the bottom of this file (ported from RootNet v2.2/2.3).
 */
@Composable
fun LinksScreen() {
    val context = LocalContext.current
    val cache = ServerCacheStore.instance
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pinging by remember { mutableStateOf(false) }
    var geoBusy by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    // Pagination — 10 cards at a time, "More" appends the next 10.
    var visibleCount by rememberSaveable { mutableIntStateOf(10) }
    var menuOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf<String?>(null) }
    // Combined Copy/Export counter — every 5th DISTINCT config plays a video.
    // Persisted so closing the app doesn't reset it; a refresh does.
    var actionCount by rememberSaveable { mutableIntStateOf(cache.actionCount("links")) }
    var countedConfigs by remember { mutableStateOf(cache.countedConfigs("links")) }
    var noClientConfig by remember { mutableStateOf<String?>(null) }

    var gate by remember { mutableStateOf<LibGateState?>(null) }
    var gatePurpose by remember { mutableStateOf(LibGatePurpose.UNLOCK) }
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

    // Cache-first load: show the cached list instantly, hit Supabase only on
    // first run (no cache) or explicit refresh.
    LaunchedEffect(Unit) {
        val hidden = cache.hiddenConfigs()
        val cached = cache.cachedServers().filterNot { it.rawConfig in hidden }
        if (cached.isNotEmpty()) {
            servers = cached
            loading = false
        } else {
            val fetched = RemoteServerRepository().fetchServers().filterNot { it.rawConfig in hidden }
            if (fetched.isNotEmpty()) {
                cache.saveServers(fetched)
                servers = fetched
            }
            loading = false
        }
        enrichGeoFlags()
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            loading = true
            val fetched = RemoteServerRepository().fetchServers()
                .filterNot { it.rawConfig in cache.hiddenConfigs() }
            if (fetched.isNotEmpty()) {
                cache.saveServers(fetched)
                servers = fetched
            }
            loading = false
            enrichGeoFlags()
        }
    }

    fun openOrFallback(server: VpnServer) {
        val opened = ConfigActions.openWithDefaultApp(context, server.rawConfig)
        if (!opened) {
            if (ConfigActions.isLinkLike(server.rawConfig)) {
                noClientConfig = server.rawConfig
            } else {
                scope.launch {
                    snackbar.showSnackbar("This config can't be opened directly — copy it and import it in your client")
                }
            }
        }
    }

    fun runGate(purpose: LibGatePurpose, onRewarded: () -> Unit) {
        if (!AdiveryAdsManager.isRewardedConfigured()) {
            onRewarded()
            return
        }
        if (gate != null) return
        gatePurpose = purpose
        pendingGateAction = onRewarded
        gate = LibGateState.FINDING
        scope.launch {
            val ready = AdiveryAdsManager.awaitRewardedReady(45_000)
            if (!ready) {
                gate = LibGateState.UNAVAILABLE
                return@launch
            }
            val rewarded = runCatching { AdiveryAdsManager.showRewardedAd() }.getOrDefault(false)
            if (rewarded) {
                gate = null
                pendingGateAction = null
                onRewarded()
            } else {
                gate = LibGateState.SKIPPED
            }
        }
    }

    fun refreshGate() {
        val doRefresh: () -> Unit = {
            // Clear the lock overlay first so the UI unblocks.
            gate = null
            pendingGateAction = null
            refreshKey++
            visibleCount = 10
            cache.resetActionTracking("links")
            actionCount = 0
            countedConfigs = emptySet()
            scope.launch {
                snackbar.showSnackbar("Refreshed \u2014 pinging servers...")
                pinging = true
                val updated = servers.toMutableList()
                for (i in updated.indices) {
                    val ms = pingServer(updated[i])
                    updated[i] = updated[i].copy(pingMs = ms)
                    servers = updated.toList()
                }
                cache.saveServers(updated)
                pinging = false
                snackbar.showSnackbar("Ping complete")
            }
            Unit
        }
        // Show a picture (interstitial) ad only — try immediately, or
        // prepare + wait up to 10s. No rewarded-video fallback for refresh.
        if (AdiveryAdsManager.maybeShowInterstitial(onFinished = doRefresh)) return
        scope.launch {
            gate = LibGateState.FINDING
            gatePurpose = LibGatePurpose.REFRESH
            pendingGateAction = doRefresh
            val ready = AdiveryAdsManager.awaitInterstitialReady(10_000)
            if (ready && AdiveryAdsManager.maybeShowInterstitial(onFinished = doRefresh)) {
                // Interstitial shown — gate clears when ad closes via onFinished.
            } else {
                // Ad not available — refresh without an ad.
                doRefresh()
            }
        }
        Unit
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

    /** Every 5th DISTINCT Copy/Export tap shows a PICTURE (interstitial) ad.
     *  Counter + counted set persist across app restarts (only a refresh
     *  resets them). If the picture can't show, the rewarded lock rule
     *  applies. */
    fun performGatedAction(configKey: String, action: () -> Unit) {
        if (configKey !in countedConfigs) {
            countedConfigs = countedConfigs + configKey
            actionCount++
            cache.setCountedConfigs(countedConfigs, "links")
            cache.setActionCount(actionCount, "links")
        }
        if (actionCount >= 5) {
            actionCount = 0
            countedConfigs = emptySet()
            cache.setActionCount(0, "links")
            cache.setCountedConfigs(emptySet(), "links")
            val shown = AdiveryAdsManager.maybeShowInterstitial(onFinished = { action() })
            if (!shown) {
                runGate(LibGatePurpose.UNLOCK, onRewarded = { action() })
            }
        } else {
            action()
        }
    }

    fun copyServer(server: VpnServer) {
        performGatedAction(server.rawConfig) {
            ConfigActions.copyToClipboard(context, "VlessHub config", server.rawConfig)
            scope.launch { snackbar.showSnackbar("Config copied — import it into your client") }
        }
    }

    fun exportServer(server: VpnServer) {
        performGatedAction(server.rawConfig) {
            exporting = server.rawConfig
            openOrFallback(server)
            exporting = null
        }
    }

    // ── ⠇ menu actions ────────────────────────────────────────────────────

    fun sortByPing() {
        val sorted = servers.sortedWith(
            compareBy<VpnServer> { if (it.pingMs == null || it.pingMs == -1) 1 else 0 }
                .thenBy { it.pingMs ?: Int.MAX_VALUE },
        )
        servers = sorted
        cache.saveServers(sorted)
        scope.launch { snackbar.showSnackbar("Sorted by ping — fastest first") }
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
            // ── Slim header ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Links", style = MaterialTheme.typography.headlineSmall, color = VlessHubColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Copy or export a config to your VPN client app",
                        color = VlessHubColors.TextSecondary,
                        fontSize = 12.sp,
                    )
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = VlessHubColors.AccentNeon, strokeWidth = 2.dp)
                    } else {
                        Icon(AppIcons.Speed, contentDescription = "Ping servers", tint = VlessHubColors.AccentNeon)
                    }
                }
                // Labeled so the user knows what gets refreshed.
                Surface(
                    onClick = { if (!loading && !pinging && gate == null) refreshGate() },
                    enabled = !loading && !pinging && gate == null,
                    shape = RoundedCornerShape(10.dp),
                    color = VlessHubColors.AccentNeon.copy(alpha = 0.1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = VlessHubColors.AccentNeon, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh links", color = VlessHubColors.AccentNeon, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = !loading && gate == null) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = VlessHubColors.AccentNeon)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sort by ping") },
                            onClick = {
                                menuOpen = false
                                sortByPing()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove timed-out servers") },
                            onClick = {
                                menuOpen = false
                                removeTimedOut()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restore hidden servers") },
                            onClick = {
                                menuOpen = false
                                restoreHidden()
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
                            CircularProgressIndicator(color = VlessHubColors.AccentNeon)
                        }
                        servers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PulsingOrb(icon = AppIcons.OpenInNew, size = 56.dp, iconSize = 26.dp)
                                Spacer(Modifier.height(16.dp))
                                Text("No servers available", color = VlessHubColors.TextSecondary, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Tap ↻ to refresh", color = VlessHubColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                        else -> Column(Modifier.fillMaxSize()) {
                            val distinctServers = remember(servers) { servers.distinctBy { it.rawConfig } }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(distinctServers.take(visibleCount), key = { it.rawConfig }) { server ->
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn(animationSpec = tween(300)),
                                    ) {
                                        ConfigCard(
                                            server = server,
                                            exporting = exporting == server.rawConfig,
                                            onCopy = { copyServer(server) },
                                            onOpen = { exportServer(server) },
                                        )
                                    }
                                }
                                if (visibleCount < distinctServers.size) {
                                    item {
                                        MoreButton(label = "More") {
                                            // Picture ad before each extra page.
                                            val shown = AdiveryAdsManager.maybeShowInterstitial(
                                                onFinished = { visibleCount += 10 },
                                            )
                                            if (!shown) {
                                                runGate(LibGatePurpose.UNLOCK) { visibleCount += 10 }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(8.dp)) }
                            }
                        }
                    }
                }

                gate?.let { state ->
                    AdLockOverlay(
                        state = state,
                        purpose = gatePurpose,
                        refreshLabel = "links",
                        onRetry = ::retryGate,
                        onCancel = ::cancelGate,
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))

        noClientConfig?.let { raw ->
            NoClientDialog(
                onDismiss = { noClientConfig = null },
                onCopy = {
                    noClientConfig = null
                    ConfigActions.copyToClipboard(context, "VlessHub config", raw)
                    scope.launch { snackbar.showSnackbar("Config copied — import it into your client") }
                },
            )
        }
    }
}

/**
 * The **Files** tab — .npvt / .sip / .npv VPN config files with a download
 * manager (download → open state machine, real progress bar, public Downloads
 * save) ported from RootNet v2.2. Download is gated by a one-time-per-file
 * picture ad; Refresh is the rewarded-video gate.
 */
@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val cache = ServerCacheStore.instance
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var files by remember { mutableStateOf<List<VpnFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    // Pagination — 5 file cards at a time; "More" plays a picture ad, then
    // appends the next 5.
    var visibleFileCount by rememberSaveable { mutableIntStateOf(5) }
    var downloadingId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var downloadedFilenames by remember { mutableStateOf(cache.downloadedFiles()) }
    var pendingPublicSave by remember { mutableStateOf<Pair<String, String>?>(null) }

    var gate by remember { mutableStateOf<LibGateState?>(null) }
    var gatePurpose by remember { mutableStateOf(LibGatePurpose.UNLOCK) }
    var pendingGateAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingPublicSave ?: return@rememberLauncherForActivityResult
        pendingPublicSave = null
        val (filename, internalPath) = pending
        val internal = File(internalPath)
        scope.launch {
            if (granted && internal.exists()) {
                val bytes = withContext(Dispatchers.IO) { runCatching { internal.readBytes() }.getOrNull() }
                val location = bytes?.let { DownloadStorage.saveToPublicDownloads(context, filename, it) }
                if (location != null) {
                    internal.delete()
                    cache.saveFileLocation(filename, location)
                    snackbar.showSnackbar("$filename saved to your Downloads folder")
                } else {
                    snackbar.showSnackbar("Downloaded, but couldn't move it to Downloads")
                }
            } else {
                snackbar.showSnackbar("Downloaded to app storage — storage permission denied")
            }
        }
    }

    LaunchedEffect(Unit) {
        val cached = cache.cachedFiles()
        if (cached.isNotEmpty()) files = cached
        val fetched = RemoteVpnFileRepository().fetchFiles()
        if (fetched.isNotEmpty()) {
            cache.saveFiles(fetched)
            files = fetched
        }
        loading = false
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            val fetched = RemoteVpnFileRepository().fetchFiles()
            if (fetched.isNotEmpty()) {
                cache.saveFiles(fetched)
                files = fetched
            }
        }
    }

    fun runGate(purpose: LibGatePurpose, onRewarded: () -> Unit) {
        if (!AdiveryAdsManager.isRewardedConfigured()) {
            onRewarded()
            return
        }
        if (gate != null) return
        gatePurpose = purpose
        pendingGateAction = onRewarded
        gate = LibGateState.FINDING
        scope.launch {
            val ready = AdiveryAdsManager.awaitRewardedReady(45_000)
            if (!ready) {
                gate = LibGateState.UNAVAILABLE
                return@launch
            }
            val rewarded = runCatching { AdiveryAdsManager.showRewardedAd() }.getOrDefault(false)
            if (rewarded) {
                gate = null
                pendingGateAction = null
                onRewarded()
            } else {
                gate = LibGateState.SKIPPED
            }
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

    /** The app-private location a downloaded file is stored at. */
    fun downloadedFileFor(file: VpnFile): File {
        val safeName = file.filename.replace(Regex("[^A-Za-z0-9._\\-]"), "_")
        return File(File(context.filesDir, "downloads"), safeName)
    }

    fun startFileDownload(file: VpnFile) {
        scope.launch {
            downloadingId = file.id
            val internal = downloadedFileFor(file)
            val ok = withContext(Dispatchers.IO) {
                RemoteVpnFileRepository().downloadFile(file, internal) { p ->
                    downloadProgress = downloadProgress + (file.id to p)
                }
            }
            if (!ok) {
                downloadProgress = downloadProgress - file.id
                downloadingId = null
                internal.delete()
                snackbar.showSnackbar("Download failed — check your connection")
                return@launch
            }
            val bytes = withContext(Dispatchers.IO) {
                runCatching { internal.readBytes() }.getOrNull()
            }
            val location = if (bytes != null) {
                withContext(Dispatchers.IO) { DownloadStorage.saveToPublicDownloads(context, file.filename, bytes) }
            } else null
            cache.markFileDownloaded(file.filename)
            // Completed download — counts toward the 3-per-video cycle.
            // Failed / partial downloads never reach this line.
            cache.setFileDownloadsDone(cache.fileDownloadsDone() + 1)
            when {
                location != null -> {
                    internal.delete()
                    cache.saveFileLocation(file.filename, location)
                    snackbar.showSnackbar("${file.filename} downloaded to your Downloads folder")
                }
                bytes != null && DownloadStorage.needsStoragePermission(context) -> {
                    cache.saveFileLocation(file.filename, internal.absolutePath)
                    pendingPublicSave = file.filename to internal.absolutePath
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    snackbar.showSnackbar("${file.filename} downloaded — allow storage access to save it to Downloads")
                }
                else -> {
                    cache.saveFileLocation(file.filename, internal.absolutePath)
                    snackbar.showSnackbar("${file.filename} downloaded")
                }
            }
            downloadProgress = downloadProgress - file.id
            downloadingId = null
            downloadedFilenames = cache.downloadedFiles()
        }
    }

    /**
     * Every 3 COMPLETED file downloads, the next download plays a rewarded
     * VIDEO first (which resets the cycle). Failed or partially-downloaded
     * files never count toward the 3.
     */
    fun downloadFile(file: VpnFile) {
        if (downloadingId != null || gate != null) return
        if (cache.fileDownloadsDone() >= 3) {
            runGate(LibGatePurpose.DOWNLOAD) {
                cache.setFileDownloadsDone(0)
                startFileDownload(file)
            }
        } else {
            startFileDownload(file)
        }
    }

    /** Broad MIME so the chooser lists every candidate — the user picks. */
    fun guessMime(filename: String): String = when (filename.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "txt", "conf" -> "text/plain"
        else -> "application/octet-stream"
    }

    /**
     * Open a downloaded file with the user's chosen app — the standard
     * Android "Open with" chooser (same UX as Telegram/WhatsApp). No
     * decryption, no clipboard: the picked app receives the raw file via a
     * granted read-only content URI.
     */
    fun openDownloadedFile(file: VpnFile) {
        val location = cache.fileLocation(file.filename)
        val local = downloadedFileFor(file)
        val targetPath = location?.takeIf { !it.startsWith("content://") } ?: run {
            if (local.exists()) local.absolutePath else null
        }
        val contentUri: android.net.Uri? = when {
            location != null && location.startsWith("content://") ->
                android.net.Uri.parse(location)
            targetPath != null -> runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(targetPath),
                )
            }.getOrNull()
            else -> null
        }
        if (contentUri == null) {
            if (location == null && !local.exists()) {
                cache.markFileNotDownloaded(file.filename)
                downloadedFilenames = cache.downloadedFiles()
                scope.launch { snackbar.showSnackbar("File was removed — download it again") }
            } else {
                scope.launch { snackbar.showSnackbar("Couldn't open the file") }
            }
            return
        }
        val mime = guessMime(file.filename)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mime)
            addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            // Chooser grants read access to every candidate app.
            clipData = android.content.ClipData.newRawUri("file", contentUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "Open with")
        runCatching { context.startActivity(chooser) }
            .onFailure { scope.launch { snackbar.showSnackbar("No app can open this file") } }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(BackgroundGradient).padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Files", style = MaterialTheme.typography.headlineSmall, color = VlessHubColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "VPN config files shared in the channels",
                        color = VlessHubColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                // Labeled so the user knows what gets refreshed.
                Surface(
                    onClick = {
                        runGate(LibGatePurpose.REFRESH) {
                            refreshKey++
                            visibleFileCount = 5
                            scope.launch { snackbar.showSnackbar("Refreshed — files updated") }
                        }
                    },
                    enabled = !loading && gate == null,
                    shape = RoundedCornerShape(10.dp),
                    color = VlessHubColors.AccentNeon.copy(alpha = 0.1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = VlessHubColors.AccentNeon, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh files", color = VlessHubColors.AccentNeon, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (gate != null) Modifier.blur(10.dp) else Modifier),
                ) {
                    when {
                        loading && files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VlessHubColors.AccentNeon)
                        }
                        files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PulsingOrb(icon = Icons.Filled.Lock, size = 56.dp, iconSize = 26.dp)
                                Spacer(Modifier.height(16.dp))
                                Text("No files yet", color = VlessHubColors.TextSecondary, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Tap ↻ to refresh", color = VlessHubColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            val shownFiles = files.take(visibleFileCount)
                            items(shownFiles, key = { it.id }) { file ->
                                FileCard(
                                    file = file,
                                    downloading = downloadingId == file.id,
                                    progress = downloadProgress[file.id] ?: 0f,
                                    downloaded = file.filename in downloadedFilenames,
                                    onDownload = { downloadFile(file) },
                                    onOpen = { openDownloadedFile(file) },
                                )
                            }
                            if (visibleFileCount < files.size) {
                                item {
                                    MoreButton(label = "More") {
                                        // Picture (interstitial) ad before each
                                        // extra page of files; if it can't show,
                                        // the rewarded lock rule applies.
                                        val shown = AdiveryAdsManager.maybeShowInterstitial(
                                            onFinished = { visibleFileCount += 5 },
                                        )
                                        if (!shown) {
                                            runGate(LibGatePurpose.UNLOCK) { visibleFileCount += 5 }
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }

                gate?.let { state ->
                    AdLockOverlay(
                        state = state,
                        purpose = gatePurpose,
                        refreshLabel = "files",
                        onRetry = ::retryGate,
                        onCancel = ::cancelGate,
                    )
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }
}

// ─── Shared pieces (Links + Files) ────────────────────────────────────────

/** Neon "More" pill shown at the end of a paginated list. */
@Composable
fun MoreButton(label: String = "More", onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = VlessHubColors.AccentNeon.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, VlessHubColors.AccentNeon.copy(alpha = 0.4f)),
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                color = VlessHubColors.AccentNeon,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}


/**
 * Full-screen lock overlay shown while a video gate is pending. The list
 * behind it is blurred; the lock icon shakes every ~3 seconds while the user
 * must watch the ad. A skipped ad keeps the screen locked until a full watch.
 */
@Composable
private fun AdLockOverlay(
    state: LibGateState,
    purpose: LibGatePurpose,
    refreshLabel: String = "list",
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val shakePhase by rememberInfiniteTransition(label = "lockShake").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "shake",
    )
    val shaking = state == LibGateState.SKIPPED
    val shakeFraction = if (shaking) (shakePhase / 0.22f).coerceIn(0f, 1f) else 0f
    val shakePx = if (shaking) {
        (sin(shakeFraction * 2.0 * PI * 3.0) * (1f - shakeFraction) * 6f).toFloat()
    } else 0f

    Box(
        modifier = Modifier.fillMaxSize().background(VlessHubColors.BgDeepForest.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 4.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = VlessHubColors.TextMuted)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            val action = when (purpose) {
                LibGatePurpose.REFRESH -> "refresh your $refreshLabel"
                LibGatePurpose.DOWNLOAD -> "download this file"
                LibGatePurpose.UNLOCK -> "continue"
            }
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = VlessHubColors.AccentNeon,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer { translationX = shakePx * density },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                when (state) {
                    LibGateState.FINDING -> "Finding ad…"
                    LibGateState.SKIPPED -> "Watch the full ad to $action"
                    LibGateState.UNAVAILABLE -> "Ad unavailable"
                },
                color = VlessHubColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (state) {
                    LibGateState.FINDING -> "Please wait while we load the ad — the screen stays locked until it plays"
                    LibGateState.SKIPPED -> "Your screen stays locked until the ad is watched"
                    LibGateState.UNAVAILABLE -> "We couldn't load the ad — the screen stays locked. Try again."
                },
                color = VlessHubColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            when (state) {
                LibGateState.FINDING -> CircularProgressIndicator(
                    color = VlessHubColors.AccentNeon,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                )
                LibGateState.SKIPPED -> NeonGateButton("Watch ad", onClick = onRetry)
                LibGateState.UNAVAILABLE -> NeonGateButton("Try again", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun NeonGateButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = VlessHubColors.AccentNeon,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            color = VlessHubColors.BgDeepForest,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ConfigCard(
    server: VpnServer,
    exporting: Boolean,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    GlassCard(
        shape = MaterialTheme.shapes.medium,
        borderColor = VlessHubColors.CardBorder.copy(alpha = 0.4f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        server.name,
                        color = VlessHubColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                val formatLabel = if (server.configFormat == ConfigFormat.LINK) null
                else server.configFormat.displayName
                val meta = buildList {
                    server.sourceChannel?.let { add("source: $it") }
                    add("protocol: ${server.type.displayName}")
                    add("${server.flag} ${server.country}".trim())
                    if (formatLabel != null) add(formatLabel)
                    server.createdAt?.let { iso ->
                        TimeFormat.formatScrapedTime(iso)?.let { add("🕗 $it") }
                    }
                }
                Text(
                    meta.joinToString(" · "),
                    color = VlessHubColors.TextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            server.pingMs?.let { ms ->
                Spacer(Modifier.size(8.dp))
                val timedOut = ms < 0
                val pingColor = when {
                    timedOut -> VlessHubColors.Error
                    ms < 150 -> VlessHubColors.AccentNeon
                    ms < 400 -> VlessHubColors.Warning
                    else -> VlessHubColors.Error
                }
                StatusChip(text = if (timedOut) "Timeout" else "${ms}ms", color = pingColor)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = "Copy",
                icon = AppIcons.ContentCopy,
                onClick = onCopy,
            )
        }
    }
}

@Composable
private fun FileCard(
    file: VpnFile,
    downloading: Boolean,
    progress: Float,
    downloaded: Boolean,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
) {
    GlassCard(
        shape = MaterialTheme.shapes.medium,
        borderColor = VlessHubColors.CardBorder.copy(alpha = 0.4f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VlessHubColors.BgDarkEmerald.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, VlessHubColors.GlassBorder),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (file.isEncrypted) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Encrypted file",
                                tint = VlessHubColors.Warning,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                file.format.take(4).uppercase(),
                                fontSize = 12.sp,
                                color = VlessHubColors.AccentNeon,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.filename,
                        color = VlessHubColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Source channel (naming template: item = channel) + format.
                    val fileSource = file.sourceChannel?.let { if (it.startsWith("@")) it else "@$it" }
                    Text(
                        buildString {
                            if (fileSource != null) append("source: $fileSource · ")
                            append("protocol: ${file.format}")
                        },
                        color = VlessHubColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    val time = TimeFormat.formatScrapedTime(file.uploadedAt)
                    Text(
                        buildString {
                            append(formatFileSize(file.sizeBytes))
                            if (time != null) append(" · 🕗 $time")
                            if (file.configCount > 0) append(" · ${file.configCount} configs")
                        },
                        color = VlessHubColors.TextMuted,
                        fontSize = 10.5.sp,
                    )
                }
                Spacer(Modifier.size(8.dp))
                if (downloaded) {
                    FileIconButton(
                        icon = AppIcons.FolderOpen,
                        contentDescription = "Open downloaded file",
                        onClick = onOpen,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (downloading) VlessHubColors.AccentNeon.copy(alpha = 0.18f)
                                else VlessHubColors.AccentNeon.copy(alpha = 0.1f),
                            )
                            .clickable(enabled = !downloading, onClick = onDownload),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (downloading) {
                            ShineDownloadIcon()
                        } else {
                            Icon(
                                AppIcons.FileDownload,
                                contentDescription = "Download file",
                                tint = VlessHubColors.AccentNeon,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
            if (downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = VlessHubColors.AccentNeon,
                    trackColor = VlessHubColors.BgDarkEmerald.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** A round icon button used for the Files tab's download → open toggle. */
@Composable
private fun FileIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VlessHubColors.AccentNeon.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = VlessHubColors.AccentNeon, modifier = Modifier.size(24.dp))
    }
}

/**
 * The download icon with a "brighten" sweep — shown ONLY while a download is
 * in progress (masked to the glyph itself via SrcAtop).
 */
@Composable
private fun ShineDownloadIcon() {
    val transition = rememberInfiniteTransition(label = "downloadShine")
    val phase by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "shinePhase",
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .drawWithContent {
                drawContent()
                val bandCenter = size.height * phase
                val bandHalf = size.height * 0.14f
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.5f),
                            1f to Color.Transparent,
                        ),
                        startY = bandCenter - bandHalf,
                        endY = bandCenter + bandHalf,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.FileDownload, contentDescription = "Downloading…", tint = VlessHubColors.AccentNeon, modifier = Modifier.size(24.dp))
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
        color = VlessHubColors.AccentNeon.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = VlessHubColors.AccentNeon, strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = VlessHubColors.AccentNeon)
            }
            Spacer(Modifier.width(6.dp))
            Text(label, color = VlessHubColors.AccentNeon, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Shown when no installed app can open the config URI. */
@Composable
private fun NoClientDialog(onDismiss: () -> Unit, onCopy: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = VlessHubColors.BgCard) {
            Column(Modifier.padding(24.dp)) {
                Text("No app found for this config", color = VlessHubColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Install a VLESS client like v2rayNG, NekoBox or Hiddify, or copy the config and import it manually.",
                    color = VlessHubColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(20.dp))
                Surface(
                    onClick = onCopy,
                    shape = MaterialTheme.shapes.medium,
                    color = VlessHubColors.AccentNeon,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Copy config",
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = VlessHubColors.BgDeepForest,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = VlessHubColors.TextMuted)
                }
            }
        }
    }
}

/** Human file size — "820 B", "3.2 KB", "1.4 MB". */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * Real TCP connect-time ping to the config's address:port (5s timeout).
 * Returns the latency in ms, or **-1 when the ping failed/timed out**;
 * `null` is reserved for "not yet pinged".
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
 * GeoIP lookup for a config card: extracts the config's host address and
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
enum class LibGateState { FINDING, SKIPPED, UNAVAILABLE }

/** What the active video gate is for — Refresh, file download, or an unlock. */
enum class LibGatePurpose { REFRESH, DOWNLOAD, UNLOCK }

/** Safety cap when reading a downloaded file back for Copy (10 MB). */
