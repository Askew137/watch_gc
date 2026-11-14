package com.askew137.gcwatch.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.wear.WearApplication
import com.askew137.gcwatch.wear.config.ConfigRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-scoped ViewModel that exposes [ConfigRepository] state to the UI.
 */
class ConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: ConfigRepository = (app as WearApplication).configRepository

    val config: StateFlow<AppConfig> = repo.config

    fun updateConfig(newConfig: AppConfig) = repo.updateConfig(newConfig)
}
