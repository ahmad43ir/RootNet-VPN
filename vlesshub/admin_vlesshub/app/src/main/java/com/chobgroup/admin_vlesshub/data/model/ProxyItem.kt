package com.chobgroup.admin_vlesshub.data.model

data class ProxyItem(
    val host: String,
    val port: Int,
    val secret: String?,
    val source: String?,
    val link: String,
    /** Proxy ID from the backend — used for admin DELETE operations. */
    val id: String? = null,
)
