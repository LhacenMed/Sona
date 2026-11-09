package com.lhacenmed.sona.feature.scanner

import java.io.File

/**
 * A file's last change and size, as one number, with [readVersion] - how what is read of it is read: bumped, every file is
 * read again. What tells the scanner a file it read before needs no second look, as the lyrics index tells a file changed.
 */
internal fun fileSignature(path: String, readVersion: Int): Long =
    File(path).let { file -> (file.lastModified() * 31 + file.length()) * 31 + readVersion }
