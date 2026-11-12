package com.askew137.gcwatch.wear.map

import android.content.Context
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.map.reader.MapFile
import timber.log.Timber
import java.io.File

/**
 * Scans [Context.filesDir]/maps/ for *.map files and returns those whose bounding box
 * contains the given position, or all files when [automatic] is false.
 */
class AutoMapSelector(private val context: Context) {

    private val mapsDir: File get() = File(context.filesDir, "maps")

    /** All .map files found in the maps directory. */
    fun availableMapFiles(): List<File> {
        if (!mapsDir.exists()) return emptyList()
        return mapsDir.listFiles { f -> f.extension == "map" }?.toList() ?: emptyList()
    }

    /**
     * Returns the subset of [availableMapFiles] whose Mapsforge bounding box contains
     * [lat]/[lon]. If [automatic] is false, returns all available files unchanged.
     */
    fun selectMapsForPosition(lat: Double, lon: Double, automatic: Boolean): List<File> {
        val available = availableMapFiles()
        if (!automatic) return available

        return available.filter { file ->
            try {
                val mapFile = MapFile(file)
                val bbox: BoundingBox = mapFile.mapFileInfo.boundingBox
                mapFile.close()
                bbox.contains(lat, lon)
            } catch (e: Exception) {
                Timber.e(e, "AutoMapSelector: failed to read bounding box from %s", file.name)
                false
            }
        }.also { selected ->
            Timber.d("AutoMapSelector: %d/%d maps contain (%.4f, %.4f)", selected.size, available.size, lat, lon)
        }
    }
}
