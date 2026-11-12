package com.askew137.gcwatch.wear.navigation

import com.askew137.gcwatch.core.communication.MessagePaths
import com.askew137.gcwatch.wear.config.ConfigRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import timber.log.Timber

/**
 * Wearable listener service that dispatches incoming messages to the appropriate repository.
 *
 * Message routing:
 * - [MessagePaths.SET_TARGET]   -> [NavigationRepository.onSetTarget]
 * - [MessagePaths.ROUTE_RESULT] -> [NavigationRepository.onRouteResult]
 * - [MessagePaths.CONFIG_SYNC]  -> [ConfigRepository.onRemoteSync]
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
}
