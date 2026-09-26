package com.lhacenmed.sona.feature.update.github

import android.os.Build
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/** One published release of Sona - ArchiveTune's `ReleaseInfo`. */
@Immutable
data class Release(
    val tagName: String,
    /** "1.5.0", or "1.5.0-beta.2" - the tag without its "v". */
    val versionName: String,
    val isPreRelease: Boolean,
    /** Its notes, as markdown. */
    val notes: String?,
    /** When it was published, as ISO 8601. */
    val publishedAt: String,
    val htmlUrl: String,
    /** The APK built for this device's processor, or the universal one; null for a release with neither. */
    val apkUrl: String?,
) {
    internal val version: SemanticVersion? get() = SemanticVersion.parse(tagName)

    internal fun toJson(): JSONObject =
        JSONObject()
            .put("tagName", tagName)
            .put("versionName", versionName)
            .put("isPreRelease", isPreRelease)
            .put("notes", notes ?: JSONObject.NULL)
            .put("publishedAt", publishedAt)
            .put("htmlUrl", htmlUrl)
            .put("apkUrl", apkUrl ?: JSONObject.NULL)

    internal companion object {
        fun fromJson(json: JSONObject) = Release(
            tagName = json.getString("tagName"),
            versionName = json.getString("versionName"),
            isPreRelease = json.getBoolean("isPreRelease"),
            notes = json.optStringOrNull("notes"),
            publishedAt = json.getString("publishedAt"),
            htmlUrl = json.getString("htmlUrl"),
            apkUrl = json.optStringOrNull("apkUrl"),
        )

        /**
         * GitHub's release list, drafts left out. Each release publishes an APK per processor and a universal
         * one; the one built for this device is the smaller download, so it is taken when there is one.
         */
        fun listFromGitHub(json: String): List<Release> {
            val array = JSONArray(json)
            return (0 until array.length()).map(array::getJSONObject)
                .filterNot { it.optBoolean("draft") }
                .map { item ->
                    val tagName = item.optString("tag_name")
                    Release(
                        tagName = tagName,
                        versionName = tagName.removePrefix("v"),
                        isPreRelease = item.optBoolean("prerelease"),
                        notes = item.optStringOrNull("body"),
                        publishedAt = item.optString("published_at"),
                        htmlUrl = item.optString("html_url"),
                        apkUrl = item.optJSONArray("assets")?.let(::apkUrlOf),
                    )
                }
        }

        private fun apkUrlOf(assets: JSONArray): String? {
            val urlsByName = (0 until assets.length()).map(assets::getJSONObject)
                .associate { it.optString("name") to it.optString("browser_download_url") }
                .filterKeys { it.endsWith(".apk") }
            val preferredSuffixes = Build.SUPPORTED_ABIS.map { "-$it.apk" } + "-universal.apk"
            return preferredSuffixes.firstNotNullOfOrNull { suffix ->
                urlsByName.entries.firstOrNull { it.key.endsWith(suffix) }?.value
            }
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
