package com.chobgroup.vlesshub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the `proxy-api` Supabase Edge Function (same project as
 * RootNet â€” the shared MTProto proxy pool).
 *
 *   GET /proxies â†’ { "proxies": [...], "pool_size": N, "working": N }
 *
 * Public, no auth, IP rate-limited server-side. 15s timeout, one retry.
 */
object ProxyApi {

    private const val BASE_URL =
        "https://vlesshub-api.mobileahmad43-a18.workers.dev"

    private const val TIMEOUT_MS = 15_000

    data class ProxyBatch(
        val proxies: List<ProxyItem>,
        val poolSize: Int,
        val working: Int,
    )

    suspend fun fetchProxies(): ProxyBatch = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            val connection = URL("$BASE_URL/proxies").openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("Server returned HTTP $code")
                }
                val json = JSONObject(connection.inputStream.bufferedReader().readText())
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
            } catch (e: Exception) {
                // Retry on ANY failure (network, HTTP status, JSON parse).
                lastError = e
                if (attempt == 0) delay(500) // simple backoff before retry
            } finally {
                connection.disconnect()
            }
        }
        throw lastError ?: IOException("Request failed")
    }
}
