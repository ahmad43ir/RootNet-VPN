package com.chobgroup.rootnet.data.repository

import com.chobgroup.rootnet.data.AppConstants
import com.chobgroup.rootnet.data.model.VpnFile
import com.chobgroup.rootnet.data.remote.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * File repository — the **File** tab of the config launcher.
 *
 * Lists .npvt / .sip / .npv ... files from the public `vpn_files` Supabase
 * REST read (anon SELECT — see migration 20260814000001) and fetches a
 * single file's raw content on demand for **Copy**. `content` is a `bytea`
 * column, which PostgREST returns base64-encoded (older/host-specific
 * deployments may emit `\x` hex) — [decodeBytea] handles both.
 *
 * Any failure returns an empty list / null so the UI degrades gracefully.
 */
class RemoteVpnFileRepository {

    suspend fun fetchFiles(limit: Int = 50): List<VpnFile> = withContext(Dispatchers.IO) {
        try {
            val client = PinnedHttpClient.newClient()
            val url = AppConstants.SUPABASE_URL + "/rest/v1/vpn_files" +
                "?select=id,filename,size_bytes,uploaded_at,is_encrypted,config_count" +
                "&order=uploaded_at.desc&limit=$limit"
            val request = Request.Builder()
                .url(url)
                .header("apikey", AppConstants.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${AppConstants.SUPABASE_ANON_KEY}")
                .build()

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
                            ),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Downloads a file's raw content into [targetFile] (app-private storage).
     * Reports [onProgress] (0f..1f) as the response is read. The `bytea`
     * `content` column arrives base64-encoded in the JSON response (or `\x`
     * hex on some hosts), so the encoded text is streamed with progress and
     * decoded once at the end. Returns `true` only when the decoded bytes were
     * written successfully.
     */
    suspend fun downloadFile(file: VpnFile, targetFile: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val client = PinnedHttpClient.newClient()
                val url = AppConstants.SUPABASE_URL + "/rest/v1/vpn_files" +
                    "?select=filename,content&id=eq.${file.id}"
                val request = Request.Builder()
                    .url(url)
                    .header("apikey", AppConstants.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${AppConstants.SUPABASE_ANON_KEY}")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body ?: return@withContext false
                    val contentLength = body.contentLength()
                    // OkHttp transparently gunzips: contentLength may be -1, so
                    // fall back to the expected base64 size of the file's bytes.
                    val expected = if (contentLength > 0) contentLength
                    else maxOf(2L, ((file.sizeBytes + 2) / 3) * 4)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val encodedOut = ByteArrayOutputStream()
                    var total = 0L
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            encodedOut.write(buffer, 0, read)
                            total += read
                            onProgress((total.toFloat() / expected).coerceIn(0f, 1f))
                        }
                    }
                    val encoded = String(encodedOut.toByteArray(), StandardCharsets.UTF_8)
                    val decoded = decodeBytea(encoded) ?: return@withContext false
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(decoded)
                    onProgress(1f)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

    /** bytea → bytes: base64 (PostgREST JSON) or `\x` hex fallback. */
    private fun decodeBytea(encoded: String): ByteArray? {
        if (encoded.startsWith("\\x")) {
            return runCatching {
                val hex = encoded.substring(2)
                ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            }.getOrNull()
        }
        return runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
    }
}
