package com.chobgroup.rootnet.vpn

import com.chobgroup.rootnet.data.model.UnifiedConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a complete Xray-core JSON config for the embedded TUN engine
 * (RootNet v3). Inbound = Xray's native Android TUN (fd passed via env
 * outside this builder); outbound = the selected server; DNS over HTTPS so
 * queries ride the tunnel instead of leaking to the ISP resolver.
 */
object XrayConfigBuilder {

    fun build(config: UnifiedConfig): String {
        val root = JSONObject()
        root.put(
            "log",
            JSONObject().put("loglevel", "warning"),
        )
        root.put(
            "dns",
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put("https://1.1.1.1/dns-query")
                        .put("https://8.8.8.8/dns-query"),
                ),
        )

        // ── TUN inbound (fd injected via xray.tun.fd env before start) ──
        val tunIn = JSONObject()
            .put("protocol", "tun")
            .put("tag", "tun-in")
            .put(
                "settings",
                JSONObject()
                    .put("mtu", 8500)
                    .put("name", "rootnet0"),
            )
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                    .put("routeOnly", false),
            )

        val proxyOut = buildProxyOutbound(config)
        val freedom = JSONObject()
            .put("protocol", "freedom")
            .put("tag", "direct")
        val blackhole = JSONObject()
            .put("protocol", "blackhole")
            .put("tag", "block")

        root.put(
            "inbounds",
            JSONArray().put(tunIn),
        )
        root.put(
            "outbounds",
            JSONArray().put(proxyOut).put(freedom).put(blackhole),
        )
        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "IPIfNonMatch")
                .put(
                    "rules",
                    JSONArray().put(
                        // Block QUIC so YouTube etc. fall back to TCP through the tunnel.
                        JSONObject()
                            .put("network", "udp")
                            .put("port", 443)
                            .put("outboundTag", "block"),
                    ),
                ),
        )
        return root.toString()
    }

    private fun buildProxyOutbound(c: UnifiedConfig): JSONObject {
        val settings = JSONObject()
        when (c.protocol) {
            com.chobgroup.rootnet.data.model.VpnProtocol.VLESS -> {
                val user = JSONObject()
                    .put("id", c.uuid.orEmpty())
                    .put("encryption", c.encryption.ifBlank { "none" })
                (c.extra["flow"] as? String)?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
                settings.put(
                    "vnext",
                    JSONArray().put(serverEntry(c).put("users", JSONArray().put(user))),
                )
                return outbound("vless", c, settings)
            }
            com.chobgroup.rootnet.data.model.VpnProtocol.VMESS -> {
                val user = JSONObject()
                    .put("id", c.uuid.orEmpty())
                    .put("alterId", 0)
                    .put("security", "auto")
                settings.put(
                    "vnext",
                    JSONArray().put(serverEntry(c).put("users", JSONArray().put(user))),
                )
                return outbound("vmess", c, settings)
            }
            com.chobgroup.rootnet.data.model.VpnProtocol.TROJAN -> {
                val server = serverEntry(c).put("password", c.uuid.orEmpty())
                settings.put("servers", JSONArray().put(server))
                return outbound("trojan", c, settings)
            }
            com.chobgroup.rootnet.data.model.VpnProtocol.SHADOWSOCKS -> {
                val server = serverEntry(c)
                    .put("method", c.encryption.ifBlank { "aes-256-gcm" })
                    .put("password", c.uuid.orEmpty())
                settings.put("servers", JSONArray().put(server))
                return outbound("shadowsocks", c, settings)
            }
            com.chobgroup.rootnet.data.model.VpnProtocol.SOCKS -> {
                val server = serverEntry(c)
                val user = c.extra["user"] as? String
                val pass = c.extra["pass"] as? String
                if (!user.isNullOrBlank()) {
                    server.put(
                        "users",
                        JSONArray().put(JSONObject().put("user", user).put("pass", pass ?: "")),
                    )
                }
                settings.put("servers", JSONArray().put(server))
                return outbound("socks", c, settings)
            }
            else -> throw IllegalArgumentException("${c.protocol.displayName} is not supported by the built-in engine yet")
        }
    }

    private fun outbound(protocol: String, c: UnifiedConfig, settings: JSONObject): JSONObject =
        JSONObject()
            .put("protocol", protocol)
            .put("tag", "proxy")
            .put("settings", settings)
            .put("streamSettings", streamSettings(c))
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))

    private fun serverEntry(c: UnifiedConfig): JSONObject =
        JSONObject().put("address", c.address).put("port", c.port)

    private fun streamSettings(c: UnifiedConfig): JSONObject {
        val stream = JSONObject()
            .put("network", c.transport?.ifBlank { "tcp" } ?: "tcp")
            .put("security", c.security.ifBlank { "none" })
        when (c.security.lowercase()) {
            "tls" -> {
                val tls = JSONObject()
                    .put("serverName", c.sni ?: c.address)
                    .put("allowInsecure", c.allowInsecure)
                tls.putOpt("fingerprint", c.fingerprint?.takeIf { it.isNotBlank() })
                tls.putOpt("alpn", c.alpn?.let { JSONArray(it.split(",").map { s -> s.trim() }) })
                stream.put("tlsSettings", tls)
            }
            "reality" -> {
                val reality = JSONObject()
                    .put("serverName", c.sni ?: c.address)
                    .put("fingerprint", c.fingerprint ?: "chrome")
                    .put("publicKey", (c.extra["publicKey"] as? String).orEmpty())
                    .put("shortId", (c.extra["shortId"] as? String).orEmpty())
                    .put("spiderX", "")
                stream.put("realitySettings", reality)
            }
        }
        when (c.transport?.lowercase()) {
            "ws" -> {
                val ws = JSONObject().put("path", c.transportPath ?: "/")
                c.transportHost?.let { host ->
                    ws.put("headers", JSONObject().put("Host", host))
                }
                stream.put("wsSettings", ws)
            }
            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", c.transportPath.orEmpty()),
            )
            "http", "h2" -> {
                val h2 = JSONObject().put("path", c.transportPath ?: "/")
                c.transportHost?.let { h2.put("host", JSONArray().put(it)) }
                stream.put("httpSettings", h2)
            }
        }
        return stream
    }
}
