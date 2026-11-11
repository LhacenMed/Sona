package com.lhacenmed.sona.core.downloader

/** What every read of a link with yt-dlp is given beside its own arguments - the saved commands added to reading that are for it. */
fun interface ReadingArguments {
    suspend fun forLink(url: String): List<String>
}
