package com.chobgroup.admin_vlesshub.data

import com.chobgroup.admin_vlesshub.data.model.ProxyItem
import com.chobgroup.admin_vlesshub.data.remote.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object ProxyApi {

    private const val BASE_URL = "https://vlesshub-api.mobileahmad43-a18.workers.dev"

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
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
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
                                    id = obj.optString("id", "").ifEmpty { null },
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
                lastError = e
                if (attempt == 0) delay(500)
            }
        }
        throw lastError ?: IOException("Request failed")
    }
}
