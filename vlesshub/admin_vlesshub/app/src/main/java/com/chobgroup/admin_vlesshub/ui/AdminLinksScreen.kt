package com.chobgroup.admin_vlesshub.ui

import android.content.ClipData
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chobgroup.admin_vlesshub.config.ConfigNormalizer
import com.chobgroup.admin_vlesshub.core.theme.AdminBackgroundGradient
import com.chobgroup.admin_vlesshub.core.theme.AdminColors
import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.model.ConfigFormat
import com.chobgroup.admin_vlesshub.data.model.VpnServer
import com.chobgroup.admin_vlesshub.data.remote.AdminApi
import com.chobgroup.admin_vlesshub.data.remote.GeoIpResolver
import com.chobgroup.admin_vlesshub.data.repository.RemoteServerRepository
import com.chobgroup.admin_vlesshub.ui.components.AdminGlassCard
import com.chobgroup.admin_vlesshub.ui.components.AdminStatusChip
import com.chobgroup.admin_vlesshub.ui.icons.AdminIcons
import com.chobgroup.admin_vlesshub.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Links tab — VLESS/V2Ray configs with Copy, TCP Ping, Refresh, and admin Remove.
 * Simple UI: no ads, no animations, no blur gates.
 */
@Composable
fun AdminLinksScreen() {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pinging by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val fetched = RemoteServerRepository().fetchServers()
        if (fetched.isNotEmpty()) servers = fetched
        loading = false
        enrichGeoFlags(servers) { servers = it }
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            loading = true
            val fetched = RemoteServerRepository().fetchServers()
            if (fetched.isNotEmpty()) servers = fetched
            loading = false
            enrichGeoFlags(servers) { servers = it }
        }
    }

    fun pingAll() {
        scope.launch {
            pinging = true
            val updated = servers.toMutableList()
            for (i in updated.indices) {
                val ms = pingServer(updated[i])
                updated[i] = updated[i].copy(pingMs = ms)
                servers = updated.toList()
            }
            pinging = false
        }
    }

    fun removeServer(server: VpnServer) {
        if (!AdminKeyStore.instance.hasKey()) {
            scope.launch { snackbar.showSnackbar("Set admin key in Settings first") }
            return
        }
        scope.launch {
            val id = server.id
            if (id == null) {
                servers = servers.filterNot { it.rawConfig == server.rawConfig }
                snackbar.showSnackbar("Removed locally (no server ID)")
                return@launch
            }
            val ok = AdminApi.deleteServer(id)
            if (ok) {
                servers = servers.filterNot { it.rawConfig == server.rawConfig }
                snackbar.showSnackbar("Server removed")
            } else {
                snackbar.showSnackbar("Failed — check admin key")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(AdminBackgroundGradient).padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Links", style = MaterialTheme.typography.headlineSmall, color = AdminColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Admin — copy, ping, refresh, or remove configs", color = AdminColors.TextSecondary, fontSize = 12.sp)
                }
                IconButton(
                    onClick = { if (!pinging) pingAll() },
                    enabled = !loading && !pinging && servers.isNotEmpty(),
                ) {
                    if (pinging) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AdminColors.AccentRed, strokeWidth = 2.dp)
                    } else {
                        Icon(AdminIcons.Speed, contentDescription = "Ping servers", tint = AdminColors.AccentRed)
                    }
                }
                Surface(
                    onClick = { refreshKey++ },
                    enabled = !loading && !pinging,
                    shape = RoundedCornerShape(10.dp),
                    color = AdminColors.AccentRed.copy(alpha = 0.1f),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = AdminColors.AccentRed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh", color = AdminColors.AccentRed, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdminColors.AccentRed)
                }
                servers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No servers — tap Refresh", color = AdminColors.TextMuted, fontSize = 14.sp)
                }
                else -> {
                    val distinct = servers.distinctBy { it.rawConfig }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(distinct, key = { it.rawConfig }) { server ->
                            AdminConfigCard(
                                server = server,
                                onCopy = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("config", server.rawConfig)),
                                        )
                                        snackbar.showSnackbar("Config copied")
                                    }
                                },
                                onPing = {
                                    scope.launch {
                                        val ms = pingServer(server)
                                        val idx = servers.indexOfFirst { it.rawConfig == server.rawConfig }
                                        if (idx >= 0) {
                                            servers = servers.toMutableList().also { it[idx] = it[idx].copy(pingMs = ms) }
                                        }
                                    }
                                },
                                onRemove = { removeServer(server) },
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }
}

@Composable
private fun AdminConfigCard(
    server: VpnServer,
    onCopy: () -> Unit,
    onPing: () -> Unit,
    onRemove: () -> Unit,
) {
    AdminGlassCard(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.name, color = AdminColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                val formatLabel = if (server.configFormat == ConfigFormat.LINK) null else server.configFormat.displayName
                val meta = buildList {
                    server.sourceChannel?.let { add("source: $it") }
                    add("protocol: ${server.type.displayName}")
                    add("${server.flag} ${server.country}".trim())
                    if (formatLabel != null) add(formatLabel)
                    server.createdAt?.let { iso -> TimeFormat.formatScrapedTime(iso)?.let { add("🕐 $it") } }
                }
                Text(meta.joinToString(" · "), color = AdminColors.TextMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            server.pingMs?.let { ms ->
                Spacer(Modifier.size(8.dp))
                val timedOut = ms < 0
                val pingColor = when {
                    timedOut -> AdminColors.ErrorRed
                    ms < 150 -> AdminColors.SuccessGreen
                    ms < 400 -> AdminColors.AccentOrange
                    else -> AdminColors.ErrorRed
                }
                AdminStatusChip(text = if (timedOut) "Timeout" else "${ms}ms", color = pingColor)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminActionButton(label = "Copy", icon = AdminIcons.ContentCopy, onClick = onCopy)
            AdminActionButton(label = "Ping", icon = AdminIcons.Speed, onClick = onPing)
            if (AdminKeyStore.instance.hasKey()) {
                AdminActionButton(label = "Remove", icon = AdminIcons.Delete, tint = AdminColors.ErrorRed, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun AdminActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = AdminColors.AccentRed,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
            Text(label, color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private suspend fun pingServer(server: VpnServer): Int = withContext(Dispatchers.IO) {
    runCatching {
        val config = ConfigNormalizer.normalize(
            raw = server.rawConfig,
            configFormat = server.configFormat.name.lowercase(),
            protocol = server.type.wireName,
        )
        val start = System.nanoTime()
        val socket = Socket()
        try { socket.connect(InetSocketAddress(config.address, config.port), 5000) }
        finally { socket.close() }
        ((System.nanoTime() - start) / 1_000_000).toInt()
    }.getOrDefault(-1)
}

private suspend fun enrichGeoFlags(servers: List<VpnServer>, onUpdate: (List<VpnServer>) -> Unit) {
    if (servers.isEmpty()) return
    val updated = servers.toMutableList()
    for (i in updated.indices) {
        val address = runCatching {
            ConfigNormalizer.normalize(
                raw = updated[i].rawConfig,
                configFormat = updated[i].configFormat.name.lowercase(),
                protocol = updated[i].type.wireName,
            ).address
        }.getOrNull() ?: continue
        val info = GeoIpResolver.lookupHost(address) ?: continue
        updated[i] = updated[i].copy(flag = GeoIpResolver.flagEmoji(info.countryCode), country = info.country)
        onUpdate(updated.toList())
    }
}
