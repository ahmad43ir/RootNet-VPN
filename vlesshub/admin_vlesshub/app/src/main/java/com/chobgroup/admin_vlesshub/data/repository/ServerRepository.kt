package com.chobgroup.admin_vlesshub.data.repository

import com.chobgroup.admin_vlesshub.data.model.VpnServer

interface ServerRepository {
    suspend fun fetchServers(): List<VpnServer>
}
