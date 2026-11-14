package com.askew137.gcwatch.mobile.routing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

/** Routing data tiles (`*.rd5`, 5x5 degrees) that BRouter reads from [BRouterEngine.segmentsDir]. */
object SegmentRepository {

    /** [name] is e.g. `E10_N50` (south-west corner), [area] a human hint for the Czech Republic. */
    data class Tile(val name: String, val area: String)

    /** The tiles covering the Czech Republic (plus neighbours). Others can be fetched on demand. */
    val knownTiles = listOf(
        Tile("E10_N50", "Praha, Ústí n. L., Karlovy Vary"),
        Tile("E15_N50", "Liberec, Hradec Králové, Pardubice"),
        Tile("E10_N45", "Plzeň, České Budějovice, Bavorsko"),
        Tile("E15_N45", "Brno, Olomouc, Ostrava, Zlín"),
    )

    data class DownloadState(val tile: String, val bytes: Long, val total: Long)

    private val _download = MutableStateFlow<DownloadState?>(null)
    /** Non-null while a tile is being downloaded. */
    val download: StateFlow<DownloadState?> = _download.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private val _missingTile = MutableStateFlow<String?>(null)
    /**
     * Tile the engine asked for last but that is not installed (e.g. `E10_N45`). Persisted: the
     * request usually arrives while the UI is not running, and the process may be gone by the
     * time the user opens the app.
     */
    val missingTile: StateFlow<String?> = _missingTile.asStateFlow()

    private const val PREFS = "routing_prefs"
    private const val KEY_MISSING = "missing_tile"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call once from Application.onCreate. */
    fun init(context: Context) {
        _missingTile.value = prefs(context).getString(KEY_MISSING, null)
            ?.takeIf { isValidTile(it) && !isInstalled(context, it) }
    }

    private val _remoteSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val remoteSizes: StateFlow<Map<String, Long>> = _remoteSizes.asStateFlow()

    private val tileRegex = Regex("""^[EW]\d{1,3}_[NS]\d{1,2}$""")

    /** The tile name comes from engine output and ends up in a URL and a file name: validate. */
    fun isValidTile(name: String) = tileRegex.matches(name)

    fun url(tile: String) = "https://brouter.de/brouter/segments4/$tile.rd5"

    fun file(context: Context, tile: String) = File(BRouterEngine.segmentsDir(context), "$tile.rd5")

    fun isInstalled(context: Context, tile: String) = file(context, tile).isFile

    fun installedFiles(context: Context): List<File> =
        BRouterEngine.segmentsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".rd5") }
            ?.sortedBy { it.name }.orEmpty()

    fun delete(context: Context, tile: String) {
        if (isValidTile(tile)) file(context, tile).delete()
    }

    fun reportMissing(context: Context, tile: String?) {
        val valid = tile?.takeIf { isValidTile(it) }
        _missingTile.value = valid
        prefs(context).edit().putString(KEY_MISSING, valid).apply()
    }

    fun clearMissingIfInstalled(context: Context) {
        _missingTile.value?.let { if (isInstalled(context, it)) reportMissing(context, null) }
    }

    internal fun setDownload(state: DownloadState?) {
        _download.value = state
    }

    internal fun setDownloadError(message: String?) {
        _downloadError.value = message
    }

    /** HEAD requests for the sizes shown next to the download buttons. Blocking. */
    fun fetchRemoteSizes(tiles: Collection<String>) {
        val client = OkHttpClient()
        val sizes = _remoteSizes.value.toMutableMap()
        for (tile in tiles) {
            if (!isValidTile(tile) || tile in sizes) continue
            try {
                client.newCall(Request.Builder().url(url(tile)).head().build()).execute().use { r ->
                    r.header("Content-Length")?.toLongOrNull()?.let { sizes[tile] = it }
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not get size of %s", tile)
            }
        }
        _remoteSizes.value = sizes
    }
}
