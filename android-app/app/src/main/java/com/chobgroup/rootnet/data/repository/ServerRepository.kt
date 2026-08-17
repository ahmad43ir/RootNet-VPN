package com.chobgroup.rootnet.data.repository

import com.chobgroup.rootnet.data.model.VpnServer

/**
 * Server fetch abstraction — v2.0 config launcher.
 * Implemented by [RemoteServerRepository] (public Supabase REST read).
 */
interface ServerRepository {
    suspend fun fetchServers(): List<VpnServer>
}
