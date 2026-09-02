package com.chobgroup.rootnet.vpn

import android.content.Context
import android.content.SharedPreferences

/**
 * Ad-funded TIME quota (RootNet v3):
 *  - watching a rewarded video grants **30 minutes** of connection time
 *  - total remaining time is capped at **60 minutes** (2 videos)
 *  - a second ad may only be watched while connected and the timer is
 *    **below 30 min**, so the total never exceeds 60 min
 *    (e.g. 25 min left → allowed, reaches 55; 35 min left → blocked)
 *  - the timer only runs while the engine is CONNECTED; at zero the
 *    engine hard-disconnects
 *
 * Persisted so closing the app doesn't refill the clock.
 */
object TimeQuotaManager {

    private const val PREFS = "rootnet_time_quota"
    private const val KEY_REMAINING = "remaining_seconds"
    private const val KEY_DEVICE_ID = "device_id"

    /** Server ledger — reinstalls can't refill the clock. */
    private const val API_QUOTA = com.chobgroup.rootnet.data.AppConstants.API_URL + "/quota/sync"

    const val GRANT_PER_AD_SECONDS: Long = 30 * 60L        // 30 min
    const val MAX_TOTAL_SECONDS: Long = 60 * 60L           // 60 min

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remainingSeconds(context: Context): Long =
        prefs(context).getLong(KEY_REMAINING, 0L)

    /** Stable anonymous install ID for the server-side quota ledger. */
    fun deviceId(context: Context): String {
        val p = prefs(context)
        return p.getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString().also {
            p.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    /**
     * Sync with the server ledger.
     *  - [watchAd]=true  → server-verified +30 min grant (capped at 60 min);
     *    the returned value is authoritative and stored locally.
     *  - [watchAd]=false → in-session heartbeat; only burns the clock down
     *    on the server, never refills it.
     * Returns the adopted remaining seconds, or null when offline (caller
     * falls back to local-only math).
     */
    suspend fun syncWithServer(context: Context, watchAd: Boolean): Long? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val payload = org.json.JSONObject()
                    .put("deviceId", deviceId(context))
                    .put("watchAd", watchAd)
                if (!watchAd) {
                    payload.put("remainingSeconds", remainingSeconds(context))
                }
                val connection = java.net.URL(API_QUOTA)
                    .openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val response = try {
                    connection.inputStream.bufferedReader().readText()
                } finally {
                    connection.disconnect()
                }
                val json = org.json.JSONObject(response)
                val remaining = json.getLong("remainingSeconds").coerceIn(0L, MAX_TOTAL_SECONDS)
                prefs(context).edit().putLong(KEY_REMAINING, remaining).apply()
                remaining
            }.getOrNull()
        }

    /** True when the engine is allowed to start / keep running. */
    fun hasTime(context: Context): Boolean = remainingSeconds(context) > 0L

    /**
     * Called after a verified rewarded-video watch.
     * Returns the new remaining total (already capped at 60 min).
     */
    fun applyAdGrant(context: Context, connected: Boolean): Long {
        val p = prefs(context)
        val current = p.getLong(KEY_REMAINING, 0L)
        val newTotal = if (connected) {
            // Extra time only when the clock dipped below one grant,
            // keeping the ceiling at 60 min.
            if (current < GRANT_PER_AD_SECONDS) (current + GRANT_PER_AD_SECONDS)
                .coerceAtMost(MAX_TOTAL_SECONDS) else current
        } else {
            // Fresh session from scratch.
            (current + GRANT_PER_AD_SECONDS).coerceAtMost(MAX_TOTAL_SECONDS)
        }
        p.edit().putLong(KEY_REMAINING, newTotal).apply()
        return newTotal
    }

    /**
     * One tick of the metering loop — called every second while connected.
     * Returns false when time is up (caller must disconnect).
     */
    fun tick(context: Context): Boolean {
        val p = prefs(context)
        val remaining = p.getLong(KEY_REMAINING, 0L)
        if (remaining <= 0L) return false
        val next = remaining - 1
        p.edit().putLong(KEY_REMAINING, next).apply()
        return next > 0L
    }

    /** Manual reset (debug/admin). */
    fun resetAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
