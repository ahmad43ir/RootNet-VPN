package com.chobgroup.admin_vlesshub.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the admin API key used for DELETE operations.
 * The key is entered once in Settings and reused across sessions.
 */
class AdminKeyStore private constructor() {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("admin_key_store", Context.MODE_PRIVATE)
    }

    fun getAdminKey(): String = prefs.getString(KEY_ADMIN_KEY, "") ?: ""

    fun setAdminKey(key: String) {
        prefs.edit().putString(KEY_ADMIN_KEY, key).apply()
    }

    fun hasKey(): Boolean = getAdminKey().isNotBlank()

    companion object {
        private const val KEY_ADMIN_KEY = "admin_api_key"
        val instance: AdminKeyStore by lazy { AdminKeyStore() }
    }
}
