package com.chobgroup.vlesshub.data

import com.chobgroup.vlesshub.data.remote.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Client for the `proxy-api` Supabase Edge Function (same project as
 * RootNet — the shared MTProto proxy pool).
 *
 *   GET /proxies → { "proxies": [...], "pool_size": N, "working": N }
 *
 * Public, no auth, IP rate-limited server-side. Uses [PinnedHttpClient]
 * for certificate pinning, retry, and anti-replay headers.
 */
object ProxyApi {

    private const val BASE_URL =
        "https://vlesshub-api.mobileahmad43-a18.workers.dev"

    data class ProxyBatch(
        val proxies: List<ProxyItem>,
        val poolSize: Int,
        val working: Int,
    )

    suspend fun fetchProxies(): ProxyBatch = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            val client = PinnedHttpClient.newClient()
            val request = Request.Builder()
                .url("$BASE_URL/proxies")
                .header("Accept", "application/json")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Server returned HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) throw IOException("Empty response")
                    val json = JSONObject(body)
                    val array = json.getJSONArray("proxies")
                    val proxies = buildList {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            add(
                                ProxyItem(
                                    host = obj.getString("host"),
                                    port = obj.getInt("port"),
                                    secret = obj.optString("secret").ifEmpty { null },
                                    source = obj.optString("source").ifEmpty { null },
                                    link = obj.getString("link"),
                                ),
                            )
                        }
                    }
                    return@withContext ProxyBatch(
                        proxies = proxies,
                        poolSize = json.optInt("pool_size", 0),
                        working = json.optInt("working", 0),
                    )
                }
            } catch (e: Exception) {
                // Retry on ANY failure (network, HTTP status, JSON parse).
                lastError = e
                if (attempt == 0) delay(500) // simple backoff before retry
            }
        }
        throw lastError ?: IOException("Request failed")
    }
}
