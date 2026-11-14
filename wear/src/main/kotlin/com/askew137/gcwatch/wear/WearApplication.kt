package com.askew137.gcwatch.wear

import android.app.Application
import com.askew137.gcwatch.wear.config.ConfigRepository
import com.askew137.gcwatch.wear.navigation.NavigationRepository
import timber.log.Timber

class WearApplication : Application() {

    /**
     * Process-wide singletons. The WearableListenerService (which receives messages from the
     * phone) and the UI ViewModels MUST share the same instances, otherwise incoming targets,
     * routes and config updates never reach the UI.
     */
    val navigationRepository: NavigationRepository by lazy { NavigationRepository(this) }
    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
