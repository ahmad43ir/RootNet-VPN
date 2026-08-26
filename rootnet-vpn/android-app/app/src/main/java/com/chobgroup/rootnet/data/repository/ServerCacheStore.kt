package com.chobgroup.rootnet.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.chobgroup.rootnet.data.model.ConfigFormat
import com.chobgroup.rootnet.data.model.ProtocolType
import com.chobgroup.rootnet.data.model.VpnServer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local server cache + hidden-server list — SharedPreferences (not SecurePrefs,
 * no secrets here). Cache-first: the server list screen shows the cached list
 * immediately and only hits the backend when there's no cache (first run) or
 * when the user taps refresh. Hidden servers are persisted so deletions
 * survive app restarts.
 */
class ServerCacheStore private constructor() {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("rootnet_server_cache", Context.MODE_PRIVATE)
    }

    fun cachedServers(): List<VpnServer> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<VpnServer>()
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result += it.toVpnServer() }
            }
            result
        }.getOrDefault(emptyList())
    }

    fun saveServers(servers: List<VpnServer>) {
        val array = JSONArray()
        servers.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    // ── Ad-gate click tracking (persisted — survives app restarts) ──────

    fun actionCount(): Int = prefs.getInt(KEY_ACTION_COUNT, 0)

    fun setActionCount(value: Int) {
        prefs.edit().putInt(KEY_ACTION_COUNT, value).apply()
    }

    fun countedConfigs(): Set<String> =
        prefs.getStringSet(KEY_COUNTED_CONFIGS, emptySet()) ?: emptySet()

    fun setCountedConfigs(values: Set<String>) {
        prefs.edit().putStringSet(KEY_COUNTED_CONFIGS, values).apply()
    }

    /** Refresh resets the cycle — the user explicitly reloads everything. */
    fun resetActionTracking() {
        prefs.edit().remove(KEY_ACTION_COUNT).remove(KEY_COUNTED_CONFIGS).apply()
    }

    // ── Hidden servers ───────────────────────────────────────────────────

    fun hiddenConfigs(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()

    fun hideConfig(config: String) {
        val updated = (prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()) + config
        prefs.edit().putStringSet(KEY_HIDDEN, updated).apply()
    }

    fun restoreAllHidden() {
        prefs.edit().remove(KEY_HIDDEN).apply()
    }

    companion object {
        private const val KEY_SERVERS = "cached_servers"
        private const val KEY_HIDDEN = "hidden_servers"
        private const val KEY_ACTION_COUNT = "ad_gate_action_count"
        private const val KEY_COUNTED_CONFIGS = "ad_gate_counted_configs"
        val instance: ServerCacheStore by lazy { ServerCacheStore() }
    }
}

private fun VpnServer.toJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("flag", flag)
    put("country", country)
    put("rawConfig", rawConfig)
    put("type", type.wireName)
    put("configFormat", configFormat.name.lowercase())
    if (pingMs != null) put("pingMs", pingMs)
    if (createdAt != null) put("createdAt", createdAt)
}

private fun JSONObject.toVpnServer(): VpnServer = VpnServer(
    name = optString("name", "Server"),
    flag = optString("flag", "\uD83C\uDF10"),
    country = optString("country", "Cloud"),
    rawConfig = optString("rawConfig", ""),
    type = ProtocolType.fromString(optString("type")),
    configFormat = ConfigFormat.fromString(optString("configFormat")),
    pingMs = if (has("pingMs")) optInt("pingMs") else null,
    createdAt = if (has("createdAt")) optString("createdAt").takeIf { it.isNotBlank() } else null,
)
