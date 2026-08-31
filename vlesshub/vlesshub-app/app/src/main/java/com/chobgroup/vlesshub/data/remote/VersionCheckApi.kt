package com.chobgroup.vlesshub.data.remote

import com.chobgroup.vlesshub.data.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Checks the app version against the server's app_config.
 *
 * Returns a [VersionStatus] that the UI layer uses to decide whether to
 * show the normal shell or a full-screen force-update blocker.
 */
data class VersionConfig(
    val latestVersion: String = "",
    val latestBuild: Int = 0,
    val minimumVersion: String = "",
    val updateUrl: String = "",
    val releaseNotes: String = "",
    val forceUpdate: Boolean = false,
)

sealed interface VersionStatus {
    /** App is up-to-date — show normal UI. */
    data object UpToDate : VersionStatus

    /** App is below the minimum — show full-screen lock. */
    data class ForceUpdate(val config: VersionConfig) : VersionStatus
}

object VersionCheckApi {

    /**
     * Fetch the version config from Supabase's `app_config` table.
     * Returns null on any network/parse error so the app can proceed normally.
     */
    suspend fun fetchVersionConfig(): VersionConfig? = withContext(Dispatchers.IO) {
        try {
            val url = "${AppConstants.SUPABASE_URL}/rest/v1/app_config?id=eq.1&select=*"
            val request = Request.Builder()
                .url(url)
                .header("apikey", AppConstants.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AppConstants.SUPABASE_ANON_KEY}")
                .get()
                .build()

            val client = PinnedHttpClient.newClient()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank() || body == "[]") return@withContext null

                val arr = org.json.JSONArray(body)
                if (arr.length() == 0) return@withContext null
                val json = arr.getJSONObject(0)

                VersionConfig(
                    latestVersion = json.optString("latest_version", ""),
                    latestBuild = json.optInt("latest_build", 0),
                    minimumVersion = json.optString("minimum_version", ""),
                    updateUrl = json.optString("update_url", ""),
                    releaseNotes = json.optString("release_notes", ""),
                    forceUpdate = json.optBoolean("force_update", false),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compare two semver strings (X.Y.Z). Returns >0 if a>b, <0 if a<b, 0 if equal.
     */
    fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /**
     * Determine whether the app needs a forced update.
     *
     * The app is blocked when:
     *  - Its version is older than `minimumVersion`, OR
     *  - `forceUpdate` is true AND the app version is older than `latestVersion`.
     */
    fun checkStatus(
        appVersionName: String,
        config: VersionConfig,
    ): VersionStatus {
        // Below minimum → always block
        if (config.minimumVersion.isNotBlank() && compareVersion(appVersionName, config.minimumVersion) < 0) {
            return VersionStatus.ForceUpdate(config)
        }
        // Force update ON and app older than latest → block
        if (config.forceUpdate && config.latestVersion.isNotBlank()
            && compareVersion(appVersionName, config.latestVersion) < 0
        ) {
            return VersionStatus.ForceUpdate(config)
        }
        return VersionStatus.UpToDate
    }
}
