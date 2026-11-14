package com.askew137.gcwatch.mobile.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer version of GC Watch.
 *
 * This is a v1 implementation: it only reads the latest release and returns
 * [UpdateInfo] when the remote tag is newer than [currentVersion]. The UI
 * will display a notification/snackbar prompting the user to visit the
 * release page manually – no auto-install in v1.
 */
object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val RELEASES_URL =
        "https://api.github.com/repos/Askew137/gc_watch/releases/latest"

    /**
     * Makes an HTTP GET to the GitHub Releases API.
     *
     * @param currentVersion Semantic version string of the installed app, e.g. "1.0.0".
     * @return [UpdateInfo] if a newer release exists, or `null` if up to date / network error.
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("GitHub API returned HTTP ${response.code}")
                    return null
                }
                response.body?.string() ?: return null
            }

            val json = JSONObject(body)
            val tagName    = json.getString("tag_name").trimStart('v')
            val releaseUrl = json.getString("html_url")

            Timber.d("Latest GitHub release: $tagName (installed: $currentVersion)")

            if (isNewerVersion(remote = tagName, current = currentVersion)) {
                UpdateInfo(version = tagName, releaseUrl = releaseUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            // Silent fail – update check is non-critical.
            Timber.w(e, "Update check failed (silent)")
            null
        }
    }

    /**
     * Compares two semver strings (`MAJOR.MINOR.PATCH`).
     * Returns `true` if [remote] is strictly greater than [current].
     */
    internal fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts  = remote.split(".").mapNotNull { it.trim().toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.trim().toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false // equal
    }
}

data class UpdateInfo(val version: String, val releaseUrl: String)
