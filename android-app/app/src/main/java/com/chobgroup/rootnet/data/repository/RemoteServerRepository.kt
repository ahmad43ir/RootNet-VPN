package com.chobgroup.rootnet.data.repository

import com.chobgroup.rootnet.data.AppConstants
import com.chobgroup.rootnet.data.model.ConfigFormat
import com.chobgroup.rootnet.data.model.ProtocolType
import com.chobgroup.rootnet.data.model.VpnServer
import com.chobgroup.rootnet.data.remote.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Server repository — v2.0 config-launcher model.
 *
 * The app has no accounts anymore, so the server list comes from the **public
 * Supabase REST** read of `servers` (RLS grants anon SELECT of active rows).
 * Every refresh fetches fresh servers from the backend —
 * configs ALWAYS come from the DB, never hardcoded.
 *
 * Any failure returns an empty list so the UI shows its empty state instead of
 * crashing (graceful degradation).
 */
class RemoteServerRepository : ServerRepository {

    override suspend fun fetchServers(): List<VpnServer> = withContext(Dispatchers.IO) {
        try {
            val client = PinnedHttpClient.newClient()
            val url = AppConstants.SUPABASE_URL + "/rest/v1/servers" +
                "?select=name,flag,country,config,type,config_format,created_at&is_active=eq.true"
            val request = Request.Builder()
                .url(url)
                .header("apikey", AppConstants.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AppConstants.SUPABASE_ANON_KEY}")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                mapServers(JSONArray(body))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapServers(array: JSONArray): List<VpnServer> {
        val result = mutableListOf<VpnServer>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val config = item.optString("config")
            if (config.isBlank()) continue
            result += VpnServer(
                name = item.optString("name", "Server"),
                flag = item.optString("flag", "\uD83C\uDF10"),
                country = item.optString("country", "Cloud"),
                rawConfig = config,
                type = ProtocolType.fromString(item.optString("type")),
                configFormat = ConfigFormat.fromString(item.optString("config_format")),
                createdAt = item.optString("created_at", "").takeIf { it.isNotBlank() },
            )
        }
        return result
    }
}
