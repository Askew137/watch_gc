package com.askew137.gcwatch.mobile.ui

import android.app.Application
import com.askew137.gcwatch.mobile.config.MobileConfigRepository
import timber.log.Timber

/** Application class – plants Timber tree and initialises global singletons. */
class MobileApplication : Application() {

    /** Global config repository; held here so it lives as long as the process. */
    lateinit var configRepository: MobileConfigRepository
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        configRepository = MobileConfigRepository(this)
        Timber.d("MobileApplication started")
    }

    override fun onTerminate() {
        super.onTerminate()
        configRepository.release()
    }
}
