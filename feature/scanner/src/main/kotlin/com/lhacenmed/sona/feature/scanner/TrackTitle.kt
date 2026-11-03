package com.lhacenmed.sona.feature.scanner

import java.io.File

/**
 * The title a file goes under, however it was found: its [tag], or - with none - its name without the extension.
 * A file with no title is still a file of the library, never left out of it.
 */
internal fun trackTitleOf(tag: String?, path: String): String =
    tag?.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension.ifEmpty { File(path).name }
