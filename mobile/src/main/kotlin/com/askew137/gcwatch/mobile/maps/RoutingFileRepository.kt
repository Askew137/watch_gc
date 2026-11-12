package com.askew137.gcwatch.mobile.maps

import android.content.Context
import android.net.Uri
import com.askew137.gcwatch.mobile.routing.GraphHopperEngine
import com.graphhopper.GraphHopper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File

/**
 * Repository for GraphHopper routing graph directories stored under `filesDir/routing/`.
 *
 * GraphHopper can either:
 *  - Load a pre-built graph from a directory (fastest – just copy the directory).
 *  - Import a raw `.osm.pbf` file and build the graph (slow first run, then cached).
 */
object RoutingFileRepository {

    /** Returns all first-level subdirectories in `filesDir/routing/`. */
    fun listRoutingGraphs(context: Context): List<File> =
        getGraphHopperImportDir(context)
            .listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * Parent directory for GraphHopper graph data.
     * Each imported OSM region gets its own sub-directory named after the `.osm.pbf` file.
     */
    fun getGraphHopperImportDir(context: Context): File =
        GraphHopperEngine.getGraphDirectory(context).also { it.mkdirs() }

    /**
     * Import a raw `.osm.pbf` file into a GraphHopper graph via [GraphHopper.importAndClose].
     *
     * The resulting graph is stored in a subdirectory named after the PBF file (without extension).
     * Emits [ImportProgress] updates as the import progresses.
     *
     * **Note:** This can take several minutes for large regions. Run in IO context (this Flow
     * already enforces [Dispatchers.IO]).
     */
    fun importOsmPbf(context: Context, pbfUri: Uri): Flow<ImportProgress> = flow {
        val fileName = resolveFileName(context, pbfUri)
            ?: "osm_${System.currentTimeMillis()}.osm.pbf"
        val baseName = fileName.removeSuffix(".osm.pbf").removeSuffix(".pbf")

        emit(ImportProgress(regionName = baseName, phase = ImportPhase.COPYING, progressPercent = 0))

        // Copy PBF to a local temp file so GraphHopper can open it by path.
        val tempDir = File(context.cacheDir, "osm_import").also { it.mkdirs() }
        val localPbf = File(tempDir, fileName)

        copyUriToFile(context, pbfUri, localPbf) { pct ->
            emit(ImportProgress(regionName = baseName, phase = ImportPhase.COPYING, progressPercent = pct))
        }

        emit(ImportProgress(regionName = baseName, phase = ImportPhase.IMPORTING, progressPercent = 0))

        val graphOutDir = File(getGraphHopperImportDir(context), baseName)
        graphOutDir.mkdirs()

        try {
            // Release any cached instance so it doesn't conflict.
            GraphHopperEngine.release()

            val gh = GraphHopper()
            gh.forMobile()
            gh.osmFile = localPbf.absolutePath
            gh.graphHopperLocation = graphOutDir.absolutePath
            gh.setProfiles(
                com.graphhopper.config.Profile("car").apply {
                    vehicle = "car"
                    weighting = "fastest"
                }
            )
            gh.encodingManager = com.graphhopper.routing.util.EncodingManager.create("car")
            gh.importAndClose()

            Timber.i("OSM import complete → ${graphOutDir.absolutePath}")
            emit(ImportProgress(regionName = baseName, phase = ImportPhase.DONE, progressPercent = 100))
        } catch (e: Exception) {
            Timber.e(e, "OSM import failed")
            graphOutDir.deleteRecursively()
            emit(ImportProgress(regionName = baseName, phase = ImportPhase.ERROR, progressPercent = 0, error = e.message))
        } finally {
            localPbf.delete()
        }
    }.flowOn(Dispatchers.IO)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun copyUriToFile(
        context: Context,
        uri: Uri,
        dest: File,
        onProgress: suspend (Int) -> Unit,
    ) {
        val totalBytes = context.contentResolver
            .openAssetFileDescriptor(uri, "r")
            ?.use { it.length } ?: -1L

        val inputStream = checkNotNull(context.contentResolver.openInputStream(uri)) {
            "Cannot open input stream for $uri"
        }

        val buffer = ByteArray(64 * 1024) // 64 KB chunks for PBF files
        var written = 0L

        inputStream.use { input ->
            dest.outputStream().use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    written += read
                    if (totalBytes > 0) {
                        onProgress(((written * 100) / totalBytes).toInt().coerceIn(0, 99))
                    }
                }
            }
        }
        onProgress(100)
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not resolve display name from URI")
            null
        } ?: uri.lastPathSegment
    }
}

enum class ImportPhase { COPYING, IMPORTING, DONE, ERROR }

data class ImportProgress(
    val regionName: String,
    val phase: ImportPhase,
    val progressPercent: Int,
    val error: String? = null,
)
