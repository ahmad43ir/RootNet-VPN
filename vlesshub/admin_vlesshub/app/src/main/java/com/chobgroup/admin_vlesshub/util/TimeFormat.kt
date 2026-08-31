package com.chobgroup.admin_vlesshub.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormat {

    private fun parser(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun formatter(): SimpleDateFormat = SimpleDateFormat("h:mm a", Locale.US)

    fun formatScrapedTime(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            val trimmed = iso.take(19)
            val date: Date = parser().parse(trimmed) ?: return null
            formatter().format(date)
        } catch (_: Exception) {
            null
        }
    }
}
