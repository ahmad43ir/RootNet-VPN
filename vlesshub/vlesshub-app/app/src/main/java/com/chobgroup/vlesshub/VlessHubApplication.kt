package com.chobgroup.vlesshub

import android.app.Application
import com.chobgroup.vlesshub.ads.AdiveryAdsManager
import com.chobgroup.vlesshub.data.repository.ServerCacheStore

/**
 * App entry — initializes Adivery (the only ad network) and the local
 * server/file cache. Every init is non-fatal on failure.
 */
class VlessHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdiveryAdsManager.init(this)
        ServerCacheStore.instance.init(this)
    }
}
