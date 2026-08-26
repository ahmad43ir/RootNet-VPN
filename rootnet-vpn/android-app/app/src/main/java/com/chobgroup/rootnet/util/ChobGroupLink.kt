package com.chobgroup.rootnet.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chobgroup.rootnet.data.AppConstants
import com.chobgroup.rootnet.data.remote.GeoIpResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens the Chob Group hub page. pages.dev is blocked in Iran, so when the
 * device's public IP is Iranian the workers.dev reverse-proxy mirror is used
 * instead (see [AppConstants.PROXY_LANDING_URL]). On any lookup failure the
 * regular URL opens — the button never dead-ends.
 */
object ChobGroupLink {

    fun open(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val iran = runCatching { GeoIpResolver.isDeviceInIran() }.getOrDefault(false)
            val url = if (iran) AppConstants.PROXY_LANDING_URL else AppConstants.UPDATE_URL
            withContext(Dispatchers.Main) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
    }
}
