package com.chobgroup.vlesshub.ads

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Adivery wiring â€” the ONLY ad network for ProxyBox (AdMob removed, mirrors
 * RootNet v2.3).
 *
 *  - **Interstitial** (picture ad) â†’ every **3rd Copy/Share/Open** tap on a
 *    DIFFERENT proxy (combined counter) â€” the action completes when it closes;
 *    capped to one per 60s app-wide.
 *  - **Rewarded video** â†’ the "Get a new batch" refresh gate + the lock-gate
 *    escape (watch to the end; a skip keeps the screen locked).
 *  - **Banner** â†’ pinned at the bottom of the proxy list.
 *
 * All IDs are placeholders â€” the user will paste the real Adivery App ID and
 * placement UUIDs after this migration. While they're placeholders the manager
 * reports not-configured and every action proceeds without an ad (no lockout),
 * so the app never bricks before the real IDs are wired.
 *
 * Reward rule (same as RootNet spec Â§10.2): the reward is granted ONLY from
 * Adivery's official callback `onRewardedAdClosed(placementId, isRewarded)`
 * when `isRewarded == true` â€” never because the ad was requested or shown.
 *
 * API verified against `com.adivery:sdk:4.9.0` (Maven Central AAR, 2026-08).
 */
object AdiveryAdsManager {

    // â”€â”€ CONFIGURATION â€” Adivery dashboard (ProxyBox app) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // App code `0d0ce797-932b-4190-8b1a-79e1b504786d` with three placements:
    // image (interstitial) d5738d1f-â€¦, video (rewarded) af125f4e-â€¦.
    // The banner placement is not wired yet â€” leave the placeholder so the
    // banner stays hidden until a banner placement is created.
    private const val APP_ID = "0d0ce797-932b-4190-8b1a-79e1b504786d"
    private const val INTERSTITIAL_PLACEMENT_ID = "d5738d1f-0a34-4cf9-8b1c-359bf2bf3bd7"
    private const val REWARDED_PLACEMENT_ID = "af125f4e-6dbd-429d-99ac-956ddd254fa9"
    private const val BANNER_PLACEMENT_ID = "REPLACE_WITH_BANNER_PLACEMENT_ID"

    private const val TAG = "AdiveryAdsManager"
    private const val REWARDED_TIMEOUT_MS = 90_000L
    // One picture (interstitial) ad per 60s max â€” app-wide (mirrors RootNet).
    private const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    @Volatile
    private var configured = false

    private var appContext: Context? = null

    /** Pending interstitial "closed" continuation (at most one at a time). */
    @Volatile
    private var pendingInterstitial: (() -> Unit)? = null

    /** Pending rewarded continuation, resumed from onRewardedAdClosed. */
    @Volatile
    private var pendingReward: ((Boolean) -> Unit)? = null

    private var lastInterstitialShownAt = 0L

    // â”€â”€ Initialization â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Called once from [com.chobgroup.vlesshub.MainActivity]. */
    fun init(context: Context) {
        if (configured) return
        configured = true
        appContext = context.applicationContext
        runCatching {
            Adivery.configure(appContext as Application, APP_ID)
            Adivery.addGlobalListener(object : AdiveryListener() {
                override fun onInterstitialAdClosed(placementId: String) {
                    Log.i(TAG, "Interstitial closed: $placementId")
                    reloadInterstitial()
                    pendingInterstitial?.invoke()
                    pendingInterstitial = null
                }

                override fun onInterstitialAdLoaded(placementId: String) {
                    Log.i(TAG, "Interstitial loaded: $placementId")
                }

                override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
                    Log.i(TAG, "Rewarded closed: $placementId rewarded=$isRewarded")
                    // âš  Reward is granted ONLY from this official callback when
                    // isRewarded == true â€” never on show/request.
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
            if (isConfigured(INTERSTITIAL_PLACEMENT_ID)) {
                Adivery.prepareInterstitialAd(ctx, INTERSTITIAL_PLACEMENT_ID)
            }
            if (isConfigured(REWARDED_PLACEMENT_ID)) {
                Adivery.prepareRewardedAd(ctx, REWARDED_PLACEMENT_ID)
            }
        }.onFailure {
            Log.w(TAG, "Adivery init failed â€” actions proceed without ads", it)
        }
    }

    // â”€â”€ Interstitial (3rd-distinct-tap gate, mirrors RootNet) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun isInterstitialReady(): Boolean =
        isConfigured(INTERSTITIAL_PLACEMENT_ID) && isLoaded(INTERSTITIAL_PLACEMENT_ID)

    /**
     * Shows the Adivery interstitial if loaded (and the app-wide cooldown is
     * clear), then calls [onFinished] when the user closes it. If the ad isn't
     * ready or the cooldown is active, the callback is NOT invoked â€” the
     * caller sees `false` and runs the lock gate instead (mirrors RootNet
     * v2.3). Returns `true` when the ad was actually shown (or one is already
     * on screen â€” its pending callback fires on close).
     */
    fun maybeShowInterstitial(onFinished: () -> Unit): Boolean {
        if (!isInterstitialReady()) return false
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) return false
        if (pendingInterstitial != null) {
            // An ad is already on screen â€” don't stack one on top and don't
            // trigger the fallback; the already-pending callback fires on close.
            return true
        }
        pendingInterstitial = onFinished
        return runCatching {
            lastInterstitialShownAt = now
            Adivery.showAd(INTERSTITIAL_PLACEMENT_ID)
            true
        }.onFailure {
            Log.w(TAG, "Interstitial show failed", it)
            pendingInterstitial = null
            onFinished()
        }.getOrElse { false }
    }

    // â”€â”€ Rewarded video (refresh gate + lock-gate escape) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun isRewardedReady(): Boolean =
        isConfigured(REWARDED_PLACEMENT_ID) && isLoaded(REWARDED_PLACEMENT_ID)

    /** True once the user has pasted real placement IDs (placeholders = off). */
    fun isRewardedConfigured(): Boolean =
        isConfigured(REWARDED_PLACEMENT_ID)

    /** Kicks a fresh rewarded-ad load (safe to call any time). */
    fun prepareRewarded() {
        reloadRewarded()
    }

    /**
     * Suspends until the rewarded video is loaded (polling [isRewardedReady]),
     * kicking a load first if needed. Returns `true` when a rewarded ad is
     * ready to show within [timeoutMs], `false` otherwise. Used by the lock
     * overlay so the UI can show "Finding adâ€¦" while Adivery fills.
     */
    suspend fun awaitRewardedReady(timeoutMs: Long = 60_000L): Boolean {
        if (isRewardedReady()) return true
        prepareRewarded()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            if (isRewardedReady()) return true
        }
        return isRewardedReady()
    }

    /**
     * Shows the Adivery rewarded video, suspending until it closes. Returns
     * `true` ONLY when the user earned the reward (`onRewardedAdClosed` with
     * `isRewarded == true`). `false` if not ready, show fails, or the user
     * skipped â€” the caller then shows a snackbar or proceeds (no lockout).
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

    // â”€â”€ Banner (bottom of proxy list) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Persistent banner pinned under the list â€” Adivery only. */
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

    // â”€â”€ Internals â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun isLoaded(placementId: String): Boolean = runCatching {
        Adivery.isLoaded(placementId)
    }.getOrDefault(false)

    private fun isConfigured(placementId: String): Boolean =
        !placementId.startsWith("REPLACE_") && appContext != null

    private fun reloadInterstitial() {
        appContext?.let {
            if (isConfigured(INTERSTITIAL_PLACEMENT_ID)) {
                runCatching { Adivery.prepareInterstitialAd(it, INTERSTITIAL_PLACEMENT_ID) }
            }
        }
    }

    private fun reloadRewarded() {
        appContext?.let {
            if (isConfigured(REWARDED_PLACEMENT_ID)) {
                runCatching { Adivery.prepareRewardedAd(it, REWARDED_PLACEMENT_ID) }
            }
        }
    }
}
