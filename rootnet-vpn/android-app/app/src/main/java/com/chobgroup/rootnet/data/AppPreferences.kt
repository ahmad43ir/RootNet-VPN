package com.chobgroup.rootnet.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight app-level preferences (non-server settings):
 * auto-connect, reconnect, animations, kill-switch hint shown.
 */
object AppPreferences {
    private const val PREFS = "rootnet_app_prefs"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_RECONNECT = "reconnect_auto"
    private const val KEY_KS_HINT_SHOWN = "ks_hint_shown"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Connect automatically on app launch when time remains. */
    fun autoConnect(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_CONNECT, false)
    fun setAutoConnect(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    /** Retry a failed connection once automatically. */
    fun reconnectAutomatically(context: Context): Boolean = prefs(context).getBoolean(KEY_RECONNECT, true)
    fun setReconnectAutomatically(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_RECONNECT, value).apply()

    fun killSwitchHintShown(context: Context): Boolean = prefs(context).getBoolean(KEY_KS_HINT_SHOWN, false)
    fun setKillSwitchHintShown(context: Context) = prefs(context).edit().putBoolean(KEY_KS_HINT_SHOWN, true).apply()
}
