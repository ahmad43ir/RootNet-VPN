package com.chobgroup.rootnet.data

/**
 * PUBLIC-only configuration — mirrors the original app's public constants.
 * Never put service-role keys / admin secrets here.
 */
object AppConstants {
    const val SUPABASE_URL = "https://bprkazfxqmanrybiexnh.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_h2oEryaNO2GWDEYw-flm3A_EV9pP9Co"
    const val API_URL = "$SUPABASE_URL/functions/v1/rootnet-api"

    const val UPDATE_URL = "https://chobgroup.pages.dev"
    const val PRIVACY_POLICY_URL = "https://chobgroup.pages.dev/privacy.html"

    /** Reliable regional fallback for the landing page (pages.dev is blocked
     *  in the target region — served via the Cloudflare reverse-proxy Worker). */
    const val PROXY_LANDING_URL = "https://rootnet-proxy.mobileahmad43-a18.workers.dev"

    /** Popular VLESS client apps — "Install" targets on the Settings help screen. */
    const val CLIENT_V2RAYNG_PACKAGE = "com.v2ray.ang"
    const val CLIENT_NEKOBOX_PACKAGE = "com.nick.mobile"
    const val CLIENT_HIDDIFY_PACKAGE = "app.hiddify.com"
}
