package com.chobgroup.vlesshub.data.remote

import com.chobgroup.vlesshub.data.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetAddress
import java.net.URL

/** A GeoIP result for a server host (or this device). */
data class GeoInfo(val ip: String, val country: String, val countryCode: String)

/**
 * GeoIP lookups against the `geo-api` Edge Function (rate-limited 60/min/IP,
 * results normalized to `{ip, country, countryCode}`). Per-host results are
 * cached in memory so re-renders / refreshes don't burn the quota.
 */
object GeoIpResolver {

    private const val ENDPOINT = "${AppConstants.SUPABASE_URL}/functions/v1/geo-api"

    /** host/ip → resolved info, process-lifetime cache. */
    private val cache = LinkedHashMap<String, GeoInfo>(64)

    /** Resolves a config host (domain or literal IP) to its country. */
    suspend fun lookupHost(host: String): GeoInfo? = withContext(Dispatchers.IO) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return@withContext null
        synchronized(cache) { cache[trimmed] }?.let { return@withContext it }

        val ip = if (isLiteralIp(trimmed)) trimmed else resolveHost(trimmed) ?: return@withContext null
        val info = lookup(ip) ?: return@withContext null
        synchronized(cache) { cache[trimmed] = info }
        info
    }

    /** Looks up THIS device's public IP country via geo-api self-lookup. */
    suspend fun lookupSelf(): GeoInfo? = withContext(Dispatchers.IO) {
        // geo-api returns the caller's country when no `ip` param is sent.
        fetchGeo(ENDPOINT)
    }

    /** True when this device's public IP is in Iran. */
    suspend fun isDeviceInIran(): Boolean =
        lookupSelf()?.countryCode?.equals("IR", ignoreCase = true) == true

    // ── internals ────────────────────────────────────────────────────────

    private fun lookup(ip: String): GeoInfo? = fetchGeo("$ENDPOINT?ip=$ip")

    private fun fetchGeo(url: String): GeoInfo? = runCatching {
        val connection = URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("apikey", AppConstants.SUPABASE_ANON_KEY)
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val country = json.optString("country", "").ifBlank { return@runCatching null }
            GeoInfo(
                ip = json.optString("ip", ""),
                country = country,
                countryCode = json.optString("countryCode", "XX"),
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun resolveHost(host: String): String? =
        runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()

    private fun isLiteralIp(host: String): Boolean =
        host.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}")) || host.contains(":")

    /** Two-letter ISO code → regional-indicator flag emoji ("DE" → 🇩🇪). */
    fun flagEmoji(countryCode: String): String {
        val code = countryCode.trim().uppercase()
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return ""
        return code.map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("")
    }
}
