package com.askew137.gcwatch.wear.gnss

import android.content.Context
import com.askew137.gcwatch.core.communication.MessagePaths
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber

/**
 * Protobuf DTO for phone location updates arriving on [MessagePaths.PHONE_LOCATION].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PhoneLocation(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accuracy: Float = Float.NaN,
    val bearingDeg: Float = Float.NaN,
    val speedMps: Float = Float.NaN,
)

/**
 * Listens for phone-side location updates relayed over the Wearable [MessageClient].
 * Emits [WatchLocation] via [callbackFlow].
 */
@OptIn(ExperimentalSerializationApi::class)
class PhoneGnssReceiver(context: Context) {

    private val messageClient: MessageClient = Wearable.getMessageClient(context)

    fun locationFlow(): Flow<WatchLocation> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
            if (event.path == MessagePaths.PHONE_LOCATION) {
                try {
                    val phoneLocation = ProtoBuf.decodeFromByteArray<PhoneLocation>(event.data)
                    val watchLoc = WatchLocation(
                        lat = phoneLocation.lat,
                        lon = phoneLocation.lon,
                        bearingDeg = phoneLocation.bearingDeg,
                        accuracyM = phoneLocation.accuracy,
                        speedMps = phoneLocation.speedMps,
                    )
                    trySend(watchLoc).isSuccess
                } catch (e: Exception) {
                    Timber.e(e, "PhoneGnssReceiver: failed to decode PhoneLocation")
                }
            }
        }

        messageClient.addListener(listener)
        Timber.d("PhoneGnssReceiver: registered listener")

        awaitClose {
            messageClient.removeListener(listener)
            Timber.d("PhoneGnssReceiver: unregistered listener")
        }
    }
}
