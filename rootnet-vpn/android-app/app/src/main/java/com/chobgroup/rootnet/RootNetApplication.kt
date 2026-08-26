package com.chobgroup.rootnet

import android.app.Application
import android.content.pm.ApplicationInfo
import com.chobgroup.rootnet.data.ads.AdiveryAdsManager
import com.chobgroup.rootnet.data.repository.ServerCacheStore
import com.chobgroup.rootnet.services.CrashlyticsService

/**
 * App entry — v2.2 config launcher.
 *
 * Initializes Adivery (the only ad network: interstitial for Copy, rewarded
 * video for Export + Refresh, banner), the local server cache, and Crashlytics
 * (release-only). Every init is non-fatal on failure — the app never crashes
 * because an ad network or cache is unreachable.
 */
class RootNetApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // The only ad network (Adivery interstitial/rewarded/banner) — non-fatal.
        AdiveryAdsManager.init(this)
        // Local server cache (config list shows instantly on next launch).
        ServerCacheStore.instance.init(this)

        // Crash reporting (release only) + global uncaught-exception handler.
        if (!isDebuggable()) {
            CrashlyticsService.enable()
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                CrashlyticsService.recordError(throwable, reason = "Uncaught on ${thread.name}")
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
