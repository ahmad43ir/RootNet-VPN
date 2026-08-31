package com.chobgroup.admin_vlesshub.config

import com.chobgroup.admin_vlesshub.data.model.ConfigFormat
import com.chobgroup.admin_vlesshub.data.model.UnifiedConfig
import com.chobgroup.admin_vlesshub.data.model.VpnProtocol
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.util.Base64

/**
 * VPN config parsing layer — extracts address/port for TCP ping.
 * Same parsers as VlessHub.
 */
object ConfigNormalizer {

    private val NESTED_PROTOCOL_KEYS = setOf(
        "vless", "vmess", "trojan", "ss", "shadowsocks", "socks", "socks4", "socks5", "socks5h", "wireguard",
    )

    fun normalize(raw: String, configFormat: String? = null, protocol: String? = null): UnifiedConfig {
        val format = configFormat?.lowercase() ?: detectFormat(raw)
        return when (format) {
            "link" -> fromLink(raw, protocol)
            "json" -> fromJson(raw, protocol)
            "npv" -> fromNpv(raw)
            "conf" -> fromWgConf(raw, protocol)
            "raw" -> fromRaw(raw, protocol)
            "sip" -> fromSip(raw, protocol)
            else -> throw IllegalArgumentException("Unknown config format: $format")
        }
    }

    fun detectFormat(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            if (trimmed.contains("\"npv\"") || trimmed.contains("\"npvt\"") || trimmed.contains("\"npt\"")) return "npv"
            val hasSipFields = trimmed.contains("\"protocol\"") &&
                    (trimmed.contains("\"host\"") || trimmed.contains("\"address\"") || trimmed.contains("\"server\"")) &&
                    !trimmed.contains("\"config\"")
            if (hasSipFields || trimmed.contains("\"sip\"")) return "sip"
            return "json"
        }
        if (trimmed.startsWith("[Interface]") || trimmed.startsWith("[Peer]")) return "conf"
        try {
            val decoded = String(Base64.decode(trimmed, Base64.DEFAULT), StandardCharsets.UTF_8)
            if (decoded.trimStart().startsWith("{")) return "json"
        } catch (_: Exception) { }
        if (trimmed.contains("://")) return "link"
        throw IllegalArgumentException("Unable to detect config format")
    }

    fun fromLink(raw: String, protocol: String? = null): UnifiedConfig {
        val uri = URI(raw)
        val scheme = uri.scheme?.lowercase() ?: "vless"
        val inferred = protocol?.lowercase() ?: when (scheme) {
            "vmess" -> "vmess"
            "trojan" -> "trojan"
            "ss", "shadowsocks" -> "shadowsocks"
            "socks", "socks4", "socks5", "socks5h" -> "socks"
            "wireguard" -> "wireguard"
            else -> "vless"
        }
        return when (inferred) {
            "vmess" -> fromVmessLink(raw)
            "trojan" -> fromTrojanLink(raw, uri)
            "shadowsocks" -> fromShadowsocksLink(raw, uri)
            "socks" -> fromSocksLink(raw, uri)
            else -> fromVlessUri(raw)
        }
    }

    fun fromVlessUri(raw: String): UnifiedConfig {
        val uri = URI(raw)
        val query = parseQuery(uri.rawQuery)
        return UnifiedConfig(
            protocol = VpnProtocol.VLESS,
            uuid = uri.rawUserInfo?.takeIf { it.isNotEmpty() },
            address = uri.host,
            port = if (uri.port > 0) uri.port else 443,
            rawConfig = raw,
        )
    }

    fun fromVmessLink(raw: String): UnifiedConfig {
        var encoded = raw.trim()
        val schemeIdx = encoded.indexOf("://")
        if (schemeIdx >= 0) encoded = encoded.substring(schemeIdx + 3)
        if (encoded.startsWith('/')) encoded = encoded.substring(1)
        val cut = encoded.indexOfFirst { it == '?' || it == '#' }
        if (cut >= 0) encoded = encoded.substring(0, cut)
        encoded = encoded.replace('-', '+').replace('_', '/')
        when (encoded.length % 4) {
            2 -> encoded += "=="
            3 -> encoded += "="
        }
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
        val json = JSONObject(decoded)
        return UnifiedConfig(
            protocol = VpnProtocol.VMESS,
            uuid = json.optString("id").takeIf { it.isNotEmpty() },
            address = json.optString("add").ifEmpty { json.optString("address") },
            port = json.optInt("port", 443).takeIf { it > 0 } ?: 443,
            rawConfig = raw,
        )
    }

    fun fromTrojanLink(raw: String, uri: URI): UnifiedConfig {
        val query = parseQuery(uri.rawQuery)
        return UnifiedConfig(
            protocol = VpnProtocol.TROJAN,
            uuid = uri.rawUserInfo,
            address = uri.host,
            port = if (uri.port > 0) uri.port else 443,
            rawConfig = raw,
        )
    }

    fun fromShadowsocksLink(raw: String, uri: URI): UnifiedConfig {
        return UnifiedConfig(
            protocol = VpnProtocol.SHADOWSOCKS,
            address = uri.host,
            port = if (uri.port > 0) uri.port else 443,
            rawConfig = raw,
        )
    }

    fun fromSocksLink(raw: String, uri: URI): UnifiedConfig {
        return UnifiedConfig(
            protocol = VpnProtocol.SOCKS,
            address = uri.host,
            port = if (uri.port > 0) uri.port else 1080,
            rawConfig = raw,
        )
    }

    fun fromJson(raw: String, protocol: String? = null): UnifiedConfig {
        val json = JSONObject(raw)
        val inferred = protocol?.lowercase()
            ?: json.optString("type", "").ifEmpty { json.optString("protocol", "vless") }
        return UnifiedConfig(
            protocol = VpnProtocol.fromString(inferred),
            uuid = json.optString("id").ifEmpty { json.optString("uuid") }.ifEmpty { null },
            address = json.optString("add").ifEmpty { json.optString("address") },
            port = json.optInt("port", 443).takeIf { it > 0 } ?: 443,
            rawConfig = raw,
        )
    }

    fun fromNpv(raw: String): UnifiedConfig {
        val json = JSONObject(raw)
        val npv = json.optJSONObject("npv")
            ?: json.optJSONObject("npvt")
            ?: json.optJSONObject("npt")
            ?: throw IllegalArgumentException("Invalid NPV format")
        val profiles = npv.optJSONArray("profiles")
        if (profiles != null && profiles.length() > 0) {
            for (i in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(i) ?: continue
                val type = profile.optString("type").ifEmpty { profile.optString("protocol") }.takeIf { it.isNotBlank() }
                val resolved = resolveNpvNode(profile.opt("config"), type) ?: continue
                return resolved
            }
        }
        val innerProtocol = npv.optString("protocol").ifEmpty { npv.optString("type") }.takeIf { it.isNotBlank() }
        val config = npv.opt("config") ?: throw IllegalArgumentException("Invalid NPV format: missing config")
        return resolveNpvNode(config, innerProtocol) ?: throw IllegalArgumentException("Invalid NPV format")
    }

    private fun resolveNpvNode(value: Any?, protocolHint: String?, depth: Int = 0): UnifiedConfig? {
        if (depth > 4 || value == null || value == JSONObject.NULL) return null
        return when (value) {
            is org.json.JSONArray -> {
                for (i in 0 until value.length()) {
                    resolveNpvNode(value.opt(i), protocolHint, depth + 1)?.let { return it }
                }
                null
            }
            is JSONObject -> {
                if (value.length() == 1) {
                    val key = value.keys().next()
                    val inner = value.opt(key)
                    if (key.lowercase() in NESTED_PROTOCOL_KEYS && inner is String) {
                        return resolveNpvNode(inner, key.lowercase(), depth + 1)
                    }
                }
                runCatching { fromJson(value.toString(), protocolHint) }.getOrNull()
            }
            is String -> {
                val s = value.trim()
                if (s.isEmpty()) return null
                if (s.startsWith("{")) return runCatching { fromJson(s, protocolHint) }.getOrNull()
                if (!s.contains("://")) {
                    try {
                        val decoded = String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8).trimStart()
                        if (decoded.startsWith("{")) return resolveNpvNode(JSONObject(decoded), protocolHint, depth + 1)
                    } catch (_: Exception) { }
                }
                runCatching { fromLink(s, protocolHint) }.getOrNull()
            }
            else -> null
        }
    }

    fun fromWgConf(raw: String, protocol: String? = null): UnifiedConfig {
        var endpoint: String? = null
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Endpoint = ")) endpoint = trimmed.removePrefix("Endpoint = ").trim()
        }
        if (endpoint == null) throw IllegalArgumentException("WireGuard config must contain an Endpoint")
        val endpointUri = URI(if (endpoint.contains("://")) endpoint else "wg://$endpoint")
        return UnifiedConfig(
            protocol = VpnProtocol.WIREGUARD,
            address = endpointUri.host,
            port = if (endpointUri.port > 0) endpointUri.port else 51820,
            rawConfig = raw,
        )
    }

    fun fromRaw(raw: String, protocol: String? = null): UnifiedConfig =
        if (raw.trim().startsWith("{")) fromJson(raw, protocol) else fromLink(raw, protocol)

    fun fromSip(raw: String, protocol: String? = null): UnifiedConfig {
        val json = JSONObject(raw)
        val proto = (protocol?.lowercase() ?: json.optString("protocol", "").ifEmpty { json.optString("type", "socks") })
        val host = json.optString("host").ifEmpty { json.optString("address") }.ifEmpty { json.optString("server") }
        val port = json.optInt("port", 1080).takeIf { it > 0 } ?: 1080
        return UnifiedConfig(
            protocol = VpnProtocol.SOCKS,
            address = host,
            port = port,
            rawConfig = raw,
        )
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (rawQuery.isNullOrEmpty()) return map
        rawQuery.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                map[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }
        }
        return map
    }
}
