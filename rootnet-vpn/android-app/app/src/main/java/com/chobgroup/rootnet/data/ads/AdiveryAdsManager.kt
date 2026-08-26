package com.chobgroup.rootnet.data.ads

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
 * Adivery wiring — the primary ad network for RootNet v2.2.
 *
 *  - **Interstitial** (picture ad) → every **3rd Copy/Export** tap
 *    (combined counter) — the action completes when it closes.
 *  - **Rewarded video** → the **File-tab entry** + **Refresh** gates — the
 *    action only happens after a full watch (skips stay locked).
 *  - **Banner** (picture ad) → pinned at the bottom of the server list.
 *
 * App ID `73697db8-c7dc-4af2-9f3c-dd422942cf57` (Adivery dashboard) with three
 * placements: `image_rootnet` (interstitial), `video_rootnet` (rewarded),
 * `banner_rootnet` (banner). **Adivery is the ONLY ad network** (v2.2 — AdMob
 * and Unity Ads were removed). If Adivery can't load/show, the gated action
 * still completes without an ad — the no-lockout rule (spec §10.3) is kept.
 *
 * Reward rule (spec §10.2): the reward is granted ONLY from Adivery's official
 * reward callback `AdiveryListener.onRewardedAdClosed(placementId, isRewarded)`
 * when `isRewarded == true` — never because the ad was requested or shown.
 *
 * API verified against `com.adivery:sdk:4.9.0` (Maven Central AAR, 2026-08):
 *   Adivery.configure(Application, appId)
 *   Adivery.prepareInterstitialAd(Context, placementId)
 *   Adivery.prepareRewardedAd(Context, placementId)
 *   Adivery.isLoaded(placementId): Boolean
 *   Adivery.showAd(placementId)
 *   Adivery.addGlobalListener(AdiveryListener)
 *   AdiveryBannerAdView — setPlacementId / setBannerSize / loadAd /
 *     setRetryOnError / setBannerAdListener(AdiveryAdListener)
 */
object AdiveryAdsManager {

    // ── CONFIGURATION ───────────────────────────────────────────────────────
    private const val APP_ID = "73697db8-c7dc-4af2-9f3c-dd422942cf57"

    // Adivery placement IDs — UUIDs created in the Adivery dashboard.
    // ✅ image_rootnet (interstitial) · video_rootnet (rewarded) ·
    //    banner_rootnet (banner) — all wired.
    private const val INTERSTITIAL_PLACEMENT_ID = "9c9a3a33-1229-4d71-b327-56658da33940"
    private const val REWARDED_PLACEMENT_ID = "a354b16e-4a45-4642-8093-1224f163d7a1"
    private const val BANNER_PLACEMENT_ID = "ede3a8e3-c166-4858-9802-70281a5dd2c9"

    private const val TAG = "AdiveryAdsManager"
    private const val REWARDED_TIMEOUT_MS = 90_000L
    // One picture (interstitial) ad per 60s max — app-wide. This caps the
    // image ads to exactly one per window no matter how many gated actions
    // (Copy/Export 3rd tap, downloads) fire in a row, so a single interaction
    // can never surface two image ads back to back.
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

    // ── Initialization ──────────────────────────────────────────────────────

    /** Called once from [com.chobgroup.rootnet.RootNetApplication]. */
    fun init(application: Application) {
        if (configured) return
        configured = true
        appContext = application.applicationContext
        runCatching {
            Adivery.configure(application, APP_ID)
            Adivery.addGlobalListener(object : AdiveryListener() {
                override fun onInterstitialAdClosed(placementId: String) {
                    Log.i(TAG, "Interstitial closed: $placementId")
                    reloadInterstitial()
                    pendingInterstitial?.invoke()
                    pendingInterstitial = null
                }

                override fun onRewardedAdClosed(placementId: String, isRewarded: Boolean) {
                    Log.i(TAG, "Rewarded closed: $placementId rewarded=$isRewarded")
                    // ⚠ Reward is granted ONLY from this official callback when
                    // isRewarded == true (spec §10.2) — never on show/request.
                    reloadRewarded()
                    pendingReward?.invoke(isRewarded)
                    pendingReward = null
                }

                override fun onRewardedAdLoaded(placementId: String) {
                    Log.i(TAG, "Rewarded loaded: $placementId")
                }

                override fun onInterstitialAdLoaded(placementId: String) {
                    Log.i(TAG, "Interstitial loaded: $placementId")
                }

                override fun log(tag: String, message: String) {
                    Log.d("Adivery-$tag", message)
                }
            })
            // Preload both full-screen formats at startup (spec: preload, don't
            // wait for the user to tap).
            val context = appContext ?: return@runCatching
            if (isConfigured(INTERSTITIAL_PLACEMENT_ID)) {
                Adivery.prepareInterstitialAd(context, INTERSTITIAL_PLACEMENT_ID)
            }
            if (isConfigured(REWARDED_PLACEMENT_ID)) {
                Adivery.prepareRewardedAd(context, REWARDED_PLACEMENT_ID)
            }
        }.onFailure {
            Log.w(TAG, "Adivery init failed — falling back to legacy networks", it)
        }
    }

    // ── Interstitial (Copy gate) ────────────────────────────────────────────

    fun isInterstitialReady(): Boolean =
        isConfigured(INTERSTITIAL_PLACEMENT_ID) && isLoaded(INTERSTITIAL_PLACEMENT_ID)

    /**
     * Shows the Adivery interstitial if loaded (and the app-wide cooldown is
     * clear), then calls [onFinished] when the user closes it. If the ad isn't
     * ready or the cooldown is active, the callback is NOT invoked — the
     * caller sees `false` and decides what to do next (the app's rule: the
     * screen locks until an ad is watched). Returns `true` when the ad was
     * actually shown (or one is already on screen — its pending callback
     * fires on close).
     */
    fun maybeShowInterstitial(onFinished: () -> Unit): Boolean {
        if (!isInterstitialReady()) return false
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) return false
        if (pendingInterstitial != null) {
            // An ad is already on screen — don't stack one on top and don't
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

    // ── Rewarded video (File-tab entry + Refresh gates) ─────────────────────

    fun isRewardedReady(): Boolean =
        isConfigured(REWARDED_PLACEMENT_ID) && isLoaded(REWARDED_PLACEMENT_ID)

    /** Kicks a fresh rewarded-ad load (safe to call any time). */
    fun prepareRewarded() {
        reloadRewarded()
    }

    /**
     * Suspends until the rewarded video is loaded (polling [isRewardedReady]),
     * kicking a load first if needed. Returns `true` when a rewarded ad is
     * ready to show within [timeoutMs], `false` otherwise. Used by the lock
     * overlay so the UI can show "Finding ad…" while Adivery fills.
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
     * skipped — the caller then falls back to Unity or aborts (no lockout).
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
                // If the awaiting coroutine is cancelled (timeout / screen
                // disposed), drop the slot so the next request shows a fresh ad
                // instead of skipping straight to the fallback.
                cont.invokeOnCancellation { pendingReward = null }
                runCatching { Adivery.showAd(REWARDED_PLACEMENT_ID) }.onFailure {
                    Log.w(TAG, "Rewarded show failed", it)
                    pendingReward = null
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
    }

    // ── Banner (bottom of server list) ──────────────────────────────────────

    /**
     * Persistent banner pinned under the list — Adivery only. If the placement
     * has no fill yet (new placements return "No value for networks" until ad
     * networks are attached in the dashboard), the view stays empty; retry is
     * on so it picks up a fill once the dashboard side is ready.
     */
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
