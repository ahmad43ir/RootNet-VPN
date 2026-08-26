package com.chobgroup.vlesshub.data

/**
 * PUBLIC-only configuration â€” mirrors the original app's public constants.
 * Never put service-role keys / admin secrets here.
 */
object AppConstants {
    const val SUPABASE_URL = "https://bprkazfxqmanrybiexnh.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_h2oEryaNO2GWDEYw-flm3A_EV9pP9Co"
    const val API_URL = "$SUPABASE_URL/functions/v1/rootnet-api"

    const val UPDATE_URL = "https://chobgroup.pages.dev"
    const val PRIVACY_POLICY_URL = "https://chobgroup.pages.dev/privacy.html"

    /** Chob Group hub page (About + all projects). */
    const val WEBSITE_URL = "https://chobgroup.pages.dev"

    /** Support contact for channel owners ("add my channel" / "remove my
     *  channel" requests). Same address the privacy policy lists. */
    const val CONTACT_EMAIL = "privacy@rootnet.app"

    /** Email subject presets for the Settings → Support actions. */
    const val SUBJECT_REMOVE_CHANNEL = "Remove my Telegram channel from VlessHub"
    const val SUBJECT_ADD_CHANNEL = "Add my Telegram channel to VlessHub"

    /** Reliable regional fallback for the landing page (pages.dev is blocked
     *  in the target region â€” served via the Cloudflare reverse-proxy Worker). */
    const val PROXY_LANDING_URL = "https://rootnet-proxy.mobileahmad43-a18.workers.dev"

    /** Popular VLESS client apps â€” "Install" targets on the Settings help screen. */
    const val CLIENT_V2RAYNG_PACKAGE = "com.v2ray.ang"
    const val CLIENT_NEKOBOX_PACKAGE = "com.nick.mobile"
    const val CLIENT_HIDDIFY_PACKAGE = "app.hiddify.com"
}
