package com.lhacenmed.sona.feature.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the remote version manifest and decides whether a newer build exists. Best-effort: any
 * network or parse failure returns null so a launch is never blocked by a failed update check.
 */
object UpdateChecker {

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/LhacenMed/Sona/main/version.json"

    /** Returns the available update when the manifest's versionCode exceeds the installed one. */
    suspend fun check(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 15_000
            }
            val json = try { conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } }
            finally { conn.disconnect() }
            AppUpdate.fromJson(json).takeIf { it.versionCode > context.installedVersionCode() }
        }.getOrNull()
    }
}
