package com.chobgroup.proxybox.data

/**
 * A single MTProto proxy from the shared Supabase pool
 * (`scraper_proxies`), formatted as a Telegram `tg://proxy` link.
 */
data class ProxyItem(
    val host: String,
    val port: Int,
    val secret: String?,
    val source: String?,
    val link: String,
)
