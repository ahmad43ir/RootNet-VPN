package com.chobgroup.admin_vlesshub

import android.app.Application
import com.chobgroup.admin_vlesshub.data.AdminKeyStore

class AdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdminKeyStore.instance.init(this)
    }
}
