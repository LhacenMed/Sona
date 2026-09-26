package com.lhacenmed.sona.feature.update.github

/**
 * A version as Semantic Versioning orders it - ArchiveTune's `SemVer`: a release outranks its own
 * pre-releases, and pre-release identifiers compare number by number, word by word.
 */
internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>,
) : Comparable<SemanticVersion> {

    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemanticVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).let { if (it != 0) return it }
        if (!isPreRelease || !other.isPreRelease) return other.preRelease.size.sign() - preRelease.size.sign()
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val c = compareIdentifiers(preRelease[i], other.preRelease[i])
            if (c != 0) return c
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    /** "1.5.0", or "1.5.0-beta.2". */
    override fun toString(): String =
        if (isPreRelease) "$major.$minor.$patch-${preRelease.joinToString(".")}" else "$major.$minor.$patch"

    companion object {
        private val Pattern = Regex("""(?i)\bv?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?\b""")

        /** The first version in [text] - a tag such as "v1.5.0-beta.2", or a name - or null when it holds none. */
        fun parse(text: String): SemanticVersion? {
            val match = Pattern.find(text) ?: return null
            val (major, minor, patch, preRelease) = match.destructured
            return SemanticVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                preRelease = preRelease.split('.').filter { it.isNotBlank() },
            )
        }

        // Numbers compare as numbers and rank below words, which compare as text.
        private fun compareIdentifiers(a: String, b: String): Int {
            val aNumber = a.toLongOrNull()
            val bNumber = b.toLongOrNull()
            return when {
                aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                aNumber != null -> -1
                bNumber != null -> 1
                else -> a.compareTo(b)
            }
        }

        private fun Int.sign(): Int = if (this > 0) 1 else 0
    }
}
