package com.lhacenmed.sona.feature.backup.media

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * The SHA-256 of what [input] reads, as lowercase hex: what a file is, whatever it is called or wherever it lies.
 * Two files with the same fingerprint have the same bytes, so one stands for the other.
 */
fun fingerprintOf(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun fingerprintOf(file: File): String = file.inputStream().use(::fingerprintOf)
