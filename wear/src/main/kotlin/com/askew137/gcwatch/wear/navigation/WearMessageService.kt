package com.askew137.gcwatch.wear.navigation

import com.askew137.gcwatch.core.communication.MessagePaths
import com.askew137.gcwatch.wear.config.ConfigRepository
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import timber.log.Timber
import java.io.File

/**
 * Wearable listener service that dispatches incoming messages to the appropriate repository.
 *
 * Message routing:
 * - [MessagePaths.SET_TARGET]   -> [NavigationRepository.onSetTarget]
 * - [MessagePaths.ROUTE_RESULT] -> [NavigationRepository.onRouteResult]
 * - [MessagePaths.CONFIG_SYNC]  -> [ConfigRepository.onRemoteSync]
 * - channel `/map_transfer/<name>.map` -> stored in `filesDir/maps/`
 */
class WearMessageService : WearableListenerService() {

    // Repositories are singletons; a real DI framework (Hilt) would inject these.
    private val navigationRepo: NavigationRepository by lazy {
        NavigationRepository(applicationContext)
    }
    private val configRepo: ConfigRepository by lazy {
        ConfigRepository(applicationContext)
    }

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("WearMessageService: received message on path=%s size=%d", event.path, event.data.size)
        when (event.path) {
            MessagePaths.SET_TARGET -> navigationRepo.onSetTarget(event.data)
            MessagePaths.ROUTE_RESULT -> navigationRepo.onRouteResult(event.data)
            MessagePaths.CONFIG_SYNC -> configRepo.onRemoteSync(event.data)
            else -> Timber.d("WearMessageService: unhandled path %s", event.path)
        }
    }

    /** Receives a Mapsforge `.map` file streamed from the phone's MapTransferService. */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val path = channel.path
        if (!path.startsWith(MAP_TRANSFER_PREFIX)) {
            Timber.d("WearMessageService: ignoring channel %s", path)
            return
        }

        // Keep only the file name; never trust the remote path for directory traversal.
        val fileName = File(path.removePrefix(MAP_TRANSFER_PREFIX)).name
        if (!fileName.endsWith(".map")) {
            Timber.w("WearMessageService: rejecting non-.map transfer %s", path)
            return
        }

        val mapsDir = File(filesDir, "maps").apply { mkdirs() }
        val tmp = File(mapsDir, "$fileName.part")
        val dest = File(mapsDir, fileName)
        val client = Wearable.getChannelClient(this)

        try {
            Timber.i("WearMessageService: receiving map %s", fileName)
            val input = Tasks.await(client.getInputStream(channel))
            var total = 0L
            input.use { src ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var n: Int
                    while (src.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        total += n
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (tmp.renameTo(dest)) {
                Timber.i("WearMessageService: map %s received (%d bytes)", fileName, total)
            } else {
                Timber.e("WearMessageService: could not move %s into place", fileName)
            }
        } catch (e: Exception) {
            Timber.e(e, "WearMessageService: map transfer failed for %s", fileName)
            tmp.delete()
        } finally {
            runCatching { Tasks.await(client.close(channel)) }
        }
    }

    private companion object {
        const val MAP_TRANSFER_PREFIX = "/map_transfer/"
    }
}
