package com.lhacenmed.sona.feature.update

import android.content.Context
import android.os.SystemClock
import com.lhacenmed.sona.feature.update.github.ReleaseApk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Stateless engine that streams one APK to the cache, emitting [UpdateState] as a cold [Flow] —
 * [UpdateService] collects it on a background scope so the download outlives the dialog.
 */
object ApkDownloader {

    /** How often the download says how far it has got. */
    private const val PROGRESS_INTERVAL_MS = 250L

    /** Where the downloaded APK lands. Stable name so a re-download overwrites the last attempt. */
    fun apkFile(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }.resolve("sona-update.apk")

    /**
     * The staged APK found again on launch, while [ApkVerifier] still finds it an update to install - so the
     * install prompt can resume. One already installed, or no longer installable, is deleted in place and null
     * returned. Called never mid-install — the system installer runs in its own process and finishes long
     * before the app is next started. Cheap no-op when nothing is staged.
     */
    fun stagedUpdate(context: Context): StagedApk? {
        val apk = apkFile(context)
        if (!apk.exists()) return null
        return ApkVerifier.inspect(context, apk) ?: run { apk.delete(); null }
    }

    /**
     * Streams [published] into [apkFile], as the [UpdateState]s it passes through - [UpdateState.Downloaded]
     * only for one that arrived whole, hashing to the published digest, and that [ApkVerifier] passes. It is
     * hashed as it streams, so checking it costs no second read of the file.
     */
    fun download(context: Context, published: ReleaseApk): Flow<UpdateState> = flow {
        val ctx = context.applicationContext
        val apk = apkFile(ctx)
        apk.delete()

        emit(UpdateState.Connecting)

        val speed = SpeedTracker()
        val digest = published.sha256?.let { MessageDigest.getInstance("SHA-256") }
        var received = 0L
        try {
            val conn = openWithRedirects(published.url)
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: published.sizeBytes
            // Said as soon as it is known how much is coming, then a few times a second: every chunk would
            // be hundreds of updates a second, faster than anyone can read them.
            emit(UpdateState.Downloading(received, totalBytes, bytesPerSecond = 0L))
            var lastEmittedAt = SystemClock.elapsedRealtime()
            try {
                conn.inputStream.use { input ->
                    apk.outputStream().use { out ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            yield()
                            out.write(buf, 0, n)
                            digest?.update(buf, 0, n)
                            received += n
                            speed.add(n.toLong())
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastEmittedAt >= PROGRESS_INTERVAL_MS) {
                                lastEmittedAt = now
                                emit(UpdateState.Downloading(received, totalBytes, speed.bytesPerSec()))
                            }
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

        val isIntact = received == published.sizeBytes &&
            (digest == null || digest.digest().toHex() == published.sha256)
        val staged = if (isIntact) ApkVerifier.inspect(ctx, apk) else null
        if (staged == null) {
            apk.delete()
            val reason = if (isIntact) R.string.update_download_invalid else R.string.update_download_damaged
            emit(UpdateState.Error(ctx.getString(reason))); return@flow
        }
        emit(UpdateState.Downloaded(staged))
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

/** Lowercase hex, as GitHub writes a digest. */
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
