package com.askew137.gcwatch.mobile.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val HOME     = "home"
    const val MAPS     = "maps"
    const val ROUTING  = "routing"
    const val SETTINGS = "settings"
}

/**
 * Root navigation graph for the GC Watch mobile companion.
 *
 * Destinations:
 *  - `home`     → [HomeScreen]
 *  - `maps`     → [MapsScreen] (map file management + watch transfer)
 *  - `routing`  → routing graph section inside [MapsScreen] (navigated as a sub-route)
 *  - `settings` → [SettingsScreen]
 */
@Composable
fun MobileNavHost(viewModel: MobileViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController   = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel    = viewModel,
                onNavigateMaps     = { navController.navigate(Routes.MAPS) },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.MAPS) {
            MapsScreen(
                viewModel = viewModel,
                onNavigateUp = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel    = viewModel,
                onNavigateUp = { navController.popBackStack() },
            )
        }
    }
}
