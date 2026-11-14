package com.askew137.gcwatch.wear.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.askew137.gcwatch.core.model.ComputedRoute
import com.askew137.gcwatch.core.model.Waypoint
import com.askew137.gcwatch.wear.WearApplication
import com.askew137.gcwatch.wear.gnss.WatchLocation
import com.askew137.gcwatch.wear.navigation.NavigationRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-scoped ViewModel that exposes [NavigationRepository] state to the UI.
 */
class NavigationViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: NavigationRepository = (app as WearApplication).navigationRepository

    val activeTarget: StateFlow<Waypoint?> = repo.activeTarget
    val currentRoute: StateFlow<ComputedRoute?> = repo.currentRoute

    fun requestRoute(from: WatchLocation, to: Waypoint) = repo.requestRoute(from, to)
    fun clearRoute() = repo.clearRoute()
    fun clearAll() = repo.clearAll()

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext as Application
            return NavigationViewModel(app) as T
        }
    }
}
