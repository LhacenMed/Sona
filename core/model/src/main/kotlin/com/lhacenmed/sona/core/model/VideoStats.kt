package com.lhacenmed.sona.core.model

/**
 * What a video's site counted of it when it was downloaded: when it was uploaded, how often it was watched, and how often
 * liked - each null where the site does not say.
 */
data class VideoStats(val uploadedAtMs: Long?, val viewCount: Long?, val likeCount: Long?)
