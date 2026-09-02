package com.chobgroup.rootnet.data.repository

import com.chobgroup.rootnet.data.AppConstants
import com.chobgroup.rootnet.data.model.ConfigFormat
import com.chobgroup.rootnet.data.model.ProtocolType
import com.chobgroup.rootnet.data.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64

/**
 * Server sources for RootNet v3:
 *  - **10 BPB worker subscriptions** â€” refresh picks ONE at random and
 *    parses the VLESS links out of its body (base64 or plain-text sub).
 *  - Fallback: the public Supabase `servers` table (scraped links).
 *
 * The subscription URLs live in [AppConstants.SUBSCRIPTION_URLS]; slots are
 * filled as the BPB workers get deployed.
 */
object BpbSubRepository {

    /** Regex over the decoded sub body — vless:// AND trojan:// links. */
    private val CONFIG_RE = Regex("(?:vless|trojan)://[^\\s\"'<>]+")

    /** Protocol from the link scheme (BPB subs mix VLESS and Trojan). */
    private fun protocolOf(link: String): ProtocolType =
        if (link.startsWith("trojan://", ignoreCase = true)) ProtocolType.TROJAN else ProtocolType.VLESS


    /** VIP server - always included in the list. */
    private val VIP_SERVER = VpnServer(
        name = "VIP Server",
        flag = "⭐",
        country = "VIP",
        rawConfig = "vless://9779a56b-e8bf-4fa6-866c-0f56787363b4@web-production-f0ca8.up.railway.app:443?encryption=none&security=tls&sni=web-production-f0ca8.up.railway.app&fp=chrome&alpn=http%2F1.1&insecure=0&allowInsecure=0&type=ws&host=web-production-f0ca8.up.railway.app&path=%2Fws%2F9779a56b-e8bf-4fa6-866c-0f56787363b4#This%20Service%20is%20Free",
        type = ProtocolType.VLESS,
        configFormat = ConfigFormat.LINK,
    )

    fun subscriptionUrls(): List<String> =
        AppConstants.SUBSCRIPTION_URLS.filter { it.isNotBlank() }

    /**
     * Pick one random subscription, fetch + parse it.
     * Returns null when there is no configured sub or the fetch fails â€”
     * caller then falls back to the Supabase server list.
     */
    suspend fun fetchRandomSub(): List<VpnServer>? = withContext(Dispatchers.IO) {
        // 1. Preferred: backend proxy â€” sub URLs never leave the server.
        val viaApi = runCatching { fetchViaApi() }.getOrNull()
        if (viaApi != null) return@withContext viaApi

        // 2. Fallback: local sub URL slots (offline/dev).
        val urls = subscriptionUrls()
        if (urls.isEmpty()) return@withContext null
        val url = urls.random()
        runCatching { parseSubscription(url) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /** GET rootnet-api/bpb-sub - backend picks + parses a random sub for us. */
    private fun fetchViaApi(): List<VpnServer>? {
        val connection = java.net.URL(AppConstants.API_URL + "/bpb-sub")
            .openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().readText()
            val array = org.json.JSONObject(body).optJSONArray("servers") ?: return null
            val result = mutableListOf<VpnServer>()
            for (i in 0 until array.length()) {
                val s = array.optJSONObject(i) ?: continue
                val rawConfig = s.optString("rawConfig")
                if (rawConfig.isBlank()) continue
                result += VpnServer(
                    name = "Server ${result.size + 1}",
                    flag = s.optString("flag", "\uD83D\uDEF0"),
                    country = s.optString("country", "BPB service"),
                    rawConfig = rawConfig,
                    type = protocolOf(rawConfig),
                    configFormat = ConfigFormat.LINK,
                )
            }
            return result.takeIf { it.isNotEmpty() }
        } finally {
            connection.disconnect()
        }
    }

    /** Fetch a single subscription and turn its links into servers. */
    private fun parseSubscription(url: String): List<VpnServer> {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode !in 200..299) return emptyList()
        val raw = connection.inputStream.bufferedReader().readText()
        val body = decodeBody(raw)
        return CONFIG_RE.findAll(body)
            .map { it.value.trimEnd('.') }
            .distinct()
                .mapIndexed { index, link -> toServer(link, url, index) }
                .toList()
        } finally {
            connection.disconnect()
        }
    }

    /** Sub bodies may be plain text or base64 (standard V2Ray subs). */
    private fun decodeBody(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.contains("vless://") || trimmed.contains("trojan://")) return trimmed
        return runCatching {
            String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse { trimmed }
    }

    private fun toServer(link: String, subUrl: String, index: Int): VpnServer {
        return VpnServer(
            name = "Server ${index + 1}",
            flag = "\uD83D\uDEF0",
            country = "BPB service",
            rawConfig = link,
            type = protocolOf(link),
            configFormat = ConfigFormat.LINK,
        )
    }

}
