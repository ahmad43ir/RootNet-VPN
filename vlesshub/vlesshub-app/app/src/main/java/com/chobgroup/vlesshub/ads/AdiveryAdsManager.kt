package com.chobgroup.vlesshub.ads

import android.app.Application
import android.content.Context
import android.util.Log
import com.adivery.sdk.Adivery
import com.adivery.sdk.AdiveryListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Adivery wiring — the ONLY ad network for VlessHub.
 *
 *  - **Interstitial** (picture ad) → every 5th Copy/Share/Open tap on a
 *    DIFFERENT proxy (combined counter) — the action completes when it closes;
 *    capped to one per 60s app-wide.
 *  - **Rewarded video** → the "Get a new batch" refresh gate + the lock-gate
 *    escape (watch to the end; a skip keeps the screen locked).
 *
 * Reward rule: the reward is granted ONLY from Adivery's official callback
 * `onRewardedAdClosed(placementId, isRewarded)` when `isRewarded == true` —
 * never because the ad was requested or shown.
 *
 * API verified against `com.adivery:sdk:4.9.0` (Maven Central AAR, 2026-08).
 */
object AdiveryAdsManager {

    // -- CONFIGURATION — Adivery dashboard (VlessHub app) --
    private const val APP_ID = "3bfedc15-0410-4bff-b593-98d89fe3f7b6"
    private const val INTERSTITIAL_PLACEMENT_ID = "459ffbdd-40f3-43d1-b4eb-d5ddb864b25c"
    private const val REWARDED_PLACEMENT_ID = "459ffbdd-40f3-43d1-b4eb-d5ddb864b25c"

    private const val TAG = "AdiveryAdsManager"
    private const val REWARDED_TIMEOUT_MS = 90_000L
    // One picture (interstitial) ad per 60s max — app-wide.
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

    // -- Initialization --

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
                    // Reward is granted ONLY from this official callback when
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
            if (isConfigured(INTERSTITIAL_PLACEMENT_ID)) {
                Adivery.prepareInterstitialAd(ctx, INTERSTITIAL_PLACEMENT_ID)
            }
            if (isConfigured(REWARDED_PLACEMENT_ID)) {
                Adivery.prepareRewardedAd(ctx, REWARDED_PLACEMENT_ID)
            }
        }.onFailure {
            Log.w(TAG, "Adivery init failed — actions proceed without ads", it)
        }
    }

    // -- Interstitial (5th-distinct-tap gate) --

    fun isInterstitialReady(): Boolean =
        isConfigured(INTERSTITIAL_PLACEMENT_ID) && isLoaded(INTERSTITIAL_PLACEMENT_ID)

    /**
     * Shows the Adivery interstitial if loaded (and the app-wide cooldown is
     * clear), then calls [onFinished] when the user closes it. If the ad isn't
     * ready or the cooldown is active, the callback is NOT invoked — the
     * caller sees `false` and runs the lock gate instead. Returns `true` when
     * the ad was actually shown (or one is already on screen — its pending
     * callback fires on close).
     */
    fun maybeShowInterstitial(onFinished: () -> Unit): Boolean {
        if (!isInterstitialReady()) return false
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) return false
        if (pendingInterstitial != null) {
            // An ad is already on screen — don't stack one on top.
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

    // -- Rewarded video (refresh gate + lock-gate escape) --

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
     * ready to show within [timeoutMs], `false` otherwise.
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
     * Kicks a fresh interstitial load and suspends until it's ready
     * (polling [isInterstitialReady]). Returns true when an interstitial
     * can be shown within [timeoutMs], false otherwise.
     */
    suspend fun awaitInterstitialReady(timeoutMs: Long = 10_000L): Boolean {
        if (isInterstitialReady()) return true
        appContext?.let {
            if (isConfigured(INTERSTITIAL_PLACEMENT_ID)) {
                runCatching { Adivery.prepareInterstitialAd(it, INTERSTITIAL_PLACEMENT_ID) }
            }
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            if (isInterstitialReady()) return true
        }
        return isInterstitialReady()
    }

    /**
     * Shows the Adivery rewarded video, suspending until it closes. Returns
     * `true` ONLY when the user earned the reward (`onRewardedAdClosed` with
     * `isRewarded == true`). `false` if not ready, show fails, or the user
     * skipped.
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
                cont.invokeOnCancellation { pendingReward = null }
                runCatching { Adivery.showAd(REWARDED_PLACEMENT_ID) }.onFailure {
                    Log.w(TAG, "Rewarded show failed", it)
                    pendingReward = null
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
    }

    // -- Internals --

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
