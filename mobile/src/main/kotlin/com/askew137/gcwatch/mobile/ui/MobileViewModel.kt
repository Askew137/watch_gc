package com.askew137.gcwatch.mobile.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.askew137.gcwatch.core.model.AppConfig
import com.askew137.gcwatch.mobile.maps.CopyProgress
import com.askew137.gcwatch.mobile.maps.MapFileRepository
import com.askew137.gcwatch.mobile.maps.MapTransferService
import com.askew137.gcwatch.mobile.update.UpdateChecker
import com.askew137.gcwatch.mobile.update.UpdateInfo
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File

/**
 * Shared [ViewModel] for the mobile companion app.
 *
 * All mutable state is exposed as [StateFlow] and never modified from the UI.
 */
class MobileViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<MobileApplication>()
    private val configRepo get() = app.configRepository

    // ── Config ────────────────────────────────────────────────────────────────

    val config: StateFlow<AppConfig> = configRepo.config

    fun updateConfig(newConfig: AppConfig) {
        configRepo.updateConfig(newConfig)
    }

    // ── Watch connectivity ────────────────────────────────────────────────────

    private val _connectedNodeName = MutableStateFlow<String?>(null)
    val connectedNodeName: StateFlow<String?> = _connectedNodeName.asStateFlow()

    // ── Map files ─────────────────────────────────────────────────────────────

    private val _mapFiles = MutableStateFlow<List<File>>(emptyList())
    val mapFiles: StateFlow<List<File>> = _mapFiles.asStateFlow()

    private val _copyProgress = MutableStateFlow<CopyProgress?>(null)
    val copyProgress: StateFlow<CopyProgress?> = _copyProgress.asStateFlow()

    // ── Update check ──────────────────────────────────────────────────────────

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateCheckInProgress = MutableStateFlow(false)
    val updateCheckInProgress: StateFlow<Boolean> = _updateCheckInProgress.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        refreshMapFiles()
        refreshConnectedNode()
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun refreshMapFiles() {
        _mapFiles.value = MapFileRepository.listLocalMapFiles(app)
    }

    fun copyMapFile(uri: Uri) {
        viewModelScope.launch {
            MapFileRepository.copyMapFileFromUri(app, uri).collectLatest { progress ->
                _copyProgress.value = progress
                if (progress.done) {
                    refreshMapFiles()
                    _copyProgress.value = null
                }
            }
        }
    }

    fun sendMapToWatch(mapFile: File) {
        MapTransferService.startTransfer(app, Uri.fromFile(mapFile))
    }

    fun checkForUpdate() {
        if (_updateCheckInProgress.value) return
        viewModelScope.launch {
            _updateCheckInProgress.value = true
            try {
                val versionName = app.packageManager
                    .getPackageInfo(app.packageName, 0)
                    .versionName ?: "1.0.0"
                _updateInfo.value = UpdateChecker.checkForUpdate(versionName)
            } finally {
                _updateCheckInProgress.value = false
            }
        }
    }

    fun clearUpdateInfo() {
        _updateInfo.value = null
    }

    fun openReleaseUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        app.startActivity(intent)
    }

    private fun refreshConnectedNode() {
        viewModelScope.launch {
            try {
                val nodes = Wearable.getNodeClient(app).connectedNodes.await()
                _connectedNodeName.value = nodes.firstOrNull()?.displayName
            } catch (e: Exception) {
                Timber.w(e, "Could not query connected nodes")
                _connectedNodeName.value = null
            }
        }
    }
}

/** Simple factory wiring [MobileViewModel] to the [Application]. */
class MobileViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MobileViewModel(application) as T
}
