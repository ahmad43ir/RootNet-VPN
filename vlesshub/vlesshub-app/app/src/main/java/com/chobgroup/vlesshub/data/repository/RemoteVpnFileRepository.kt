package com.chobgroup.vlesshub.data.repository

import com.chobgroup.vlesshub.data.AppConstants
import com.chobgroup.vlesshub.data.model.VpnFile
import com.chobgroup.vlesshub.data.remote.PinnedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import android.util.Base64

/**
 * File repository â€” the **File** tab of the config launcher.
 *
 * Lists .npvt / .sip / .npv ... files from the public `vpn_files` Supabase
 * REST read (anon SELECT â€” see migration 20260814000001) and fetches a
 * single file's raw content on demand for **Copy**. `content` is a `bytea`
 * column, which PostgREST returns base64-encoded (older/host-specific
 * deployments may emit `\x` hex) â€” [decodeBytea] handles both.
 *
 * Any failure returns an empty list / null so the UI degrades gracefully.
 */
class RemoteVpnFileRepository {

    private val TAG_DL = "VlessHubDL"

    suspend fun fetchFiles(limit: Int = 50): List<VpnFile> = withContext(Dispatchers.IO) {
        try {
            val client = PinnedHttpClient.newClient()
            val url = AppConstants.VLESSHUB_API_URL + "/files?limit=$limit"
            val request = Request.Builder()
                .url(url)
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
                val url = AppConstants.VLESSHUB_API_URL + "/files/${file.id}/content"
                val request = Request.Builder()
                    .url(url)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.e(TAG_DL, "HTTP ${response.code} for file ${file.id}")
                        return@withContext false
                    }
                    val body = response.body ?: run {
                        android.util.Log.e(TAG_DL, "null body for file ${file.id}")
                        return@withContext false
                    }
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
                    val decoded = decodeResponse(encoded) ?: run {
                        android.util.Log.e(TAG_DL, "decode failed for file ${file.id}; head=${encoded.take(40)}")
                        return@withContext false
                    }
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(decoded)
                    onProgress(1f)
                    true
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG_DL, "exception for file ${file.id}", e)
                false
            }
        }

    /**
     * PostgREST wraps the row in a JSON array: `[{"filename":..,"content":".."}]`
     * where `content` is the bytea rendered as `\x`-hex or base64. Extract the
     * content field first, then decode; fall back to decoding the raw body for
     * non-JSON responses.
     */
    private fun decodeResponse(raw: String): ByteArray? {
        val fromJson = runCatching {
            val array = org.json.JSONArray(raw)
            val content = array.getJSONObject(0).optString("content")
            if (content.isBlank()) null else decodeBytea(content)
        }.getOrNull()
        return fromJson?.let(::unwrapBase64) ?: decodeBytea(raw)?.let(::unwrapBase64)
    }

    /**
     * bytea â†’ bytes: base64 (PostgREST JSON) or `\x` hex fallback.
     * Stored payloads are themselves base64 TEXT (file â†’ base64 â†’ bytea),
     * so after the first decode we base64-decode again when the result is
     * clean ASCII base64 and the inner payload is smaller (real bytes).
     */
    private fun decodeBytea(encoded: String): ByteArray? {
        if (encoded.startsWith("\\x")) {
            return runCatching {
                val hex = encoded.substring(2)
                ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            }.getOrNull()
        }
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .recoverCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrNull()
    }

    /** Second-stage decode: base64-of-base64 stored payloads. */
    private fun unwrapBase64(bytes: ByteArray): ByteArray = runCatching {
        if (bytes.isEmpty() || bytes.size < 8) return@runCatching bytes
        val text = String(bytes, StandardCharsets.US_ASCII)
        if (!text.matches(Regex("[A-Za-z0-9+/=\r\n]+"))) return@runCatching bytes
        val inner = Base64.decode(text, Base64.DEFAULT)
        if (inner.size in 1 until bytes.size) inner else bytes
    }.getOrDefault(bytes)
}
