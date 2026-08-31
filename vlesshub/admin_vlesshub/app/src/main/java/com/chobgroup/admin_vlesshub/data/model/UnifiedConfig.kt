package com.chobgroup.admin_vlesshub.data.model

data class UnifiedConfig(
    val protocol: VpnProtocol,
    val uuid: String? = null,
    val address: String,
    val port: Int,
    val encryption: String = "none",
    val security: String = "none",
    val sni: String? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false,
    val transport: String? = null,
    val transportHost: String? = null,
    val transportPath: String? = null,
    val alpn: String? = null,
    val rawConfig: String? = null,
    val originalFormat: ConfigFormat = ConfigFormat.LINK,
    val extra: Map<String, Any> = emptyMap(),
)

enum class VpnProtocol(val wireName: String) {
    VLESS("vless"),
    VMESS("vmess"),
    TROJAN("trojan"),
    WIREGUARD("wireguard"),
    SHADOWSOCKS("shadowsocks"),
    SOCKS("socks");

    val displayName: String
        get() = when (this) {
            VLESS -> "VLESS"
            VMESS -> "VMess"
            TROJAN -> "Trojan"
            WIREGUARD -> "WireGuard"
            SHADOWSOCKS -> "Shadowsocks"
            SOCKS -> "SOCKS"
        }

    companion object {
        fun fromString(value: String?): VpnProtocol = when (value?.lowercase()) {
            "vmess" -> VMESS
            "trojan" -> TROJAN
            "wireguard" -> WIREGUARD
            "shadowsocks", "ss" -> SHADOWSOCKS
            "socks", "socks4", "socks5", "socks5h" -> SOCKS
            else -> VLESS
        }
    }
}
