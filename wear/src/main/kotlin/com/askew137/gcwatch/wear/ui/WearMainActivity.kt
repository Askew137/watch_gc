package com.askew137.gcwatch.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.askew137.gcwatch.wear.gnss.GnssRepository
import com.askew137.gcwatch.wear.map.AutoMapSelector
import com.askew137.gcwatch.wear.map.MapEngineService
import com.askew137.gcwatch.wear.ui.compass.CompassViewModel
import com.askew137.gcwatch.wear.ui.map.MapViewModel
import timber.log.Timber

class WearMainActivity : ComponentActivity() {

    // Single-instance engine/repo – in a production app these would be injected via Hilt
    private lateinit var mapEngine: MapEngineService
    private lateinit var gnssRepository: GnssRepository
    private lateinit var autoMapSelector: AutoMapSelector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mapEngine = MapEngineService(applicationContext)
        gnssRepository = GnssRepository(applicationContext)
        autoMapSelector = AutoMapSelector(applicationContext)

        Timber.d("WearMainActivity: onCreate")

        setContent {
            val configViewModel: ConfigViewModel = viewModel()
            val mapViewModel: MapViewModel = viewModel()
            val navigationViewModel: NavigationViewModel = viewModel(
                factory = NavigationViewModel.Factory(applicationContext)
            )
            val compassViewModel: CompassViewModel = viewModel()

            WearApp(
                configViewModel = configViewModel,
                mapViewModel = mapViewModel,
                navigationViewModel = navigationViewModel,
                compassViewModel = compassViewModel,
                gnssRepository = gnssRepository,
                mapEngine = mapEngine,
                autoMapSelector = autoMapSelector,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mapEngine.destroy()
        Timber.d("WearMainActivity: onDestroy")
    }
}
