package com.chobgroup.admin_vlesshub.data.model

enum class ConfigFormat {
    LINK, JSON, NPV, CONF, RAW, SIP;

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

data class VpnServer(
    val name: String,
    val flag: String,
    val country: String,
    val rawConfig: String,
    val type: VpnProtocol = VpnProtocol.VLESS,
    val configFormat: ConfigFormat = ConfigFormat.LINK,
    val pingMs: Int? = null,
    val createdAt: String? = null,
    val sourceChannel: String? = null,
    /** Server ID from the backend — used for admin DELETE operations. */
    val id: String? = null,
)
