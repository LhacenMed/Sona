package com.lhacenmed.sona.core.common.storage

import android.content.Context
import java.io.File

/** The files of the user's own the downloader keeps - where each is, read by the downloader and carried by a backup. */
object DownloaderFiles {
    /** The cookies saved from signing in on sites, in the Netscape format yt-dlp reads - kept when the downloader is removed. */
    fun cookies(context: Context): File = File(context.noBackupFilesDir, "download-cookies.txt")

    /** Every video downloaded while the archive is on, by its id at its site - one a line. */
    fun archive(context: Context): File = File(context.filesDir, "download-archive.txt")
}
