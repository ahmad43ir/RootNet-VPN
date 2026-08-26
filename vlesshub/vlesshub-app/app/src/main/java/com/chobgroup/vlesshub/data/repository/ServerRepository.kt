package com.chobgroup.vlesshub.data.repository

import com.chobgroup.vlesshub.data.model.VpnServer

/**
 * Server fetch abstraction â€” v2.0 config launcher.
 * Implemented by [RemoteServerRepository] (public Supabase REST read).
 */
interface ServerRepository {
    suspend fun fetchServers(): List<VpnServer>
}
