package com.chobgroup.admin_vlesshub.data.remote

import com.chobgroup.admin_vlesshub.data.AdminKeyStore
import com.chobgroup.admin_vlesshub.data.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Admin DELETE operations — remove servers, files, and proxies from the backend.
 * Each call sends the admin key via `X-Admin-Key` header.
 *
 * Backend endpoints (Cloudflare Worker → Supabase REST):
 *   DELETE /servers/{id}   → { ok: true, deleted: N }
 *   DELETE /files/{id}     → { ok: true, deleted: N }
 *   DELETE /proxies/{id}   → { ok: true, deleted: N }
 */
object AdminApi {

    private val JSON_MEDIA = "application/json".toMediaType()

    /** Delete a server config by its ID. */
    suspend fun deleteServer(id: String): Boolean = withContext(Dispatchers.IO) {
        doDelete("${AppConstants.VLESSHUB_API_URL}/servers/$id")
    }

    /** Delete a VPN file by its ID. */
    suspend fun deleteFile(id: Long): Boolean = withContext(Dispatchers.IO) {
        doDelete("${AppConstants.VLESSHUB_API_URL}/files/$id")
    }

    /** Delete a proxy by its ID. */
    suspend fun deleteProxy(id: String): Boolean = withContext(Dispatchers.IO) {
        doDelete("${AppConstants.VLESSHUB_API_URL}/proxies/$id")
    }

    /**
     * Executes a DELETE request with admin key header.
     * Returns true when the backend confirms deletion (HTTP 200 + body `{ ok: true }`).
     */
    private fun doDelete(url: String): Boolean {
        val adminKey = AdminKeyStore.instance.getAdminKey()
        if (adminKey.isBlank()) return false

        return try {
            val client = PinnedHttpClient.newClient(callTimeoutMillis = 10_000)
            val request = Request.Builder()
                .url(url)
                .delete("".toRequestBody(JSON_MEDIA))
                .header("X-Admin-Key", adminKey)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return true // 200 OK with empty body = success
                runCatching {
                    val json = JSONObject(body)
                    json.optBoolean("ok", true)
                }.getOrDefault(true)
            }
        } catch (_: Exception) {
            false
        }
    }
}
