package com.chobgroup.admin_vlesshub.data.model

data class VpnFile(
    val id: Long,
    val filename: String,
    val sizeBytes: Long,
    val uploadedAt: String? = null,
    val isEncrypted: Boolean = false,
    val configCount: Int = 0,
    val sourceChannel: String? = null,
) {
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
