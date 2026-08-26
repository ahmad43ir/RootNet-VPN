package com.chobgroup.vlesshub.data.remote

import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Pinned HTTPS client â€” spec Â§7.0 / Â§11.1. Ports the original app's
 * `certificate_pinning.dart` + `api_client.dart` retry/backoff behavior:
 *
 *  - **Certificate pinning** on `bprkazfxqmanrybiexnh.supabase.co` (SPKI
 *    SHA-256, primary + backup pin for rotation â€” spec Â§11.1).
 *  - **Anti-replay headers** on every request: `X-Request-Timestamp` (epoch
 *    ms) + `X-Request-Id` (unique) â€” spec Â§7.0 / Â§11.3.
 *  - **Retry** up to 2 times with exponential backoff (500ms base) on
 *    `408, 429, 500, 502, 503, 504` and IO errors â€” spec Â§7.0.
 *  - 15s call timeout (pass a smaller value for short-lived calls).
 *
 * Every API call in the app goes through this client (auth, servers, version,
 * push registration).
 */
object PinnedHttpClient {

    const val PINNED_HOST = "bprkazfxqmanrybiexnh.supabase.co"

    private val certificatePinner = CertificatePinner.Builder()
        .add(
            PINNED_HOST,
            // Primary SPKI (cert SHA-256 e4890723...3298, generated 2026-07-22).
            "sha256/ZcJbApTb7wyllleAjHw2vYAskqdT+DhMY9aPDFwAtf4=",
            // Backup pin â€” keep old pins during cert rotation (spec Â§11.1).
            "sha256/5IkHI2A4x/6wXNhi5BzX/Fco8o2mG5Xmdh2cKVxbMpg=",
        )
        .build()

    private val requestId = AtomicLong(System.currentTimeMillis())

    fun newClient(callTimeoutMillis: Long = 15_000): OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .certificatePinner(certificatePinner)
            .addInterceptor(AntiReplayInterceptor)
            .addInterceptor(RetryInterceptor)
            .build()

    /** Adds `X-Request-Timestamp` + `X-Request-Id` to every request. */
    private object AntiReplayInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("X-Request-Timestamp", System.currentTimeMillis().toString())
                .header("X-Request-Id", "${System.nanoTime()}_${requestId.incrementAndGet()}")
                .build()
            return chain.proceed(request)
        }
    }

    /** Exponential backoff on retryable statuses + IO errors â€” spec Â§7.0. */
    private object RetryInterceptor : Interceptor {
        private val retryableStatuses = setOf(408, 429, 500, 502, 503, 504)
        private const val MAX_RETRIES = 2
        private const val BASE_DELAY_MS = 500L

        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            while (true) {
                attempt++
                val response = try {
                    chain.proceed(chain.request())
                } catch (e: IOException) {
                    if (attempt > MAX_RETRIES) throw e
                    backoff(attempt)
                    continue
                }
                if (response.code !in retryableStatuses || attempt > MAX_RETRIES) return response
                response.close()
                backoff(attempt)
            }
        }

        private fun backoff(attempt: Int) {
            val delayMs = BASE_DELAY_MS * (1L shl (attempt - 1))
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
