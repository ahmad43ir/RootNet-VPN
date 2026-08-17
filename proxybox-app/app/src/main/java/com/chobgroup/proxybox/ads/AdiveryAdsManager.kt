package com.chobgroup.proxybox.ads

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryAdListener
import com.adivery.sdk.AdiveryBannerAdView
import com.adivery.sdk.AdiveryListener
import com.adivery.sdk.BannerSize
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Adivery wiring — the ONLY ad network for ProxyBox (AdMob removed, mirrors
 * RootNet v2.2).
 *
 *  - **Rewarded video** → the "Get a new batch" refresh gate (watch to the end
 *    to fetch; skipped → snackbar, no fetch; no ad available → fetch anyway,
 *    no-lockout).
 *  - **Banner** → pinned at the bottom of the proxy list.
 *
 * All IDs are placeholders — the user will paste the real Adivery App ID and
 * placement UUIDs after this migration. While they're placeholders the manager
 * reports not-ready and every action proceeds without an ad (no lockout).
 *
 * Reward rule (same as RootNet spec §10.2): the reward is granted ONLY from
 * Adivery's official callback `onRewardedAdClosed(placementId, isRewarded)`
 * when `isRewarded == true` — never because the ad was requested or shown.
 *
 * API verified against `com.adivery:sdk:4.9.0` (Maven Central AAR, 2026-08).
 */
object AdiveryAdsManager {

    // ── CONFIGURATION — replace with the real values from the Adivery dashboard ──
    private const val APP_ID = "REPLACE_WITH_PROXYBOX_APP_ID"
    private const val REWARDED_PLACEMENT_ID = "REPLACE_WITH_REWARDED_PLACEMENT_ID"
    private const val BANNER_PLACEMENT_ID = "REPLACE_WITH_BANNER_PLACEMENT_ID"

    private const val TAG = "AdiveryAdsManager"
    private const val REWARDED_TIMEOUT_MS = 90_000L

    @Volatile
    private var configured = false

    private var appContext: Context? = null

    /** Pending rewarded continuation, resumed from onRewardedAdClosed. */
    @Volatile
    private var pendingReward: ((Boolean) -> Unit)? = null

    // ── Initialization ──────────────────────────────────────────────────────

    /** Called once from [com.chobgroup.proxybox.MainActivity]. */
    fun init(context: Context) {
        if (configured) return
        configured = true
        appContext = context.applicationContext
        runCatching {
            Adivery.configure(appContext as Application, APP_ID)
            Adivery.addGlobalListener(object : AdiveryListener() {
                override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
                    Log.i(TAG, "Rewarded closed: $placementId rewarded=$isRewarded")
                    // ⚠ Reward is granted ONLY from this official callback when
                    // isRewarded == true — never on show/request.
                    reloadRewarded()
                    pendingReward?.invoke(isRewarded)
                    pendingReward = null
                }

                override fun onRewardedAdLoaded(placementId: String) {
                    Log.i(TAG, "Rewarded loaded: $placementId")
                }

                override fun log(tag: String, message: String) {
                    Log.d("Adivery-$tag", message)
                }
            })
            val ctx: Context = appContext ?: return@runCatching
            if (isConfigured(REWARDED_PLACEMENT_ID)) {
                Adivery.prepareRewardedAd(ctx, REWARDED_PLACEMENT_ID)
            }
        }.onFailure {
            Log.w(TAG, "Adivery init failed — actions proceed without ads", it)
        }
    }

    // ── Rewarded video (refresh gate) ───────────────────────────────────────

    fun isRewardedReady(): Boolean =
        isConfigured(REWARDED_PLACEMENT_ID) && isLoaded(REWARDED_PLACEMENT_ID)

    /**
     * Shows the Adivery rewarded video, suspending until it closes. Returns
     * `true` ONLY when the user earned the reward (`onRewardedAdClosed` with
     * `isRewarded == true`). `false` if not ready, show fails, or the user
     * skipped — the caller then shows a snackbar or proceeds (no lockout).
     */
    suspend fun showRewardedAd(): Boolean {
        if (!isRewardedReady()) return false
        return withTimeoutOrNull(REWARDED_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                if (pendingReward != null) {
                    cont.resume(false)
                    return@suspendCancellableCoroutine
                }
                pendingReward = { rewarded ->
                    if (cont.isActive) cont.resume(rewarded)
                }
                // Drop the slot if the awaiting coroutine is cancelled
                // (timeout / screen disposed) so the next request shows a fresh
                // ad instead of skipping straight through.
                cont.invokeOnCancellation { pendingReward = null }
                runCatching { Adivery.showAd(REWARDED_PLACEMENT_ID) }.onFailure {
                    Log.w(TAG, "Rewarded show failed", it)
                    pendingReward = null
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
    }

    // ── Banner (bottom of proxy list) ───────────────────────────────────────

    /** Persistent banner pinned under the list — Adivery only. */
    @Composable
    fun BannerAdView(modifier: Modifier = Modifier) {
        if (isConfigured(BANNER_PLACEMENT_ID)) {
            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    AdiveryBannerAdView(ctx).apply {
                        setPlacementId(BANNER_PLACEMENT_ID)
                        setBannerSize(BannerSize.BANNER)
                        setRetryOnError(true)
                        setBannerAdListener(object : AdiveryAdListener() {
                            override fun onAdLoaded() {
                                Log.i(TAG, "Banner loaded")
                            }

                            override fun onError(message: String) {
                                Log.w(TAG, "Banner error: $message")
                            }
                        })
                        loadAd()
                    }
                },
            )
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun isLoaded(placementId: String): Boolean = runCatching {
        Adivery.isLoaded(placementId)
    }.getOrDefault(false)

    private fun isConfigured(placementId: String): Boolean =
        !placementId.startsWith("REPLACE_") && appContext != null

    private fun reloadRewarded() {
        appContext?.let {
            if (isConfigured(REWARDED_PLACEMENT_ID)) {
                runCatching { Adivery.prepareRewardedAd(it, REWARDED_PLACEMENT_ID) }
            }
        }
    }
}
