package com.chobgroup.vlesshub.data.model

/**
 * A VPN config file shown in the app's **File** tab â€” pulled from the
 * `vpn_files` Supabase table (files the admin/bot uploaded: .npvt, .sip,
 * .npv, ...). Unlike link servers these aren't URIs a client app can open,
 * so the row offers **Copy** (raw content to the clipboard).
 *
 * `format` is derived from the filename extension and shown as the
 * "protocol:" label (e.g. `NPVT`, `SIP`).
 */
data class VpnFile(
    val id: Long,
    val filename: String,
    val sizeBytes: Long,
    /** ISO-8601 UTC upload time (shown in device-local time like servers). */
    val uploadedAt: String? = null,
    val isEncrypted: Boolean = false,
    val configCount: Int = 0,
    /** Telegram channel this file was scraped from (e.g. "@broz_time"). */
    val sourceChannel: String? = null,
) {
    /** Human-readable file/protocol label derived from the extension. */
    val format: String
        get() {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "npvt" -> "NPVT"
                "npv" -> "NPV"
                "npt" -> "NPT"
                "sip" -> "SIP"
                "conf" -> "WireGuard Conf"
                "json" -> "JSON"
                "ovpn" -> "OpenVPN"
                "txt" -> "Text"
                else -> ext.ifBlank { "FILE" }.uppercase()
            }
        }
}
