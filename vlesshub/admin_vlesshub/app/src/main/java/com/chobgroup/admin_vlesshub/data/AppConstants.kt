package com.chobgroup.admin_vlesshub.data

/**
 * PUBLIC-only configuration — same endpoints as VlessHub.
 * Admin key is stored locally via AdminKeyStore, never hardcoded here.
 */
object AppConstants {
    const val SUPABASE_URL = "https://bprkazfxqmanrybiexnh.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_h2oEryaNO2GWDEYw-flm3A_EV9pP9Co"

    /** VlessHub data plane — Cloudflare Worker on D1. */
    const val VLESSHUB_API_URL = "https://vlesshub-api.mobileahmad43-a18.workers.dev"

    /** Supabase REST for direct proxy management. */
    const val SUPABASE_REST_URL = "$SUPABASE_URL/rest/v1"
}
