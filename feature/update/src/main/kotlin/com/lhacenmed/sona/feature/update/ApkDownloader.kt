package com.lhacenmed.sona.feature.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A downloaded APK that is a newer build than the running one, ready to hand to the installer. */
data class StagedApk(val file: File, val versionCode: Int, val versionName: String)

/**
 * Stateless engine that streams one APK to the cache, emitting [UpdateState] as a cold [Flow] —
 * [UpdateService] collects it on a background scope so the download outlives the dialog.
 */
object ApkDownloader {

    /** Where the downloaded APK lands. Stable name so a re-download overwrites the last attempt. */
    fun apkFile(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }.resolve("sona-update.apk")

    /**
     * Inspects the staged APK on launch and decides its fate by comparing its packaged versionCode
     * against the running build: a newer, not-yet-installed APK is returned so the install prompt
     * can resume; an already-installed (or stale) one is deleted in place and null returned. Called
     * never mid-install — the system installer runs in its own process and finishes long before the
     * app is next started. Cheap no-op when nothing is staged.
     */
    fun stagedUpdate(context: Context): StagedApk? {
        val apk = apkFile(context)
        if (!apk.exists()) return null
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0)
        val code = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        if (code <= context.installedVersionCode()) { apk.delete(); return null }
        return StagedApk(apk, code.toInt(), info?.versionName.orEmpty())
    }

    /** Streams the APK at [apkUrl] into [apkFile], as the [UpdateState]s it passes through. */
    fun download(context: Context, apkUrl: String): Flow<UpdateState> = flow {
        val ctx = context.applicationContext
        val apk = apkFile(ctx)
        apk.delete()

        emit(UpdateState.Connecting)

        val speed = SpeedTracker()
        try {
            val conn = openWithRedirects(apkUrl)
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
            var received = 0L
            try {
                conn.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            yield()
                            out.write(buf, 0, n)
                            received += n
                            speed.add(n.toLong())
                            emit(UpdateState.Downloading(
                                progress = totalBytes?.let { (received.toFloat() / it).coerceIn(0f, 1f) },
                                log = formatDownloadLog(speed.bytesPerSec(), received, totalBytes),
                            ))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            apk.delete(); throw e
        } catch (e: Exception) {
            apk.delete()
            emit(UpdateState.Error("Download failed: ${e.message}")); return@flow
        }

        emit(UpdateState.Downloaded(apk))
    }.flowOn(Dispatchers.IO)

    /**
     * Opens a connection to [url], manually following cross-host redirects (Android's
     * HttpURLConnection only auto-follows same-host ones — GitHub release assets redirect to a CDN).
     */
    private fun openWithRedirects(url: String): HttpURLConnection {
        var location = url
        repeat(5) {
            val conn = (URL(location).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            conn.connect()
            if (conn.responseCode in 300..399) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next != null) { location = next; return@repeat }
            }
            return conn
        }
        error("Too many redirects for $url")
    }
}
