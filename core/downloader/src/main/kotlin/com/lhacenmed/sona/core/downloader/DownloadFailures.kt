package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.DownloadFailure
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** What each failure says, in yt-dlp's words or its site's - the one place a failure is told apart. */
private val FailureWords = listOf(
    DownloadFailure.NO_SPACE to listOf("No space left", "ENOSPC"),
    DownloadFailure.LOGIN_REQUIRED to listOf(
        "Sign in to confirm", "login required", "This video is private", "members-only", "Join this channel", "age-restricted",
        "age verification", "confirm your age", "use --cookies",
    ),
    DownloadFailure.UNAVAILABLE to listOf(
        "Video unavailable", "has been removed", "not available in your country", "This video is no longer available", "HTTP Error 404",
        "geo restriction", "unavailable in your", "blocked in your", "has been disabled",
        // The site refusing the request itself - its page, not a stream gone stale.
        "Unable to download webpage: HTTP Error 403",
    ),
    DownloadFailure.FORMAT_GONE to listOf("Requested format is not available"),
    // A connection that never got an answer - "Unable to download webpage" alone is any failed page, answered or not.
    DownloadFailure.NO_NETWORK to listOf("getaddrinfo", "Temporary failure in name resolution", "timed out", "Connection reset", "Network is unreachable", "No route to host"),
)

/** What a read of the link kept to download from says once its streams no longer work: a fresh read is the whole repair - YTDLnis's. */
private val StaleReadWords = listOf("HTTP Error 403", "HTTP Error 410", "JSONDecodeError", "unable to download video data", "unable to download video subtitles")

/** Why [error] failed a download. */
fun failureOf(error: Throwable): DownloadFailure = when (error) {
    is DownloaderMissingException -> DownloadFailure.NO_DOWNLOADER
    is UnknownHostException, is ConnectException, is SocketTimeoutException -> DownloadFailure.NO_NETWORK
    else -> {
        val message = error.message.orEmpty()
        FailureWords.firstOrNull { (_, words) -> words.any { message.contains(it, ignoreCase = true) } }?.first ?: DownloadFailure.FAILED
    }
}

/** Whether [error] is a read kept too long, rather than the link itself failing. */
fun isStaleRead(error: Throwable): Boolean = StaleReadWords.any { error.message?.contains(it, ignoreCase = true) == true }
