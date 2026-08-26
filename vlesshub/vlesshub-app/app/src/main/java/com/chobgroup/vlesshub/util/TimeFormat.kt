package com.chobgroup.vlesshub.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats the "scraped at" timestamp shown next to each config in the server
 * list. The pipeline stores `servers.created_at` as ISO-8601 UTC; we parse it
 * in UTC and display it in the device's local time (e.g. "8:32 PM").
 *
 * Plain [SimpleDateFormat] instead of `java.time` on purpose â€” minSdk 23 has
 * no java.time without core-library desugaring, which the project doesn't use.
 */
object TimeFormat {

    private val PARSE = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val DISPLAY = SimpleDateFormat("h:mm a", Locale.US)

    /** Returns a local "h:mm a" string, or null when the input isn't parseable. */
    fun formatScrapedTime(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            // Everything the pipeline writes is UTC; take up to the seconds
            // field so offsets / fractional seconds can't break the parse.
            val trimmed = iso.take(19)
            val date: Date = PARSE.parse(trimmed) ?: return null
            DISPLAY.format(date)
        } catch (_: Exception) {
            null
        }
    }
}
