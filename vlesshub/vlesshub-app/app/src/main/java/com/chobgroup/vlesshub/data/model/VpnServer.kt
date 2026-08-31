package com.chobgroup.vlesshub.data.model

/** Raw config input format — spec §6.1. */
enum class ConfigFormat {
    LINK,
    JSON,
    NPV,
    CONF,
    RAW,
    SIP;

    companion object {
        fun fromString(value: String?): ConfigFormat = when (value?.lowercase()) {
            "json" -> JSON
            "npv" -> NPV
            "conf" -> CONF
            "raw" -> RAW
            "sip" -> SIP
            else -> LINK
        }
    }

    /** Human-readable description for UI display. */
    val displayName: String
        get() = when (this) {
            LINK -> "Link"
            JSON -> "JSON"
            NPV -> "NPV"
            CONF -> "WireGuard Conf"
            RAW -> "Raw"
            SIP -> "SIP (SOCKS only)"
        }
}

/**
 * A VPN server as shown in the server list — spec §6.1.
 * Configs ALWAYS come from the backend (spec rule 5) — never hardcoded.
 */
data class VpnServer(
    val name: String,
    val flag: String,
    val country: String,
    val rawConfig: String,
    val type: VpnProtocol = VpnProtocol.VLESS,
    val configFormat: ConfigFormat = ConfigFormat.LINK,
    val pingMs: Int? = null,
    /** When this config was scraped (ISO-8601 UTC from `servers.created_at`). */
    val createdAt: String? = null,
    /** Telegram channel this config was scraped from (e.g. "@channelname"). */
    val sourceChannel: String? = null,
)
