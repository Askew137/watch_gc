package com.askew137.gcwatch.mobile.maps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.InputStream

/**
 * Foreground [Service] that transfers a `.map` file to the connected Wear OS watch
 * via [ChannelClient].
 *
 * Start via [MapTransferService.startTransfer].
 */
class MapTransferService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uriStr = intent?.getStringExtra(EXTRA_MAP_URI)
        if (uriStr == null) {
            Timber.e("MapTransferService started without URI")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val mapUri = Uri.parse(uriStr)
        val fileName = mapUri.lastPathSegment ?: "map.map"

        startForeground(
            NOTIF_ID,
            buildNotification(fileName, progress = 0, done = false, error = false),
        )

        scope.launch {
            transferMap(mapUri, fileName, startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Transfer logic ────────────────────────────────────────────────────────

    private suspend fun transferMap(mapUri: Uri, fileName: String, startId: Int) {
        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        try {
            // Determine file size for progress calculation.
            val totalBytes = contentResolver.openAssetFileDescriptor(mapUri, "r")
                ?.use { it.length } ?: -1L

            // Find the first connected node.
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            if (nodes.isEmpty()) {
                Timber.w("No connected nodes – cannot transfer map")
                notifManager.notify(NOTIF_ID, buildNotification(fileName, 0, done = false, error = true))
                stopSelf(startId)
                return
            }

            val node = nodes.first()
            val channelPath = "/map_transfer/$fileName"
            Timber.i("Opening channel $channelPath to ${node.displayName}")

            val channelClient = Wearable.getChannelClient(this)
            val channel: ChannelClient.Channel =
                channelClient.openChannel(node.id, channelPath).await()

            try {
                val outputStream = channelClient.getOutputStream(channel).await()
                val inputStream: InputStream = checkNotNull(contentResolver.openInputStream(mapUri)) {
                    "Cannot open input stream for $mapUri"
                }

                val buffer = ByteArray(CHUNK_SIZE)
                var bytesSent = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesSent += read

                            val progress = if (totalBytes > 0) {
                                ((bytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1 // indeterminate
                            }
                            notifManager.notify(
                                NOTIF_ID,
                                buildNotification(fileName, progress, done = false, error = false),
                            )
                        }
                    }
                }

                Timber.i("Map transfer complete: $bytesSent bytes")

                // Notify watch that transfer is done.
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, PATH_TRANSFER_DONE, fileName.toByteArray())
                    .await()

                notifManager.notify(NOTIF_ID, buildNotification(fileName, 100, done = true, error = false))
            } finally {
                channelClient.close(channel).await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Map transfer failed")
            notifManager.notify(NOTIF_ID, buildNotification(fileName, 0, done = false, error = true))
        } finally {
            stopSelf(startId)
        }
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Map Transfer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress while sending map files to the watch"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(
        fileName: String,
        progress: Int,
        done: Boolean,
        error: Boolean,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(
            when {
                error -> "Map transfer failed"
                done  -> "Map transfer complete"
                else  -> "Sending map to watch"
            }
        )
        .setContentText(fileName)
        .setProgress(
            /* max= */ 100,
            /* progress= */ progress,
            /* indeterminate= */ progress < 0,
        )
        .setOngoing(!done && !error)
        .setAutoCancel(done || error)
        .build()

    companion object {
        private const val CHANNEL_ID       = "map_transfer"
        private const val NOTIF_ID         = 1001
        private const val EXTRA_MAP_URI    = "extra_map_uri"
        private const val CHUNK_SIZE       = 8 * 1024 // 8 KB
        private const val PATH_TRANSFER_DONE = "/map_transfer_done"

        /** Convenience launcher. */
        fun startTransfer(context: Context, mapFile: Uri) {
            val intent = Intent(context, MapTransferService::class.java).apply {
                putExtra(EXTRA_MAP_URI, mapFile.toString())
            }
            context.startForegroundService(intent)
        }
    }
}
