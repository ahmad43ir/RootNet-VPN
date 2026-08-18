package com.chobgroup.proxybox.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chobgroup.proxybox.ads.AdiveryAdsManager
import com.chobgroup.proxybox.core.theme.ProxyBoxColors
import com.chobgroup.proxybox.data.ProxyApi
import com.chobgroup.proxybox.data.ProxyItem
import com.chobgroup.proxybox.ui.icons.AppIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── State ────────────────────────────────────────────────────────────────

sealed interface HomeUiState {
    data object Initial : HomeUiState
    data object Loading : HomeUiState
    data class Success(val batch: ProxyApi.ProxyBatch) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** First load happens without an interstitial; refreshes are ad-gated. */
    var hasLoadedOnce by mutableStateOf(false)
        private set

    init {
        loadProxies()
    }

    fun loadProxies() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val batch = ProxyApi.fetchProxies()
                hasLoadedOnce = true
                _uiState.value = HomeUiState.Success(batch)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load proxies")
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Ad gates (mirrors RootNet v2.3) ────────────────────────────────────
    var gate by remember { mutableStateOf<GateState?>(null) }
    var gatePurpose by remember { mutableStateOf(GatePurpose.REFRESH) }
    var pendingGateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Combined Copy/Share/Open counter — every 3rd tap on a DIFFERENT proxy
    // shows the interstitial (re-tapping the same proxy doesn't count).
    var actionCount by remember { mutableIntStateOf(0) }
    var countedProxies by remember { mutableStateOf<Set<String>>(emptySet()) }

    /**
     * Runs the lock gate: blur the list, show "Finding ad…" while Adivery
     * loads, then play the rewarded video. Only a full watch ([onRewarded])
     * unlocks — a skip keeps the screen locked. With placeholder Adivery IDs
     * (not configured) the action proceeds without an ad so the app stays
     * usable until the real IDs are pasted.
     */
    fun runGate(purpose: GatePurpose, onRewarded: () -> Unit) {
        if (!AdiveryAdsManager.isRewardedConfigured()) {
            onRewarded()
            return
        }
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

    fun retryGate() {
        val action = pendingGateAction ?: return
        gate = null
        runGate(gatePurpose, action)
    }

    fun cancelGate() {
        gate = null
        pendingGateAction = null
    }

    /**
     * Every Copy/Share/Open tap on a NEW proxy counts toward a combined
     * counter; every **3rd distinct proxy** shows an Adivery interstitial first
     * — the action then completes when it closes. Re-tapping the same proxy
     * does NOT advance the counter. If the ad can't show, the lock gate runs.
     */
    fun performGatedAction(proxyLink: String, action: () -> Unit) {
        if (proxyLink !in countedProxies) {
            countedProxies = countedProxies + proxyLink
            actionCount++
        }
        if (actionCount >= 3) {
            actionCount = 0
            countedProxies = emptySet()
            val shown = AdiveryAdsManager.maybeShowInterstitial(onFinished = { action() })
            if (!shown) {
                runGate(GatePurpose.UNLOCK, onRewarded = { action() })
            }
        } else {
            action()
        }
    }

    val onGetProxies: () -> Unit = {
        if (viewModel.hasLoadedOnce) {
            // "Get a new batch" is gated by a rewarded video (full watch).
            runGate(GatePurpose.REFRESH) { viewModel.loadProxies() }
        } else {
            // First load happens without an ad.
            viewModel.loadProxies()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ProxyBoxColors.BgDeepForest,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(ProxyBoxColors.BgDarkEmerald, ProxyBoxColors.BgDeepForest),
                    ),
                )
                .then(if (gate != null) Modifier.blur(10.dp) else Modifier)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // Header
            Text(
                "ProxyBox",
                color = ProxyBoxColors.AccentNeon,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Free MTProto proxies for Telegram — 10 random picks per batch",
                color = ProxyBoxColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            // Status
            when (val state = uiState) {
                is HomeUiState.Success -> PoolStatusChip(state.batch.working, state.batch.poolSize)
                is HomeUiState.Error -> ErrorBanner(state.message)
                else -> {}
            }
            Spacer(Modifier.height(12.dp))

            // Get proxies button
            Button(
                onClick = onGetProxies,
                enabled = uiState !is HomeUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProxyBoxColors.AccentNeon,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (uiState is HomeUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (viewModel.hasLoadedOnce) "Get a new batch" else "Get 10 proxies",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Proxy list / states
            when (val state = uiState) {
                is HomeUiState.Success -> {
                    if (state.batch.proxies.isEmpty()) {
                        CenteredHint("No proxies available right now — try again later.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.batch.proxies, key = { it.link }) { proxy ->
                                ProxyCard(
                                    proxy = proxy,
                                    onCopy = {
                                        performGatedAction(proxy.link) {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(ClipData.newPlainText("proxy", proxy.link)),
                                                )
                                                snackbarHostState.showSnackbar("tg:// link copied")
                                            }
                                        }
                                    },
                                    onShare = { performGatedAction(proxy.link) { shareProxy(context, proxy.link) } },
                                    onOpen = { performGatedAction(proxy.link) { openInTelegram(context, proxy.link) } },
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
                is HomeUiState.Error -> CenteredHint("Couldn't load proxies.\n${state.message}")
                is HomeUiState.Initial, is HomeUiState.Loading -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ProxyBoxColors.AccentNeon)
                }
            }

            // Persistent banner ad pinned at the bottom — Adivery only.
            AdiveryAdsManager.BannerAdView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }

        // Lock overlay while a video gate is pending (v2.3 lock rule).
        gate?.let { gateState ->
            AdLockOverlay(
                state = gateState,
                purpose = gatePurpose,
                onRetry = ::retryGate,
                onCancel = ::cancelGate,
            )
        }
        }
    }
}

// ─── Ad gate state (mirrors RootNet v2.3) ───────────────────────────────

enum class GateState { FINDING, SKIPPED, UNAVAILABLE }

enum class GatePurpose { REFRESH, UNLOCK }

/**
 * Full-screen lock overlay shown while a video gate is pending. The list
 * behind it is blurred; a skipped ad keeps the screen locked until a full
 * watch (no "continue without ad" escape).
 */
@Composable
private fun AdLockOverlay(
    state: GateState,
    purpose: GatePurpose,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProxyBoxColors.BgDeepForest.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = ProxyBoxColors.TextMuted)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            val action = when (purpose) {
                GatePurpose.REFRESH -> "get a new batch"
                GatePurpose.UNLOCK -> "continue"
            }
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = ProxyBoxColors.AccentNeon,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                when (state) {
                    GateState.FINDING -> "Finding ad…"
                    GateState.SKIPPED -> "Watch the full ad to $action"
                    GateState.UNAVAILABLE -> "Ad unavailable"
                },
                color = ProxyBoxColors.TextPrimary,
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
                color = ProxyBoxColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            when (state) {
                GateState.FINDING -> CircularProgressIndicator(
                    color = ProxyBoxColors.AccentNeon,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                )
                GateState.SKIPPED -> ActionChip("Watch ad", Icons.Default.Refresh, onClick = onRetry)
                // No "continue without ad" — the screen stays locked.
                GateState.UNAVAILABLE -> ActionChip("Try again", Icons.Default.Refresh, onClick = onRetry)
            }
        }
    }
}

// ─── Pieces ───────────────────────────────────────────────────────────────

@Composable
private fun PoolStatusChip(working: Int, poolSize: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = ProxyBoxColors.CardTranslucent,
        border = BorderStroke(1.dp, ProxyBoxColors.CardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ProxyBoxColors.AccentNeon),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (working > 0) {
                    "$working working of $poolSize in the pool"
                } else {
                    "$poolSize proxies in the pool"
                },
                color = ProxyBoxColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ProxyBoxColors.ErrorRed.copy(alpha = 0.12f),
    ) {
        Text(
            text = "⚠ $message",
            modifier = Modifier.padding(12.dp),
            color = ProxyBoxColors.ErrorRed,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ColumnScope.CenteredHint(text: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ProxyBoxColors.TextMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ProxyCard(
    proxy: ProxyItem,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ProxyBoxColors.CardTranslucent,
        border = BorderStroke(1.dp, ProxyBoxColors.CardBorder),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ProxyBoxColors.AccentLime),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = proxy.host,
                    color = ProxyBoxColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = ":${proxy.port}",
                    color = ProxyBoxColors.TextMuted,
                    fontSize = 14.sp,
                )
            }
            proxy.source?.takeIf { it.isNotBlank() }?.let { source ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "source: $source",
                    color = ProxyBoxColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Copy", AppIcons.ContentCopy, onCopy)
                ActionChip("Share", Icons.Default.Share, onShare)
                ActionChip("Open", Icons.Default.Send, onOpen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = ProxyBoxColors.AccentNeon.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = ProxyBoxColors.AccentNeon)
            Spacer(Modifier.width(6.dp))
            Text(label, color = ProxyBoxColors.AccentNeon, fontSize = 12.sp)
        }
    }
}

// ─── Actions ──────────────────────────────────────────────────────────────

private fun shareProxy(context: Context, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(intent, "Share proxy"))
}

private fun openInTelegram(context: Context, link: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    } catch (_: Exception) {
        // tg:// scheme not handled (Telegram missing) — fall back to the https form.
        val https = link.replaceFirst("tg://proxy?", "https://t.me/proxy?")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(https)))
    }
}
