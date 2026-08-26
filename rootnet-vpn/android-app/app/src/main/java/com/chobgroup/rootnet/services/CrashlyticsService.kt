package com.chobgroup.rootnet.services

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crashlytics wrapper — spec A11.6. Non-fatal error recording and breadcrumb
 * logging. All methods degrade gracefully — a reporting failure never crashes
 * the app. Only enabled from release builds ([enable] is called there).
 */
object CrashlyticsService {
    private const val TAG = "CrashlyticsService"

    @Volatile
    private var enabled = false

    fun enable() {
        enabled = true
        Log.i(TAG, "Crashlytics enabled")
    }

    fun isEnabled(): Boolean = enabled

    /** Record a non-fatal (handled) exception. */
    fun recordError(throwable: Throwable, reason: String? = null) {
        if (!enabled) return
        runCatching {
            FirebaseCrashlytics.getInstance().recordException(
                if (reason != null) RuntimeException(reason, throwable) else throwable,
            )
        }
    }

    /** Log a breadcrumb describing the user's journey. */
    fun log(message: String) {
        if (!enabled) return
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }

    /** Set a custom key-value pair for crash reports. */
    fun setCustomKey(key: String, value: String) {
        if (!enabled) return
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }
}
