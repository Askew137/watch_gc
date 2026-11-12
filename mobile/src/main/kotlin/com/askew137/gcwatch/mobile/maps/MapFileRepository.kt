package com.askew137.gcwatch.mobile.maps

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File

/**
 * Repository for Mapsforge `.map` files stored in `filesDir/maps/`.
 */
object MapFileRepository {

    private fun mapsDir(context: Context): File =
        File(context.filesDir, "maps").also { it.mkdirs() }

    /** Lists all `.map` files in the local maps directory. */
    fun listLocalMapFiles(context: Context): List<File> =
        mapsDir(context).listFiles { f -> f.extension == "map" }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * Copies a `.map` file from a SAF [Uri] into `filesDir/maps/`.
     * Emits [CopyProgress] updates during the copy.
     */
    fun copyMapFileFromUri(context: Context, uri: Uri): Flow<CopyProgress> = flow {
        val fileName = resolveFileName(context, uri) ?: "map_${System.currentTimeMillis()}.map"
        val dest = File(mapsDir(context), fileName)

        emit(CopyProgress(fileName = fileName, bytesWritten = 0, totalBytes = -1, done = false))

        val totalBytes = context.contentResolver
            .openAssetFileDescriptor(uri, "r")
            ?.use { it.length } ?: -1L

        val inputStream = checkNotNull(context.contentResolver.openInputStream(uri)) {
            "Cannot open input stream for $uri"
        }

        val buffer = ByteArray(8 * 1024)
        var written = 0L

        inputStream.use { input ->
            dest.outputStream().use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    written += read
                    emit(CopyProgress(fileName = fileName, bytesWritten = written, totalBytes = totalBytes, done = false))
                }
            }
        }

        Timber.i("Copied map file $fileName ($written bytes)")
        emit(CopyProgress(fileName = fileName, bytesWritten = written, totalBytes = written, done = true))
    }.flowOn(Dispatchers.IO)

    private fun resolveFileName(context: Context, uri: Uri): String? {
        // Try content resolver display name.
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

data class CopyProgress(
    val fileName: String,
    val bytesWritten: Long,
    val totalBytes: Long,
    val done: Boolean,
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
}
