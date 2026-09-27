package com.lhacenmed.sona.feature.update.github

import android.os.Build
import androidx.compose.runtime.Immutable
import com.lhacenmed.sona.feature.update.ApkVariant
import com.lhacenmed.sona.feature.update.InstalledBuild
import org.json.JSONArray
import org.json.JSONObject

/**
 * One of a release's APKs: which [variant] it is, where it downloads from, and what it must be once it has -
 * exactly [sizeBytes] long, and hashing to [sha256] when GitHub gave its digest.
 */
@Immutable
data class ReleaseApk(
    val variant: ApkVariant,
    val url: String,
    val sizeBytes: Long,
    /** Lowercase hex, or null for a file GitHub gave no digest for. */
    val sha256: String?,
)

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
    /** Its APKs, one per variant, in [ApkVariant]'s order. */
    val apks: List<ReleaseApk>,
) {
    internal val version: SemanticVersion? get() = SemanticVersion.parse(tagName)

    /** Its APK of [variant], or null when it has none. */
    fun apkOf(variant: ApkVariant): ReleaseApk? = apks.firstOrNull { it.variant == variant }

    /**
     * The APK that updates [build] unless another is chosen: the same variant as [build] when the release
     * has it - the universal one for a universal build - otherwise the one for this device's processor, and
     * failing that the universal one. Null for a release with none of them.
     */
    fun apkFor(build: InstalledBuild): ReleaseApk? {
        val preferred = listOfNotNull(build.variant) + Build.SUPPORTED_ABIS.mapNotNull(ApkVariant::of) + ApkVariant.UNIVERSAL
        return preferred.firstNotNullOfOrNull(::apkOf)
    }

    internal fun toJson(): JSONObject =
        JSONObject()
            .put("tagName", tagName)
            .put("versionName", versionName)
            .put("isPreRelease", isPreRelease)
            .put("notes", notes ?: JSONObject.NULL)
            .put("publishedAt", publishedAt)
            .put("htmlUrl", htmlUrl)
            .put(
                "apks",
                JSONArray(
                    apks.map { apk ->
                        JSONObject()
                            .put("variant", apk.variant.name)
                            .put("url", apk.url)
                            .put("sizeBytes", apk.sizeBytes)
                            .put("sha256", apk.sha256 ?: JSONObject.NULL)
                    },
                ),
            )

    internal companion object {
        fun fromJson(json: JSONObject) = Release(
            tagName = json.getString("tagName"),
            versionName = json.getString("versionName"),
            isPreRelease = json.getBoolean("isPreRelease"),
            notes = json.optStringOrNull("notes"),
            publishedAt = json.getString("publishedAt"),
            htmlUrl = json.getString("htmlUrl"),
            apks = json.getJSONArray("apks").objects().map { apk ->
                ReleaseApk(
                    variant = ApkVariant.valueOf(apk.getString("variant")),
                    url = apk.getString("url"),
                    sizeBytes = apk.getLong("sizeBytes"),
                    sha256 = apk.optStringOrNull("sha256"),
                )
            },
        )

        /** GitHub's release list, drafts left out. */
        fun listFromGitHub(json: String): List<Release> =
            JSONArray(json).objects()
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
                        apks = item.optJSONArray("assets")?.let(::apksOf).orEmpty(),
                    )
                }

        /** The assets that are one of the variants' APKs - GitHub's "sha256:<hex>" digest read as its hex. */
        private fun apksOf(assets: JSONArray): List<ReleaseApk> =
            assets.objects()
                .mapNotNull { asset ->
                    ReleaseApk(
                        variant = ApkVariant.ofAssetName(asset.optString("name")) ?: return@mapNotNull null,
                        url = asset.optString("browser_download_url"),
                        sizeBytes = asset.optLong("size"),
                        sha256 = asset.optStringOrNull("digest")
                            ?.takeIf { it.startsWith(SHA256_PREFIX) }
                            ?.removePrefix(SHA256_PREFIX)
                            ?.lowercase(),
                    )
                }
                .sortedBy { it.variant }

        private const val SHA256_PREFIX = "sha256:"

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
