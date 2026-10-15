package io.github.audxius.pillar.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.audxius.pillar.MainActivity
import io.github.audxius.pillar.PillarApp
import io.github.audxius.pillar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps RomM downloads alive while the user leaves Pillar, e.g. to play something else. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var active = false
    private var lastStartId = 0

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "RomM game downloads"
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build("Preparing download…", null),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        val startWorker = synchronized(lock) {
            lastStartId = startId
            (!active).also { active = true }
        }
        if (startWorker) {
            val downloads = (application as PillarApp).graph.downloads
            scope.launch {
                val progress = launch {
                    downloads.states.collect { states ->
                        val running = states.values.filterIsInstance<DownloadState.Running>().firstOrNull()
                        val queued = states.values.count { it is DownloadState.Queued }
                        if (running != null) {
                            val more = if (queued > 0) " · $queued queued" else ""
                            getSystemService(NotificationManager::class.java)
                                .notify(NOTIFICATION_ID, build(running.rom.title + more, running.fraction))
                        }
                        delay(700)
                    }
                }
                var stopId = 0
                while (true) {
                    val next = synchronized(lock) {
                        downloads.nextQueued().also { if (it == null) { active = false; stopId = lastStartId } }
                    } ?: break
                    downloads.run(next)
                }
                progress.cancel()
                // Only stops if no new download request arrived after the queue drained.
                stopSelfResult(stopId)
            }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, fraction: Float?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Downloading from RomM")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .apply {
                if (fraction == null) setProgress(0, 0, true)
                else setProgress(1000, (fraction * 1000).toInt(), false)
            }
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 42
    }
}
