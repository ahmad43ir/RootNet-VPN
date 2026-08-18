package com.chobgroup.rootnet.ui.screens

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
import com.chobgroup.rootnet.config.ConfigNormalizer
import com.chobgroup.rootnet.core.theme.BackgroundGradient
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.data.ads.AdiveryAdsManager
import com.chobgroup.rootnet.data.model.ConfigFormat
import com.chobgroup.rootnet.data.model.VpnFile
import com.chobgroup.rootnet.data.model.VpnServer
import com.chobgroup.rootnet.data.repository.RemoteServerRepository
import com.chobgroup.rootnet.data.repository.RemoteVpnFileRepository
import com.chobgroup.rootnet.data.repository.ServerCacheStore
import com.chobgroup.rootnet.ui.components.GlassCard
import com.chobgroup.rootnet.ui.components.PulsingOrb
import com.chobgroup.rootnet.ui.components.StatusChip
import com.chobgroup.rootnet.ui.icons.AppIcons
import com.chobgroup.rootnet.util.ConfigActions
import com.chobgroup.rootnet.util.DownloadStorage
import com.chobgroup.rootnet.util.TimeFormat
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
 * Server / config list — v2.2 config launcher.
 *
 * Two inner tabs sit above the list:
 *   • **Linky** — the link/server configs (Copy / Export per row).
 *   • **File** — .npvt / .sip / .npv files with a real **download manager**.
 *
 * Ad gates (Adivery only):
 *   • **Download** → picture (interstitial) ad before every download.
 *   • **Refresh** → the only rewarded-video gate left; a full watch reloads
 *     both servers and files.
 *   • Every 3rd **Copy / Export** tap (combined counter) shows an interstitial.
 *
 * Global lock rule (v2.3): if a required ad fails to load or show, the screen
 * locks behind the blur + lock overlay and STAYS locked until a rewarded ad
 * is watched to the end — there is no "continue without ad" escape. A skip
 * also keeps the screen locked; the X button abandons the action entirely.
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
    var refreshKey by remember { mutableStateOf(0) }
    // ⠇ overflow menu (top right).
    var menuOpen by remember { mutableStateOf(false) }
    // rawConfig of the server whose export is currently in progress.
    var exporting by remember { mutableStateOf<String?>(null) }
    // Combined Copy/Export counter — every 3rd tap on a DIFFERENT config
    // shows the interstitial (re-tapping the same config doesn't count).
    var actionCount by remember { mutableIntStateOf(0) }
    var countedConfigs by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Set when "Open" was tapped but no app handles the config.
    var noClientConfig by remember { mutableStateOf<String?>(null) }

    // ── Linky / File tabs + video-gate state ─────────────────────────────
    var listTab by rememberSaveable { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<VpnFile>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }
    // ── File-tab download state ───────────────────────────────────────────
    var downloadingId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf<Map<Long, Float>>(emptyMap()) }
    var downloadedFilenames by remember { mutableStateOf(cache.downloadedFiles()) }
    // File ids whose download ad has already played (once per file).
    var downloadAdShown by remember { mutableStateOf(cache.downloadAdShownIds()) }
    // Downloaded file currently shown in the open dialog (file + raw content).
    var openFile by remember { mutableStateOf<Pair<VpnFile, String>?>(null) }
    // Pending public-save move: (filename, internal path) waiting on the
    // storage-permission result (Android 9 and older only).
    var pendingPublicSave by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Storage permission (API 23–28): on grant, the just-downloaded file is
    // moved from app storage into the public Downloads folder.
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
    // ── Video-gate state ───────────────────────────────────────────────────
    var gate by remember { mutableStateOf<GateState?>(null) }
    var gatePurpose by remember { mutableStateOf(GatePurpose.UNLOCK) }
    // The action to run once the locked ad is watched to the end (retry-safe).
    var pendingGateAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Cache-first load: show the cached list instantly, hit Supabase only on
    // first run (no cache) or explicit refresh. Hidden servers (removed via
    // the ⠇ menu) are filtered out and stay gone until "Restore hidden".
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
    }

    // Explicit refresh — every refresh fetches fresh servers from the backend.
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
        }
    }

    fun ensureFilesLoaded() {
        if (filesLoading) return
        scope.launch {
            filesLoading = true
            if (files.isEmpty()) {
                val cached = cache.cachedFiles()
                if (cached.isNotEmpty()) files = cached
            }
            val fetched = RemoteVpnFileRepository().fetchFiles()
            if (fetched.isNotEmpty()) {
                cache.saveFiles(fetched)
                files = fetched
            }
            filesLoading = false
        }
    }

    // Fetch files whenever the File tab becomes visible. No ad gate on entry
    // (the video ad moved to Refresh only).
    LaunchedEffect(listTab) {
        if (listTab == 1) ensureFilesLoaded()
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

    // ── Video gate (rewarded ad must be watched to the end) ────────────────

    /**
     * Runs the lock gate: blur the list, show \"Finding ad…\" while Adivery
     * loads, then play the rewarded video. Only a full watch ([onRewarded])
     * unlocks — a skip keeps the screen locked.
     */
    fun runGate(purpose: GatePurpose, onRewarded: () -> Unit) {
        if (gate != null) return
        gatePurpose = purpose
        pendingGateAction = onRewarded
        gate = GateState.FINDING
        scope.launch {
            val ready = AdiveryAdsManager.awaitRewardedReady(45_000)
            if (!ready) {
                // Ad couldn't load — the screen STAYS locked; "Try again" re-runs.
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

    /** Refresh: the only remaining video gate — full watch, then both lists reload. */
    fun refreshGate() = runGate(GatePurpose.REFRESH) {
        refreshKey++
        ensureFilesLoaded()
        scope.launch { snackbar.showSnackbar("Refreshed — servers and files updated") }
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

    fun selectTab(tab: Int) {
        // While a gate is active only "Linky" is reachable (it cancels the gate).
        if (gate != null) {
            if (tab == 0) cancelGate()
            return
        }
        when (tab) {
            0 -> listTab = 0
            // No video gate on File-tab entry — the ad moved to Refresh only.
            1 -> {
                listTab = 1
                ensureFilesLoaded()
            }
        }
    }

    /**
     * Every Copy/Export tap on a NEW config counts toward a combined counter;
     * every **3rd distinct config** shows an Adivery interstitial (image ad)
     * first — the action then completes when it closes. Tapping the same
     * config again does NOT advance the counter (only different configs do).
     * If the ad can't show, the global lock rule applies: the screen locks
     * until a rewarded ad is watched to the end.
     */
    fun performGatedAction(configKey: String, action: () -> Unit) {
        // Re-taps of a config already counted in this cycle complete without
        // counting (and without an ad).
        if (configKey !in countedConfigs) {
            countedConfigs = countedConfigs + configKey
            actionCount++
        }
        if (actionCount >= 3) {
            actionCount = 0
            countedConfigs = emptySet()
            val shown = AdiveryAdsManager.maybeShowInterstitial(onFinished = { action() })
            if (!shown) {
                runGate(GatePurpose.UNLOCK, onRewarded = { action() })
            }
        } else {
            action()
        }
    }

    fun copyServer(server: VpnServer) {
        performGatedAction(server.rawConfig) {
            ConfigActions.copyToClipboard(context, "RootNet config", server.rawConfig)
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
            // Saved — now put it where the user can actually reach it.
            val bytes = withContext(Dispatchers.IO) {
                runCatching { internal.readBytes() }.getOrNull()
            }
            val location = if (bytes != null) {
                withContext(Dispatchers.IO) { DownloadStorage.saveToPublicDownloads(context, file.filename, bytes) }
            } else null
            cache.markFileDownloaded(file.filename)
            when {
                location != null -> {
                    internal.delete()
                    cache.saveFileLocation(file.filename, location)
                    snackbar.showSnackbar("${file.filename} downloaded to your Downloads folder")
                }
                bytes != null && DownloadStorage.needsStoragePermission(context) -> {
                    // Android 9 and older: keep the copy in app storage and ask
                    // for storage access — on grant it's moved to Downloads.
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

    fun markDownloadAdShown(id: Long) {
        cache.markDownloadAdShown(id)
        downloadAdShown = cache.downloadAdShownIds()
    }

    /**
     * Download gate: the picture (interstitial) ad plays a **single time per
     * file** — on the very first download press. If that download fails, a
     * retry goes straight to downloading with no ad; if it succeeds the icon
     * becomes Open anyway. If the ad can't show, the global lock rule kicks
     * in and a rewarded ad must be watched to unlock (also counted once).
     */
    fun downloadFile(file: VpnFile) {
        if (downloadingId != null || gate != null) return
        if (file.id.toString() in downloadAdShown) {
            startFileDownload(file)
            return
        }
        val shown = AdiveryAdsManager.maybeShowInterstitial(
            onFinished = {
                markDownloadAdShown(file.id)
                startFileDownload(file)
            },
        )
        if (!shown) {
            runGate(GatePurpose.DOWNLOAD) {
                markDownloadAdShown(file.id)
                startFileDownload(file)
            }
        }
    }

    /**
     * Open a downloaded file: read it back from wherever it was saved (public
     * Downloads via MediaStore/path, or the legacy app-private copy) and
     * offer Copy.
     */
    fun openDownloadedFile(file: VpnFile) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val location = cache.fileLocation(file.filename)
                when {
                    location != null -> DownloadStorage.readBytesFromLocation(context, location)
                    downloadedFileFor(file).exists() -> downloadedFileFor(file).readBytes()
                    else -> null
                }
            }
            when {
                result == null -> {
                    if (cache.fileLocation(file.filename) == null && !downloadedFileFor(file).exists()) {
                        cache.markFileNotDownloaded(file.filename)
                        downloadedFilenames = cache.downloadedFiles()
                        snackbar.showSnackbar("File was removed — download it again")
                    } else {
                        snackbar.showSnackbar("Couldn't read the file")
                    }
                }
                result.size > MAX_OPEN_FILE_BYTES -> snackbar.showSnackbar("File is too large to open here")
                else -> openFile = file to String(result, StandardCharsets.UTF_8)
            }
        }
    }

    // ── ⠇ menu actions ────────────────────────────────────────────────────

    /** Fastest first; never-pinged and timed-out servers sink to the bottom. */
    fun sortByPing() {
        val sorted = servers.sortedWith(
            compareBy<VpnServer> { if (it.pingMs == null || it.pingMs == -1) 1 else 0 }
                .thenBy { it.pingMs ?: Int.MAX_VALUE },
        )
        servers = sorted
        cache.saveServers(sorted)
        scope.launch { snackbar.showSnackbar("Sorted by ping — fastest first") }
    }

    /**
     * Removes servers whose last ping timed out (-1). The removal is
     * persisted via the hidden list, so a refresh won't bring them back
     * until "Restore hidden servers" is used.
     */
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

    /** Brings every hidden server back and re-fetches fresh from the backend. */
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
                    Text("Servers", style = MaterialTheme.typography.headlineSmall, color = RootNetColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Export a config to your VPN client app",
                        color = RootNetColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            pinging = true
                            // Ping one server at a time, publishing each result as it
                            // arrives so the card updates immediately.
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
                IconButton(
                    onClick = { refreshGate() },
                    enabled = !loading && !pinging && gate == null,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh servers", tint = RootNetColors.AccentNeon)
                }
                // ⠇ overflow menu — sort / clean-up tools.
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = !loading && gate == null) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = RootNetColors.AccentNeon)
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

            // ── Linky / File tab row ──────────────────────────────────────
            ListTabRow(listTab = listTab, onSelect = ::selectTab)
            Spacer(Modifier.height(10.dp))

            // ── Tab content (blurred while a video gate is active) ────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (gate != null) Modifier.blur(10.dp) else Modifier),
                ) {
                    when (listTab) {
                        0 -> LinksTab(
                            servers = servers,
                            loading = loading,
                            exporting = exporting,
                            onCopy = ::copyServer,
                            onExport = ::exportServer,
                        )
                        else -> FilesTab(
                            files = files,
                            loading = filesLoading,
                            downloadingId = downloadingId,
                            progress = downloadProgress,
                            downloaded = downloadedFilenames,
                            onDownload = ::downloadFile,
                            onOpen = ::openDownloadedFile,
                        )
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

        noClientConfig?.let { raw ->
            NoClientDialog(
                onDismiss = { noClientConfig = null },
                onCopy = {
                    noClientConfig = null
                    ConfigActions.copyToClipboard(context, "RootNet config", raw)
                    scope.launch { snackbar.showSnackbar("Config copied — import it into your client") }
                },
            )
        }

        openFile?.let { (file, content) ->
            DownloadedFileDialog(
                filename = file.filename,
                sizeBytes = file.sizeBytes,
                isEncrypted = file.isEncrypted,
                content = content,
                onDismiss = { openFile = null },
                onCopy = {
                    openFile = null
                    ConfigActions.copyToClipboard(context, file.filename, content)
                    scope.launch { snackbar.showSnackbar("Copied — paste it into your VPN client app") }
                },
            )
        }
    }
}

/** The Linky tab — the link/server config list with Copy / Export. */
@Composable
private fun LinksTab(
    servers: List<VpnServer>,
    loading: Boolean,
    exporting: String?,
    onCopy: (VpnServer) -> Unit,
    onExport: (VpnServer) -> Unit,
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
                Text("Tap ↻ to refresh", color = RootNetColors.TextMuted, fontSize = 12.sp)
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Dedupe by config — the backend has no UNIQUE constraint on
                // config, and duplicate LazyColumn keys would crash.
                items(servers.distinctBy { it.rawConfig }, key = { it.rawConfig }) { server ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)),
                    ) {
                        ConfigCard(
                            server = server,
                            exporting = exporting == server.rawConfig,
                            onCopy = { onCopy(server) },
                            onOpen = { onExport(server) },
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            // Persistent picture ad (banner) at the bottom of the list — Adivery only.
            AdiveryAdsManager.BannerAdView(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp))
        }
    }
}

/**
 * The File tab — .npvt / .sip / .npv files with a download manager. Each row
 * shows a static download icon; once the file is saved the icon becomes an
 * **open** button. While a download runs the icon animates with a subtle
 * top→bottom shine and a bar under the row fills with progress.
 */
@Composable
private fun FilesTab(
    files: List<VpnFile>,
    loading: Boolean,
    downloadingId: Long?,
    progress: Map<Long, Float>,
    downloaded: Set<String>,
    onDownload: (VpnFile) -> Unit,
    onOpen: (VpnFile) -> Unit,
) {
    when {
        loading && files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = RootNetColors.AccentNeon)
        }
        files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PulsingOrb(icon = Icons.Filled.Lock, size = 56.dp, iconSize = 26.dp)
                Spacer(Modifier.height(16.dp))
                Text("No files yet", color = RootNetColors.TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("Tap ↻ to refresh", color = RootNetColors.TextMuted, fontSize = 12.sp)
            }
        }
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(files, key = { it.id }) { file ->
                FileCard(
                    file = file,
                    downloading = downloadingId == file.id,
                    progress = progress[file.id] ?: 0f,
                    downloaded = file.filename in downloaded,
                    onDownload = { onDownload(file) },
                    onOpen = { onOpen(file) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** Linky / File segmented tab row above the list. */
@Composable
private fun ListTabRow(listTab: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RootNetColors.BgDarkEmerald.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        ListTabPill("Linky", selected = listTab == 0, modifier = Modifier.weight(1f)) { onSelect(0) }
        ListTabPill("File", selected = listTab == 1, modifier = Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun ListTabPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) RootNetColors.AccentNeon else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) RootNetColors.BgDeepForest else RootNetColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Full-screen lock overlay shown while a video gate is pending. The list
 * behind it is blurred; the lock icon shakes every ~3 seconds while the user
 * must watch the ad. A skipped ad keeps the screen locked until a full watch.
 */
@Composable
private fun AdLockOverlay(
    state: GateState,
    purpose: GatePurpose,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    // One 3 s cycle: the lock shakes in a decaying burst during the first
    // ~22% of each cycle, then rests — "shakes every ~3 seconds".
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
                GatePurpose.REFRESH -> "refresh your list"
                GatePurpose.DOWNLOAD -> "download this file"
                GatePurpose.UNLOCK -> "continue"
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
                // No "continue without ad" — the global lock rule keeps the
                // screen locked until a rewarded ad is watched to the end.
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
private fun ConfigCard(
    server: VpnServer,
    exporting: Boolean,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    GlassCard(
        shape = MaterialTheme.shapes.medium,
        borderColor = RootNetColors.CardBorder.copy(alpha = 0.4f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        server.name,
                        color = RootNetColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                // One compact line: protocol · flag+country · format · 🕗 time.
                // Everything the user asked for on a single row so the card
                // stays short — it truncates instead of wrapping.
                val formatLabel = if (server.configFormat == ConfigFormat.LINK) null
                else server.configFormat.displayName
                val meta = buildList {
                    add("protocol: ${server.type.displayName}")
                    add("${server.flag} ${server.country}".trim())
                    if (formatLabel != null) add(formatLabel)
                    server.createdAt?.let { iso ->
                        TimeFormat.formatScrapedTime(iso)?.let { add("🕗 $it") }
                    }
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
                // Only render when there IS a ping result. -1 (sentinel from
                // pingServer) = failed/timeout → red "Timeout" chip.
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
            // Copy — clipboard; every 3rd Copy/Export tap shows the interstitial.
            ActionButton(
                label = "Copy",
                icon = AppIcons.ContentCopy,
                onClick = onCopy,
            )
            // Export — only for URI configs a client app can open.
            if (ConfigActions.isLinkLike(server.rawConfig)) {
                ActionButton(
                    label = if (exporting) "Opening…" else "Export",
                    icon = AppIcons.OpenInNew,
                    busy = exporting,
                    enabled = !exporting,
                    onClick = onOpen,
                )
            }
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
        borderColor = RootNetColors.CardBorder.copy(alpha = 0.4f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RootNetColors.BgDarkEmerald.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, RootNetColors.GlassBorder),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (file.isEncrypted) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Encrypted file",
                                tint = RootNetColors.Warning,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                file.format.take(4).uppercase(),
                                fontSize = 12.sp,
                                color = RootNetColors.AccentNeon,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.filename,
                        color = RootNetColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    // The file type — NPVT / SIP / NPV / ... as the protocol label.
                    Text(
                        "protocol: ${file.format}",
                        color = RootNetColors.TextMuted,
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
                        color = RootNetColors.TextMuted,
                        fontSize = 10.5.sp,
                    )
                }
                Spacer(Modifier.size(8.dp))
                // Download → Open state machine. Idle rows show a plain static
                // download icon (no always-on animation); while a download runs
                // the icon keeps its shape with a subtle shine sweep and the
                // bar under the row fills with real progress.
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
                                if (downloading) RootNetColors.AccentNeon.copy(alpha = 0.18f)
                                else RootNetColors.AccentNeon.copy(alpha = 0.1f),
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
                                tint = RootNetColors.AccentNeon,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
            // Progress bar — fills as the download gets closer to done.
            if (downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = RootNetColors.AccentNeon,
                    trackColor = RootNetColors.BgDarkEmerald.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** A round icon button used for the File tab's download → open toggle. */
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
            .background(RootNetColors.AccentNeon.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = RootNetColors.AccentNeon, modifier = Modifier.size(24.dp))
    }
}

/**
 * The download icon with a "brighten" sweep — shown ONLY while a download is
 * in progress. A narrow, crisp highlight travels down the glyph and is masked
 * to the icon's own pixels (SrcAtop), so the brightness lives **on the green
 * arrow shape itself** — never in the space around it. It loops gently until
 * the download completes; idle rows render the plain static icon instead.
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
                // Tight band (~28% of the height) so it reads as a single
                // highlight strip sweeping through the arrow, not a wash.
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
        Icon(AppIcons.FileDownload, contentDescription = "Downloading…", tint = RootNetColors.AccentNeon, modifier = Modifier.size(24.dp))
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

/** Shown when a downloaded file is opened — offers Copy (launcher role). */
@Composable
private fun DownloadedFileDialog(
    filename: String,
    sizeBytes: Long,
    isEncrypted: Boolean,
    content: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = RootNetColors.BgCard) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "📁 $filename",
                    color = RootNetColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                val lock = if (isEncrypted) " · 🔒 encrypted" else ""
                Text(
                    "Type: ${filename.substringAfterLast('.', "").uppercase()}$lock · ${formatFileSize(sizeBytes)}",
                    color = RootNetColors.AccentNeon,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "RootNet doesn't decrypt configs — copy the file and import it in your client app (v2rayNG, NekoBox, Hiddify…).",
                    color = RootNetColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(20.dp))
                Surface(
                    onClick = onCopy,
                    shape = MaterialTheme.shapes.medium,
                    color = RootNetColors.AccentNeon,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Copy to clipboard",
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = RootNetColors.BgDeepForest,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = RootNetColors.TextMuted)
                }
            }
        }
    }
}

/** Shown when no installed app can open the config URI. */
@Composable
private fun NoClientDialog(onDismiss: () -> Unit, onCopy: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = RootNetColors.BgCard) {
            Column(Modifier.padding(24.dp)) {
                Text("No app found for this config", color = RootNetColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Install a VLESS client like v2rayNG, NekoBox or Hiddify, or copy the config and import it manually.",
                    color = RootNetColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(20.dp))
                Surface(
                    onClick = onCopy,
                    shape = MaterialTheme.shapes.medium,
                    color = RootNetColors.AccentNeon,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Copy config",
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = RootNetColors.BgDeepForest,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = RootNetColors.TextMuted)
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

// ─── Ping helpers — real TCP handshake, pinged one at a time ───────────────

/**
 * Real TCP connect-time ping to the config's address:port (5s timeout).
 * Returns the latency in ms, or **-1 when the ping failed/timed out** — the
 * card shows a red "Timeout" chip for -1. `null` is reserved for "not yet
 * pinged" (no chip), so a never-pinged server is never confused with a
 * dead one.
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

/** Video-gate overlay states. */
private enum class GateState { FINDING, SKIPPED, UNAVAILABLE }

/** What the active video gate is for — Refresh, file download, or an unlock. */
private enum class GatePurpose { REFRESH, DOWNLOAD, UNLOCK }

/** Safety cap when reading a downloaded file back for Copy (10 MB). */
private const val MAX_OPEN_FILE_BYTES = 10 * 1024 * 1024
