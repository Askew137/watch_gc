package com.askew137.gcwatch.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.askew137.gcwatch.mobile.ui.theme.MobileTheme

/**
 * Single-Activity entry point for the GC Watch phone companion app.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MobileViewModel = viewModel(
                factory = MobileViewModelFactory(application)
            )
            val config by vm.config.collectAsState()
            MobileTheme(config = config) {
                MobileNavHost(viewModel = vm)
            }
        }
    }
}
