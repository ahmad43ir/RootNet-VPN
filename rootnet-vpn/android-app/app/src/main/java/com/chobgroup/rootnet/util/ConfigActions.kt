package com.chobgroup.rootnet.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

/**
 * v2.0 config-launcher actions — the app no longer runs a VPN engine. It hands
 * configs to the user: copy to the clipboard, or open them in whatever client
 * app is installed (v2rayNG, NekoBox, Hiddify, ...) via Android's default
 * scheme resolution (vless://, vmess://, trojan://, ss://, ...).
 */
object ConfigActions {

    /** True when the raw config looks like a URI a client app can handle. */
    fun isLinkLike(raw: String): Boolean = raw.trim().contains("://")

    /** Copies the raw config to the system clipboard. */
    fun copyToClipboard(context: Context, label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    /**
     * Opens the config with the default handler for its scheme. Returns `false`
     * when no app can handle it (e.g. no client installed, or the config is not
     * a URI). Never throws.
     */
    fun openWithDefaultApp(context: Context, raw: String): Boolean {
        if (!isLinkLike(raw)) return false
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return runCatching {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Opens the Play Store page for a client app (falls back to the web URL). */
    fun openPlayStore(context: Context, packageName: String) {
        val intent = runCatching {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            if (market.resolveActivity(context.packageManager) != null) market else null
        }.getOrNull() ?: Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Resolves the host [Activity] even when Compose wraps the context in a
 * [ContextWrapper] (common with themed contexts). Returns null when no
 * Activity is reachable.
 */
fun Context.unwrapActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}
