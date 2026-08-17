package com.chobgroup.rootnet.services

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crashlytics wrapper — spec §11.6. Ports the original `crashlytics_service.dart`:
 * non-fatal error recording, user-ID association on login/logout, breadcrumb
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

    /** Associate the current user with subsequent crash reports (call on login). */
    fun onUserLoggedIn(userId: String) {
        if (!enabled) return
        runCatching {
            FirebaseCrashlytics.getInstance().setUserId(userId)
            Log.i(TAG, "User ID set — ${userId.take(8)}...")
        }
    }

    /** Clear the user association (call on logout). */
    fun onUserLoggedOut() {
        if (!enabled) return
        runCatching { FirebaseCrashlytics.getInstance().setUserId("") }
    }
}
