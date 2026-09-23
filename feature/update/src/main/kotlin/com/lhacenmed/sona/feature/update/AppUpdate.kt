package com.lhacenmed.sona.feature.update

import org.json.JSONObject

/**
 * Parsed remote version manifest (version.json). [versionCode] is compared against the installed
 * build's to decide whether a newer build exists; [apkUrl] points at the GitHub Releases asset to
 * download.
 *
 * Manifest shape:
 * ```json
 * { "versionCode": 1001020, "versionName": "0.1.0-alpha.2",
 *   "apkUrl": "https://github.com/.../releases/download/v0.1.0-alpha.2/sona-0.1.0-alpha.2-release-universal.apk",
 *   "notes": "What's new…" }
 * ```
 */
data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
) {
    /** Serializes back to the manifest shape so [UpdateStore] can persist it across launches. */
    fun toJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("apkUrl", apkUrl)
        .put("notes", notes)
        .toString()

    companion object {
        fun fromJson(json: String): AppUpdate = JSONObject(json).run {
            AppUpdate(
                versionCode = getInt("versionCode"),
                versionName = getString("versionName"),
                apkUrl      = getString("apkUrl"),
                notes       = optString("notes"),
            )
        }
    }
}
