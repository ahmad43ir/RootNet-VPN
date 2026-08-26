package com.chobgroup.vlesshub.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.chobgroup.vlesshub.data.model.ConfigFormat
import com.chobgroup.vlesshub.data.model.ProtocolType
import com.chobgroup.vlesshub.data.model.VpnFile
import com.chobgroup.vlesshub.data.model.VpnServer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local server cache + hidden-server list â€” SharedPreferences (not SecurePrefs,
 * no secrets here). Cache-first: the server list screen shows the cached list
 * immediately and only hits Supabase when there's no cache (first run) or when
 * the user taps refresh. Hidden servers are persisted so deletions survive
 * app restarts; refresh restores them.
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

    // â”€â”€ File tab (vpn_files) cache + unlock flag â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun cachedFiles(): List<VpnFile> {
        val raw = prefs.getString(KEY_FILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<VpnFile>()
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result += it.toVpnFile() }
            }
            result
        }.getOrDefault(emptyList())
    }

    fun saveFiles(files: List<VpnFile>) {
        val array = JSONArray()
        files.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FILES, array.toString()).apply()
    }

    /**
     * Filenames the user already downloaded to the File tab. Persisted so a
     * downloaded file shows the **open** icon instead of the download button
     * across app restarts. The file itself lives in `filesDir/downloads/`.
     */
    fun downloadedFiles(): Set<String> =
        prefs.getStringSet(KEY_DOWNLOADED_FILES, emptySet()) ?: emptySet()

    fun markFileDownloaded(filename: String) {
        val updated = (prefs.getStringSet(KEY_DOWNLOADED_FILES, emptySet()) ?: emptySet()) + filename
        prefs.edit().putStringSet(KEY_DOWNLOADED_FILES, updated).apply()
    }

    fun markFileNotDownloaded(filename: String) {
        val updated = (prefs.getStringSet(KEY_DOWNLOADED_FILES, emptySet()) ?: emptySet()) - filename
        prefs.edit().putStringSet(KEY_DOWNLOADED_FILES, updated).apply()
        val locations = prefs.getStringSet(KEY_FILE_LOCATIONS, emptySet()) ?: emptySet()
        val remaining = locations.filterNot { it.substringBefore('\u0000') == filename }.toSet()
        if (remaining != locations) prefs.edit().putStringSet(KEY_FILE_LOCATIONS, remaining).apply()
    }

    /**
     * Where a downloaded file was saved â€” a `content://` URI (API 29+
     * MediaStore) or an absolute path. Used by the File tab's open dialog so
     * it reads from the same place the user can find the file.
     */
    fun saveFileLocation(filename: String, location: String) {
        val updated = (prefs.getStringSet(KEY_FILE_LOCATIONS, emptySet()) ?: emptySet()) +
            "$filename\u0000$location"
        prefs.edit().putStringSet(KEY_FILE_LOCATIONS, updated).apply()
    }

    fun fileLocation(filename: String): String? =
        (prefs.getStringSet(KEY_FILE_LOCATIONS, emptySet()) ?: emptySet())
            .firstOrNull { it.substringBefore('\u0000') == filename }
            ?.substringAfter('\u0000')

    /**
     * File ids whose download **ad** was already shown once. The download ad
     * plays a single time per file â€” retries after a failed download (and any
     * later re-download) skip straight to downloading with no ad.
     */
    fun downloadAdShownIds(): Set<String> =
        prefs.getStringSet(KEY_DOWNLOAD_AD_SHOWN, emptySet()) ?: emptySet()

    fun markDownloadAdShown(id: Long) {
        val updated = (prefs.getStringSet(KEY_DOWNLOAD_AD_SHOWN, emptySet()) ?: emptySet()) + id.toString()
        prefs.edit().putStringSet(KEY_DOWNLOAD_AD_SHOWN, updated).apply()
    }

    fun hiddenConfigs(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()

    fun hideConfig(config: String) {
        val updated = (prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()) + config
        prefs.edit().putStringSet(KEY_HIDDEN, updated).apply()
    }

    fun restoreAllHidden() {
        prefs.edit().remove(KEY_HIDDEN).apply()
    }

    // ── Ad-gate click tracking (persisted — survives app restarts so the
    //    counter can't be reset by closing/reopening the app) ─────────────

    /** Copy/Export (links) or Copy/Share/Open (proxies) taps in this cycle. */
    fun actionCount(): Int = prefs.getInt(KEY_ACTION_COUNT, 0)

    fun setActionCount(value: Int) {
        prefs.edit().putInt(KEY_ACTION_COUNT, value).apply()
    }

    /** Distinct configs already counted in this cycle. */
    fun countedConfigs(): Set<String> =
        prefs.getStringSet(KEY_COUNTED_CONFIGS, emptySet()) ?: emptySet()

    fun setCountedConfigs(values: Set<String>) {
        prefs.edit().putStringSet(KEY_COUNTED_CONFIGS, values).apply()
    }

    /** Refresh resets the cycle — the user explicitly reloads everything. */
    fun resetActionTracking() {
        prefs.edit().remove(KEY_ACTION_COUNT).remove(KEY_COUNTED_CONFIGS).apply()
    }

    /** Successfully completed file downloads in this cycle (failed/partial
     *  downloads never increment it). */
    fun fileDownloadsDone(): Int = prefs.getInt(KEY_FILE_DL_COUNT, 0)

    fun setFileDownloadsDone(value: Int) {
        prefs.edit().putInt(KEY_FILE_DL_COUNT, value).apply()
    }

    companion object {
        private const val KEY_SERVERS = "cached_servers"
        private const val KEY_HIDDEN = "hidden_servers"
        private const val KEY_FILES = "cached_files"
        private const val KEY_DOWNLOADED_FILES = "downloaded_files"
        private const val KEY_DOWNLOAD_AD_SHOWN = "download_ad_shown_ids"
        private const val KEY_FILE_LOCATIONS = "file_locations"
        private const val KEY_ACTION_COUNT = "ad_gate_action_count"
        private const val KEY_COUNTED_CONFIGS = "ad_gate_counted_configs"
        private const val KEY_FILE_DL_COUNT = "file_download_success_count"
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

private fun VpnFile.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("filename", filename)
    put("sizeBytes", sizeBytes)
    if (uploadedAt != null) put("uploadedAt", uploadedAt)
    put("isEncrypted", isEncrypted)
    put("configCount", configCount)
}

private fun JSONObject.toVpnFile(): VpnFile = VpnFile(
    id = optLong("id"),
    filename = optString("filename", "file"),
    sizeBytes = optLong("sizeBytes", 0),
    uploadedAt = if (has("uploadedAt")) optString("uploadedAt").takeIf { it.isNotBlank() } else null,
    isEncrypted = optBoolean("isEncrypted", false),
    configCount = optInt("configCount", 0),
)

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
