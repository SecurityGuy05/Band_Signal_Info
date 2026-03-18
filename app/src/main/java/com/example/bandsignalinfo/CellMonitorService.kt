package com.example.bandsignalinfo

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class CellMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        createNotificationChannel()
        val initial = buildNotification("Starting...", "")

        var started = false
        if (hasLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                started = true
            } catch (_: SecurityException) {
                // Started from background (e.g. boot) — location FGS type is blocked,
                // fall through to plain foreground service.
            }
        }
        if (!started) {
            if (!hasLocation) {
                // No location permission at all — nothing to do.
                stopSelf()
                return
            }
            // On API 34+, the 2-param startForeground() inherits the manifest's
            // foregroundServiceType="location" and would throw the same SecurityException.
            // Explicitly pass FOREGROUND_SERVICE_TYPE_NONE to override the manifest type.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
            } else {
                startForeground(NOTIFICATION_ID, initial)
            }
        }

        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                updateNotification()
                delay(3000)
            }
        }
    }

    private fun updateNotification() {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val carrier = tm.networkOperatorName.takeIf { it.isNotBlank() } ?: "Unknown"
            val cells = tm.allCellInfo
                ?.mapNotNull { parseCellInfo(it, carrier) }
                ?: emptyList()

            val nrCell = cells.firstOrNull { it.type == "NR (5G)" }
            val lteCell = cells.firstOrNull { it.isServing && it.type == "LTE" }

            val title = buildString {
                nrCell?.let { append("NR ${it.band}") }
                lteCell?.let {
                    if (isNotEmpty()) append("  +  ")
                    append("LTE ${it.band}")
                }
                if (isEmpty()) append("No cell info")
            }

            val text = buildString {
                nrCell?.let { append("NR ${it.rsrp}") }
                lteCell?.let {
                    if (isNotEmpty()) append("   ")
                    append("LTE ${it.rsrp}")
                }
            }

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification("$carrier  ·  $title", text))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cell Signal Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Current band and signal strength"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "cell_monitor"
        const val NOTIFICATION_ID = 1
    }
}
