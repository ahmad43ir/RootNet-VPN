package com.chobgroup.admin_vlesshub.data.repository

import com.chobgroup.admin_vlesshub.data.AppConstants
import com.chobgroup.admin_vlesshub.data.model.VpnFile
import com.chobgroup.admin_vlesshub.data.remote.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

class RemoteVpnFileRepository {

    suspend fun fetchFiles(limit: Int = 50): List<VpnFile> = withContext(Dispatchers.IO) {
        try {
            val client = PinnedHttpClient.newClient()
            val url = AppConstants.VLESSHUB_API_URL + "/files?limit=$limit"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                val array = JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        add(
                            VpnFile(
                                id = item.optLong("id"),
                                filename = item.optString("filename", "file"),
                                sizeBytes = item.optLong("size_bytes", 0),
                                uploadedAt = item.optString("uploaded_at", "").takeIf { it.isNotBlank() },
                                isEncrypted = item.optBoolean("is_encrypted", false),
                                configCount = item.optInt("config_count", 0),
                                sourceChannel = item.optString("source_channel", "").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
