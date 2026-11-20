package com.lhacenmed.sona.core.model

/**
 * What a video's site counted of it when it was downloaded - or last read again: when it was uploaded, how often it was
 * watched, and how often liked - each null where the site does not say. [sourceUrl] is the page it came from, what it is
 * read again from.
 */
data class VideoStats(
    val uploadedAtMs: Long?,
    val viewCount: Long?,
    val likeCount: Long?,
    val sourceUrl: String? = null,
    val commentCount: Long? = null,
    /** How often shared - reposted, retweeted - as its site counts it. */
    val repostCount: Long? = null,
) {
    /** Whether it holds any of the site's counts, rather than its page alone. */
    val hasCounts: Boolean get() = listOf(uploadedAtMs, viewCount, likeCount, commentCount, repostCount).any { it != null }

    /** These, each count [fresher] has in place of this one's, and this one's where it has none - a read again never loses one. */
    fun updatedBy(fresher: VideoStats): VideoStats = VideoStats(
        uploadedAtMs = fresher.uploadedAtMs ?: uploadedAtMs,
        viewCount = fresher.viewCount ?: viewCount,
        likeCount = fresher.likeCount ?: likeCount,
        sourceUrl = fresher.sourceUrl ?: sourceUrl,
        commentCount = fresher.commentCount ?: commentCount,
        repostCount = fresher.repostCount ?: repostCount,
    )
}
