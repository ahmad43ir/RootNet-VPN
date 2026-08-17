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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    val onGetProxies: () -> Unit = {
        if (viewModel.hasLoadedOnce) {
            // "Get a new batch" is gated by a rewarded video (Adivery): when an
            // ad is available it must be watched to the end before fetching; if
            // none is available the fetch still proceeds (no lockout).
            scope.launch {
                var rewarded = true
                if (AdiveryAdsManager.isRewardedReady()) {
                    rewarded = runCatching { AdiveryAdsManager.showRewardedAd() }.getOrDefault(false)
                }
                if (rewarded) {
                    viewModel.loadProxies()
                } else {
                    snackbarHostState.showSnackbar("Watch the full video to get a new batch")
                }
            }
        } else {
            // First load happens without an ad.
            viewModel.loadProxies()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ProxyBoxColors.BgDeepForest,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(ProxyBoxColors.BgDarkEmerald, ProxyBoxColors.BgDeepForest),
                    ),
                )
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
                                        scope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("proxy", proxy.link)),
                                            )
                                            snackbarHostState.showSnackbar("tg:// link copied")
                                        }
                                    },
                                    onShare = { shareProxy(context, proxy.link) },
                                    onOpen = { openInTelegram(context, proxy.link) },
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
