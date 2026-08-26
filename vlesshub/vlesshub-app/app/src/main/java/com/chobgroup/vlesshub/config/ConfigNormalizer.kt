package com.chobgroup.vlesshub.config

import com.chobgroup.vlesshub.data.model.ConfigFormat
import com.chobgroup.vlesshub.data.model.UnifiedConfig
import com.chobgroup.vlesshub.data.model.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.util.Base64

/**
 * VPN config parsing layer â€” v2.0.
 *
 * Converts raw configs (VLESS/VMess/Trojan/SS URIs, VMess JSON, NPV files,
 * WireGuard .conf, SIP) into [UnifiedConfig]. The v2.0 app no longer builds
 * Xray engine JSON (the engine is gone) â€” the parsers are kept for live TCP
 * ping (address/port extraction) and future features.
 */
object ConfigNormalizer {

    private val NESTED_PROTOCOL_KEYS = setOf(
        "vless", "vmess", "trojan", "ss", "shadowsocks", "socks", "socks4", "socks5", "socks5h", "wireguard",
    )

    // â”€â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            // NPV-family exports: {"npv": ...}, {"npvt": ...} (template), {"npt": ...}
            if (trimmed.contains("\"npv\"") || trimmed.contains("\"npvt\"") || trimmed.contains("\"npt\"")) {
                return "npv"
            }
            // SIP format: has protocol + host/address/server, but NO config field with a URI
            // This avoids misdetecting generic JSON like {"protocol":"vless", "config":"vless://..."}
            val hasSipFields = trimmed.contains("\"protocol\"") &&
                    (trimmed.contains("\"host\"") || trimmed.contains("\"address\"") || trimmed.contains("\"server\"")) &&
                    !trimmed.contains("\"config\"")
            if (hasSipFields || trimmed.contains("\"sip\"")) {
                return "sip"
            }
            return "json"
        }
        if (trimmed.startsWith("[Interface]") || trimmed.startsWith("[Peer]")) return "conf"
        // Base64-encoded JSON (VMess) â€” decoded payload starts with '{'
        try {
            val decoded = String(Base64.decode(trimmed, Base64.DEFAULT), StandardCharsets.UTF_8)
            if (decoded.trimStart().startsWith("{")) return "json"
        } catch (_: Exception) {
            // not base64 â€” continue
        }
        if (trimmed.contains("://")) return "link"
        throw IllegalArgumentException("Unable to detect config format for: ${trimmed.take(80)}")
    }

    // â”€â”€â”€ Link format â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    /** vless://uuid@host:port?encryption=none&security=tls&sni=...&type=ws&path=... */
    fun fromVlessUri(raw: String): UnifiedConfig {
        val uri = URI(raw)
        val query = parseQuery(uri.rawQuery)
        val extra = mutableMapOf<String, Any>()
        query["flow"]?.takeIf { it.isNotEmpty() }?.let { extra["flow"] = it }
        query["pbk"]?.takeIf { it.isNotEmpty() }?.let { extra["publicKey"] = it }
        query["sid"]?.takeIf { it.isNotEmpty() }?.let { extra["shortId"] = it }
        query["mode"]?.takeIf { it.isNotEmpty() }?.let { extra["mode"] = it }

        return UnifiedConfig(
            protocol = VpnProtocol.VLESS,
            uuid = uri.rawUserInfo?.takeIf { it.isNotEmpty() },
            address = uri.host,
            port = if (uri.port > 0) uri.port else 443,
            encryption = query["encryption"] ?: "none",
            security = query["security"] ?: "none",
            sni = query["sni"],
            fingerprint = query["fp"],
            allowInsecure = query["allowInsecure"] == "1" || query["insecure"] == "1",
            transport = query["type"],
            transportHost = query["host"],
            transportPath = query["path"],
            alpn = query["alpn"],
            rawConfig = raw,
            originalFormat = ConfigFormat.LINK,
            extra = extra,
        )
    }

    /**
     * vmess://base64(JSON) â€” URL-safe base64, optional padding.
     *
     * Parsed manually (NOT via java.net.URI): Java's URI class treats
     * `vmess://<b64>` as host=base64 / path="", losing the payload.
     * Standard base64 may contain '/' and '=' â€” keep them; only cut at
     * '?' or '#' suffixes (neither is part of the base64 alphabet).
     */
    fun fromVmessLink(raw: String): UnifiedConfig {
        var encoded = raw.trim()
        val schemeIdx = encoded.indexOf("://")
        if (schemeIdx >= 0) encoded = encoded.substring(schemeIdx + 3)
        // Some generators emit vmess:///base64 (extra slash) â€” strip exactly one.
        if (encoded.startsWith('/')) encoded = encoded.substring(1)
        val cut = encoded.indexOfFirst { it == '?' || it == '#' }
        if (cut >= 0) encoded = encoded.substring(0, cut)
        if (encoded.isEmpty()) throw IllegalArgumentException("VMess config has empty base64 payload")
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
            encryption = json.optString("security", "auto"),
            security = if (json.optString("tls") == "tls") "tls" else "none",
            sni = json.optString("sni").ifEmpty { null },
            transport = json.optString("net").ifEmpty { null },
            transportHost = json.optString("host").ifEmpty { null },
            transportPath = json.optString("path").ifEmpty { null },
            alpn = json.optString("alpn").ifEmpty { null },
            rawConfig = raw,
            originalFormat = ConfigFormat.LINK,
        )
    }

    /** trojan://password@host:port?security=tls&sni=... */
    fun fromTrojanLink(raw: String, uri: URI): UnifiedConfig {
        val query = parseQuery(uri.rawQuery)
        return UnifiedConfig(
            protocol = VpnProtocol.TROJAN,
            uuid = uri.rawUserInfo,
            address = uri.host,
            port = if (uri.port > 0) uri.port else 443,
            encryption = "none",
            security = query["security"] ?: "tls",
            sni = query["sni"] ?: uri.host,
            transport = "tcp",
            rawConfig = raw,
            originalFormat = ConfigFormat.LINK,
        )
    }

    /** ss://method:password@host:port */
    fun fromShadowsocksLink(raw: String, uri: URI): UnifiedConfig {
        val parts = (uri.rawUserInfo ?: "").split(':')
        val method = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "aes-256-gcm"
        val password = parts.drop(1).joinToString(":")
        return UnifiedConfig(
            protocol = VpnProtocol.SHADOWSOCKS,
            address = uri.host,
            port = if (uri.port > 0) uri.port else 443,
            uuid = password,
            encryption = method,
            rawConfig = raw,
            originalFormat = ConfigFormat.LINK,
        )
    }

    /** socks://user:pass@host:port (also socks4/socks5/socks5h) */
    fun fromSocksLink(raw: String, uri: URI): UnifiedConfig {
        val userInfo = uri.rawUserInfo ?: ""
        val idx = userInfo.indexOf(':')
        val user = if (idx >= 0) userInfo.substring(0, idx) else userInfo
        val pass = if (idx >= 0) userInfo.substring(idx + 1) else ""
        fun decode(s: String): String =
            if (s.isEmpty()) s else runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
        val extra = mutableMapOf<String, Any>()
        decode(user).takeIf { it.isNotEmpty() }?.let { extra["user"] = it }
        decode(pass).takeIf { it.isNotEmpty() }?.let { extra["pass"] = it }
        return UnifiedConfig(
            protocol = VpnProtocol.SOCKS,
            address = uri.host,
            port = if (uri.port > 0) uri.port else 1080,
            encryption = "none",
            security = "none",
            transport = "tcp",
            rawConfig = raw,
            originalFormat = ConfigFormat.LINK,
            extra = extra,
        )
    }

    // â”€â”€â”€ JSON format â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun fromJson(raw: String, protocol: String? = null): UnifiedConfig {
        val json = JSONObject(raw)
        val inferred = protocol?.lowercase()
            ?: json.optString("type", "").ifEmpty { json.optString("protocol", "vless") }
        return UnifiedConfig(
            protocol = VpnProtocol.fromString(inferred),
            uuid = json.optString("id").ifEmpty { json.optString("uuid") }.ifEmpty { null },
            address = json.optString("add").ifEmpty { json.optString("address") },
            port = json.optInt("port", 443).takeIf { it > 0 } ?: 443,
            encryption = json.optString("encryption", "none"),
            security = json.optString("security", "none"),
            sni = json.optString("sni").ifEmpty { null },
            fingerprint = json.optString("fp").ifEmpty { null },
            allowInsecure = json.optBoolean("allowInsecure", false) || json.optBoolean("insecure", false),
            transport = json.optString("net").ifEmpty { json.optString("transport").ifEmpty { json.optString("type").ifEmpty { null } } },
            transportHost = json.optString("host").ifEmpty { null },
            transportPath = json.optString("path").ifEmpty { null },
            alpn = json.optString("alpn").ifEmpty { null },
            rawConfig = raw,
            originalFormat = ConfigFormat.JSON,
        )
    }

    /**
     * NPV: `{"npv": {"protocol": "vless", "config": "vless://..."}}` or the full
     * NekoBox export `{"npv": {"profiles": [{"type":..., "config": ...}]}}` where
     * `config` may itself be a base64 VMess payload or nested JSON. For
     * multi-profile exports the first usable profile wins (each profile becomes
     * its own server row on the backend). Also accepts the template variants
     * `.npvt` / `{"npvt": ...}` / `{"npt": ...}`.
     */
    fun fromNpv(raw: String): UnifiedConfig {
        val json = JSONObject(raw)
        val npv = json.optJSONObject("npv")
            ?: json.optJSONObject("npvt")
            ?: json.optJSONObject("npt")
            ?: throw IllegalArgumentException("Invalid NPV format: missing \"npv\" key")

        val profiles = npv.optJSONArray("profiles")
        if (profiles != null && profiles.length() > 0) {
            for (i in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(i) ?: continue
                val type = profile.optString("type").ifEmpty { profile.optString("protocol") }
                    .takeIf { it.isNotBlank() }
                val resolved = resolveNpvNode(profile.opt("config"), type) ?: continue
                return resolved
            }
            throw IllegalArgumentException("Invalid NPV format: no usable profiles")
        }

        val innerProtocol = npv.optString("protocol").ifEmpty { npv.optString("type") }
            .takeIf { it.isNotBlank() }
        val config = npv.opt("config")
        if (config == null || config == JSONObject.NULL) {
            throw IllegalArgumentException("Invalid NPV format: missing config")
        }
        return resolveNpvNode(config, innerProtocol)
            ?: throw IllegalArgumentException("Invalid NPV format: unusable config")
    }

    /**
     * Resolve an NPV inner config value (URI, base64 VMess payload, JSON object,
     * array, or nested protocol-keyed object like `{"vmess": "<base64>"}`).
     * Never re-enters [fromNpv] â€” recursion is bounded by a depth guard so a
     * malformed file can't cause a stack overflow.
     */
    private fun resolveNpvNode(value: Any?, protocolHint: String?, depth: Int = 0): UnifiedConfig? {
        if (depth > 4) return null
        if (value == null || value === JSONObject.NULL) return null
        return when (value) {
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    resolveNpvNode(value.opt(i), protocolHint, depth + 1)?.let { return it }
                }
                null
            }
            is JSONObject -> {
                if (value.length() == 1) {
                    val key = value.keys().next()
                    val inner = value.opt(key)
                    val lowerKey = key.lowercase()
                    if (lowerKey in NESTED_PROTOCOL_KEYS && inner is String) {
                        return resolveNpvNode(inner, lowerKey, depth + 1)
                    }
                }
                runCatching { fromJson(value.toString(), protocolHint) }.getOrNull()
            }
            is String -> {
                val s = value.trim()
                if (s.isEmpty()) return null
                if (s.startsWith("{")) {
                    return runCatching { fromJson(s, protocolHint) }.getOrNull()
                }
                if (!s.contains("://")) {
                    // Likely a base64 VMess payload â€” decode then re-resolve.
                    try {
                        val decoded = String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8)
                            .trimStart()
                        if (decoded.startsWith("{")) {
                            return resolveNpvNode(JSONObject(decoded), protocolHint, depth + 1)
                        }
                    } catch (_: Exception) {
                        // not base64 â€” fall through to link parsing
                    }
                }
                runCatching { fromLink(s, protocolHint) }.getOrNull()
            }
            else -> null
        }
    }

    // â”€â”€â”€ WireGuard .conf â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun fromWgConf(raw: String, protocol: String? = null): UnifiedConfig {
        var endpoint: String? = null
        var listenPort: Int? = null
        var privateKey: String? = null
        var localAddress: String? = null
        var publicKey: String? = null
        var allowedIPs: String? = null
        var dns: String? = null
        var mtu: Int? = null
        var inInterface = false
        var inPeer = false

        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed == "[Interface]" -> { inInterface = true; inPeer = false }
                trimmed == "[Peer]" -> { inPeer = true; inInterface = false }
                trimmed.startsWith("PrivateKey = ") && inInterface -> privateKey = trimmed.removePrefix("PrivateKey = ").trim()
                trimmed.startsWith("Address = ") && inInterface -> localAddress = trimmed.removePrefix("Address = ").trim()
                trimmed.startsWith("DNS = ") && inInterface -> dns = trimmed.removePrefix("DNS = ").trim()
                trimmed.startsWith("MTU = ") && inInterface -> mtu = trimmed.removePrefix("MTU = ").trim().toIntOrNull()
                trimmed.startsWith("PublicKey = ") && inPeer -> publicKey = trimmed.removePrefix("PublicKey = ").trim()
                trimmed.startsWith("Endpoint = ") && inPeer -> endpoint = trimmed.removePrefix("Endpoint = ").trim()
                trimmed.startsWith("AllowedIPs = ") && inPeer -> allowedIPs = trimmed.removePrefix("AllowedIPs = ").trim()
            }
        }

        if (endpoint == null) throw IllegalArgumentException("WireGuard config must contain an Endpoint")
        val endpointUri = URI(if (endpoint.contains("://")) endpoint else "wg://$endpoint")

        val extra = mutableMapOf<String, Any>()
        privateKey?.takeIf { it.isNotEmpty() }?.let { extra["private_key"] = it }
        localAddress?.takeIf { it.isNotEmpty() }?.let { extra["local_address"] = it }
        publicKey?.takeIf { it.isNotEmpty() }?.let { extra["public_key"] = it }
        allowedIPs?.takeIf { it.isNotEmpty() }?.let { extra["allowed_ips"] = it }
        dns?.takeIf { it.isNotEmpty() }?.let { extra["dns"] = it }
        mtu?.let { extra["mtu"] = it }

        return UnifiedConfig(
            protocol = VpnProtocol.WIREGUARD,
            address = endpointUri.host,
            port = if (endpointUri.port > 0) endpointUri.port else (listenPort ?: 51820),
            encryption = "chacha20-poly1305",
            security = "none",
            rawConfig = raw,
            originalFormat = ConfigFormat.CONF,
            extra = extra,
        )
    }

    // â”€â”€â”€ Raw JSON map â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun fromRaw(raw: String, protocol: String? = null): UnifiedConfig =
        if (raw.trim().startsWith("{")) fromJson(raw, protocol) else fromLink(raw, protocol)

    // â”€â”€â”€ SIP format (SocksIP / SSH / SOCKS5 / HTTP proxy configs) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Expected JSON: {"protocol": "ssh|socks|http", "host": "...", "port": 22, "username": "...", "password": "...", "key": "..."}

    fun fromSip(raw: String, protocol: String? = null): UnifiedConfig {
        val json = JSONObject(raw)
        val proto = (protocol?.lowercase() ?: json.optString("protocol", "").ifEmpty { json.optString("type", "socks") })
        val host = json.optString("host").ifEmpty { json.optString("address") }.ifEmpty { json.optString("server") }
        val defaultPort = when (proto) {
            "ssh" -> 22
            "socks", "socks5", "socks4" -> 1080
            else -> 8080
        }
        val port = json.optInt("port", defaultPort).takeIf { it > 0 } ?: defaultPort
        val username = json.optString("username").ifEmpty { json.optString("user") }.takeIf { it.isNotEmpty() }
        val password = json.optString("password").ifEmpty { json.optString("pass") }.takeIf { it.isNotEmpty() }
        val privateKey = json.optString("key").ifEmpty { json.optString("private_key") }.takeIf { it.isNotEmpty() }

        return when (proto) {
            "socks", "socks5", "socks4" -> UnifiedConfig(
                protocol = VpnProtocol.SOCKS,
                address = host,
                port = port,
                uuid = password,
                encryption = "none",
                security = "none",
                transport = "tcp",
                rawConfig = raw,
                originalFormat = ConfigFormat.SIP,
                extra = buildMap {
                    username?.let { put("user", it) }
                    password?.let { put("pass", it) }
                },
            )
            else -> throw IllegalArgumentException("SIP config format only supports socks/socks4/socks5 protocols. '$proto' is not yet implemented (SSH/HTTP require dedicated Xray outbounds).")
        }
    }

    private fun buildMap(block: MutableMap<String, Any>.() -> Unit): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map.block()
        return map
    }

    // â”€â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (rawQuery.isNullOrEmpty()) return map
        rawQuery.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

}
