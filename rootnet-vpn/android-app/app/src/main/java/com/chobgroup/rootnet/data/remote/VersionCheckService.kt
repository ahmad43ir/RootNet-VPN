package com.chobgroup.rootnet.data.remote

import android.content.Context
import com.chobgroup.rootnet.data.AppConstants
import com.chobgroup.rootnet.data.model.VersionInfo
import com.chobgroup.rootnet.data.model.buildVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

/**
 * Version gate — spec §6.4 / §13.
 *
 * Reads `app_config` from the public Supabase REST endpoint (RLS allows public
 * reads — spec §8.2). Any failure → "no update" so the app never locks users
 * out due to a network hiccup (same graceful behavior as the original).
 */
object VersionCheckService {

    fun noUpdate(): VersionInfo = VersionInfo(
        hasUpdate = false,
        forceUpdate = false,
        isBelowMinimum = false,
        latestVersion = "",
        latestBuild = 0,
        minimumVersion = "",
        updateUrl = AppConstants.UPDATE_URL,
        releaseNotes = "",
    )

    suspend fun check(context: Context): VersionInfo = withContext(Dispatchers.IO) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersion = packageInfo.versionName ?: "0.0.0"
            val currentBuild = if (android.os.Build.VERSION.SDK_INT >= 28) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode
            }

            val client = PinnedHttpClient.newClient(callTimeoutMillis = 8_000)
            val url = AppConstants.SUPABASE_URL + "/rest/v1/app_config" +
                "?select=latest_version,latest_build,minimum_version,update_url,release_notes,force_update&id=eq.1"
            val request = Request.Builder()
                .url(url)
                .header("apikey", AppConstants.SUPABASE_ANON_KEY)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext noUpdate()
                val body = response.body?.string() ?: return@withContext noUpdate()
                val array = JSONArray(body)
                if (array.length() == 0) return@withContext noUpdate()
                val row = array.getJSONObject(0)

                return@withContext buildVersionInfo(
                    currentVersion = currentVersion,
                    currentBuild = currentBuild,
                    latestVersion = row.optString("latest_version", ""),
                    latestBuild = row.optInt("latest_build", 0),
                    minimumVersion = row.optString("minimum_version", ""),
                    forceUpdate = row.optBoolean("force_update", false),
                    updateUrl = row.optString("update_url", AppConstants.UPDATE_URL),
                    releaseNotes = row.optString("release_notes", ""),
                )
            }
        } catch (_: Exception) {
            noUpdate()
        }
    }
}
