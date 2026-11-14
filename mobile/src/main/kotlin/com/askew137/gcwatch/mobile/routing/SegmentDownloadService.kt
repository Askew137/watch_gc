package com.askew137.gcwatch.mobile.routing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads one routing data tile (`E10_N50.rd5`, ~100 MB) from brouter.de as a foreground
 * service, writing to `<name>.rd5.part` and renaming when complete.
 */
class SegmentDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Navigation data", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tile = intent?.getStringExtra(EXTRA_TILE)
        if (tile == null || !SegmentRepository.isValidTile(tile) || SegmentRepository.download.value != null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        SegmentRepository.setDownloadError(null)
        SegmentRepository.setDownload(SegmentRepository.DownloadState(tile, 0, -1))
        startForeground(NOTIF_ID, notification(tile, -1))
        scope.launch { download(tile, startId) }
        return START_NOT_STICKY
    }

    private fun download(tile: String, startId: Int) {
        val target = SegmentRepository.file(this, tile)
        val part = File(target.parentFile, "${target.name}.part")
        try {
            val client = OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(SegmentRepository.url(tile)).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty response")
                val total = body.contentLength()
                if (total > 0 && target.parentFile!!.usableSpace < total + FREE_SPACE_MARGIN) {
                    error("Not enough free space (${total / 1_000_000} MB needed)")
                }

                var written = 0L
                var lastUiUpdate = 0L
                var lastPercent = -2
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 250) {
                                lastUiUpdate = now
                                SegmentRepository.setDownload(SegmentRepository.DownloadState(tile, written, total))
                                val percent = if (total > 0) (written * 100 / total).toInt() else -1
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    notify(notification(tile, percent))
                                }
                            }
                        }
                    }
                }
                if (total > 0 && written != total) error("Incomplete download ($written of $total bytes)")
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) error("Could not store the file")
                Timber.i("Routing data %s downloaded (%d bytes)", tile, written)
            }
        } catch (e: Exception) {
            Timber.e(e, "Download of %s failed", tile)
            part.delete()
            SegmentRepository.setDownloadError("$tile: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            SegmentRepository.setDownload(null)
            SegmentRepository.clearMissingIfInstalled(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun notify(n: android.app.Notification) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun notification(tile: String, percent: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading navigation data")
            .setContentText(tile)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "segment_download"
        private const val NOTIF_ID = 1002
        private const val EXTRA_TILE = "tile"
        private const val FREE_SPACE_MARGIN = 100L * 1024 * 1024

        fun start(context: Context, tile: String) {
            context.startForegroundService(
                Intent(context, SegmentDownloadService::class.java).putExtra(EXTRA_TILE, tile)
            )
        }
    }
}
