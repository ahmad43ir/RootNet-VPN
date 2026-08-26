package com.chobgroup.rootnet.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.core.app.NotificationCompat
import com.chobgroup.rootnet.MainActivity
import com.chobgroup.rootnet.R
import com.chobgroup.rootnet.config.ConfigNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libXray.LibXray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RootNet v3 VPN engine — a foreground [VpnService] that hands the TUN file
 * descriptor to the embedded Xray-core (LibXray) which runs its own Android
 * TUN inbound. Traffic accounting drives the ad-funded quota: when the quota
 * runs out the engine is hard-stopped.
 */
class VpnEngineService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunPfd: ParcelFileDescriptor? = null
    private val busy = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        // Xray outbound sockets (proxy link + DoH) must bypass our own TUN.
        runCatching { LibXray.registerDialerController(Protector()) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopEngine()
                return START_NOT_STICKY
            }
            else -> {
                val raw = intent?.getStringExtra(EXTRA_CONFIG)
                val format = intent?.getStringExtra(EXTRA_FORMAT) ?: "link"
                val protocol = intent?.getStringExtra(EXTRA_PROTOCOL) ?: "vless"
                if (raw.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startEngine(raw, format, protocol)
            }
        }
        return START_STICKY
    }

    private fun startEngine(raw: String, format: String, protocol: String) {
        if (!busy.compareAndSet(false, true)) return
        EngineState.set(EngineState.ConnState.CONNECTING)

        if (!TimeQuotaManager.hasTime(this)) {
            fail("No time left — watch a video for 30 more minutes")
            return
        }

        val unified = runCatching {
            ConfigNormalizer.normalize(raw = raw, configFormat = format, protocol = protocol)
        }.getOrElse {
            fail(it.message ?: "Invalid config")
            return
        }

        // ── Bring up the TUN device ──
        val pfd = establishTun() ?: run {
            fail("VPN permission denied")
            return
        }
        tunPfd = pfd
        val fd = pfd.detachFd()
        runCatching { Os.setenv("xray.tun.fd", fd.toString(), true) }

        startForeground(NOTIFICATION_ID, notification())

        val json = runCatching { XrayConfigBuilder.build(unified) }.getOrElse {
            fail(it.message ?: "Config error")
            return
        }

        scope.launch {
            val response = runCatching { invoke("runXray", json) }
                .getOrElse { """{"success":false,"error":"${it.message}"}""" }
            if (!response.contains("\"success\":true")) {
                fail(response.take(180))
                return@launch
            }
            EngineState.set(EngineState.ConnState.CONNECTED)
            updateNotification()

            // Metering loop — the clock only runs while connected; hard-stop at zero.
            var heartbeat = 0
            while (isActive && EngineState.state.value == EngineState.ConnState.CONNECTED) {
                delay(1_000)
                if (++heartbeat % 60 == 0) {
                    TimeQuotaManager.syncWithServer(this@VpnEngineService, watchAd = false)
                }
                if (!TimeQuotaManager.tick(this@VpnEngineService)) {
                    EngineState.set(EngineState.ConnState.QUOTA_EXHAUSTED, "Time is up")
                    stopEngine()
                    return@launch
                }
                updateNotification()
            }
        }
    }

    private fun invoke(method: String, xrayJson: String): String =
        LibXray.invoke(
            org.json.JSONObject()
                .put("apiVersion", 2)
                .put("method", method)
                .put("payload", org.json.JSONObject().put("xrayJson", xrayJson))
                .toString(),
        )

    private fun establishTun(): ParcelFileDescriptor? =
        Builder()
            .setSession("RootNet")
            .setMtu(8500)
            .addAddress("172.19.0.1", 32)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setBlocking(false)
            .establish()

    /** Xray dials (proxy link + DoH) must bypass our own TUN. */
    private inner class Protector : libXray.DialerController {
        override fun protectFd(fd: Long): Boolean = this@VpnEngineService.protect(fd.toInt())
    }

    private fun fail(message: String) {
        busy.set(false)
        EngineState.set(EngineState.ConnState.ERROR, message)
        cleanup()
        @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun stopEngine() {
        busy.set(false)
        scope.launch {
            runCatching { LibXray.invoke("""{"apiVersion":2,"method":"stopXray"}""") }
            tunPfd?.close()
            tunPfd = null
            EngineState.set(EngineState.ConnState.DISCONNECTED)
            @Suppress("DEPRECATION") stopForeground(true)
            stopSelf()
        }
    }

    private fun cleanup() {
        runCatching { LibXray.invoke("""{"apiVersion":2,"method":"stopXray"}""") }
        tunPfd?.close()
        tunPfd = null
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        busy.set(false)
        EngineState.set(EngineState.ConnState.DISCONNECTED, "VPN revoked")
        cleanup()
        super.onRevoke()
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "RootNet VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remainingMin = TimeQuotaManager.remainingSeconds(this) / 60
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("RootNet connected")
            .setContentText("$remainingMin min remaining")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification())
    }

    companion object {
        const val ACTION_CONNECT = "com.chobgroup.rootnet.CONNECT"
        const val ACTION_DISCONNECT = "com.chobgroup.rootnet.DISCONNECT"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_PROTOCOL = "protocol"
        private const val CHANNEL_ID = "rootnet_vpn"
        private const val NOTIFICATION_ID = 41

        fun connect(context: android.content.Context, raw: String, format: String, protocol: String) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(
                    Intent(context, VpnEngineService::class.java)
                        .setAction(ACTION_CONNECT)
                        .putExtra(EXTRA_CONFIG, raw)
                        .putExtra(EXTRA_FORMAT, format)
                        .putExtra(EXTRA_PROTOCOL, protocol),
                )
            } else {
                context.startService(
                    Intent(context, VpnEngineService::class.java)
                        .setAction(ACTION_CONNECT)
                        .putExtra(EXTRA_CONFIG, raw)
                        .putExtra(EXTRA_FORMAT, format)
                        .putExtra(EXTRA_PROTOCOL, protocol),
                )
            }
        }

        fun disconnect(context: android.content.Context) {
            context.startService(
                Intent(context, VpnEngineService::class.java).setAction(ACTION_DISCONNECT),
            )
        }

        /** Connect using the fastest pinged server from the cache. */
        fun connectLastOrFastest(context: android.content.Context) {
            val cached = com.chobgroup.rootnet.data.repository.ServerCacheStore.instance.cachedServers()
            val usable = cached.filter { it.pingMs == null || it.pingMs >= 0 }
            val best = usable.minByOrNull { it.pingMs ?: Int.MAX_VALUE } ?: usable.firstOrNull() ?: return
            connect(context, best.rawConfig, best.configFormat.name.lowercase(), best.type.wireName)
        }
    }
}
