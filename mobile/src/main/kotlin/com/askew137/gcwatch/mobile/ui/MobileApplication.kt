package com.askew137.gcwatch.mobile.ui

import android.app.Application
import com.askew137.gcwatch.mobile.config.MobileConfigRepository
import com.askew137.gcwatch.mobile.BuildConfig
import timber.log.Timber

/** Application class – plants Timber tree and initialises global singletons. */
class MobileApplication : Application() {

    /** Global config repository; held here so it lives as long as the process. */
    lateinit var configRepository: MobileConfigRepository
        private set

    /**
     * Outlives the short-lived WearableListenerService, which is destroyed right after a
     * message callback returns; a route computation must not be cancelled with it.
     */
    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        configRepository = MobileConfigRepository(this)
        com.askew137.gcwatch.mobile.routing.SegmentRepository.init(this)
        Timber.d("MobileApplication started")
    }

    override fun onTerminate() {
        super.onTerminate()
        configRepository.release()
    }
}
