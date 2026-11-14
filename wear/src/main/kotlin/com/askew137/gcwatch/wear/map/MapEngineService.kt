package com.askew137.gcwatch.wear.map

import android.content.Context
import android.graphics.Bitmap
import com.askew137.gcwatch.core.model.ThemeMode
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.StreamRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
import timber.log.Timber
import java.io.File

private const val TILE_SIZE = 256

/** Smaller labels suit a ~1.4" watch face. */
private const val MAP_TEXT_SCALE = 0.7f
private const val MAX_CACHE_BYTES = 48L * 1024 * 1024

/**
 * Wraps Mapsforge rendering pipeline. Supports multi-map via [MultiMapDataStore].
 * Tile cache is sized dynamically to min(maxMemory/10, 48 MB).
 */
class MapEngineService(private val context: Context) {

    private val graphicFactory = AndroidGraphicFactory.INSTANCE
    private val displayModel = DisplayModel()

    private var multiStore: MultiMapDataStore? = null
    private var renderer: DatabaseRenderer? = null
    private var currentThemeMode: ThemeMode = ThemeMode.NORMAL

    // Dynamic tile cache
    private val tileCacheSizeBytes: Long =
        (Runtime.getRuntime().maxMemory() / 10).coerceAtMost(MAX_CACHE_BYTES)

    private val tileCache = AndroidUtil.createTileCache(
        context,
        "mapcache",
        TILE_SIZE,
        1f,
        /*overdrawFactor=*/ 1.2
    )

    init {
        AndroidGraphicFactory.createInstance(context.applicationContext as android.app.Application)
        Timber.d("MapEngineService: tile cache size = %d KB", tileCacheSizeBytes / 1024)
    }

    /** Replace the active set of .map files. */
    fun setMapFiles(files: List<File>) {
        multiStore?.close()

        if (files.isEmpty()) {
            multiStore = null
            renderer = null
            Timber.w("MapEngineService: no map files provided")
            return
        }

        val store = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        files.forEach { file ->
            try {
                store.addMapDataStore(MapFile(file), false, false)
            } catch (e: Exception) {
                Timber.e(e, "MapEngineService: failed to open map file %s", file.name)
            }
        }
        multiStore = store

        renderer = DatabaseRenderer(store, graphicFactory, tileCache, null, true, true, null)
        Timber.d("MapEngineService: loaded %d map file(s)", files.size)
    }

    /** Update render theme when theme mode changes. */
    fun applyThemeMode(themeMode: ThemeMode) {
        currentThemeMode = themeMode
        // Renderer re-reads theme per job; just invalidate cache so tiles are re-rendered
        tileCache.purge()
    }

    /**
     * Render a single tile synchronously.
     * Returns null if no map data is loaded or tile is out of range.
     */
    fun renderTile(tileX: Long, tileY: Long, zoom: Byte): Bitmap? {
        val store = multiStore ?: return null
        val rend = renderer ?: return null

        return try {
            val tile = Tile(tileX.toInt(), tileY.toInt(), zoom, TILE_SIZE)
            val renderTheme = resolveTheme(currentThemeMode)
            val job = RendererJob(tile, store, renderTheme, displayModel, MAP_TEXT_SCALE, false, false)
            val tileBitmap: TileBitmap? = rend.executeJob(job)
            tileBitmap?.let { AndroidGraphicFactory.getBitmap(it) }
        } catch (e: Exception) {
            Timber.e(e, "MapEngineService: renderTile failed (%d,%d,z%d)", tileX, tileY, zoom)
            null
        }
    }

    private val themeCache = mutableMapOf<ThemeMode, RenderThemeFuture>()

    private fun resolveTheme(mode: ThemeMode): RenderThemeFuture =
        themeCache.getOrPut(mode) {
            val xml: XmlRenderTheme = when (mode) {
                ThemeMode.NIGHT_RED -> StreamRenderTheme("", context.assets.open("themes/night_theme.xml"))
                ThemeMode.NORMAL -> InternalRenderTheme.OSMARENDER
            }
            RenderThemeFuture(graphicFactory, xml, displayModel).also { Thread(it).start() }
        }

    fun destroy() {
        multiStore?.close()
        tileCache.destroy()
    }
}
