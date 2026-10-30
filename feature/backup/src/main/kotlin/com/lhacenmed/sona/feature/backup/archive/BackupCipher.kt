package com.lhacenmed.sona.feature.backup.archive

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.google.crypto.tink.util.SecretBytes
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val MAGIC = "SONAENC1".toByteArray()
private const val SALT_LENGTH_BYTES = 16
private const val KEY_LENGTH_BYTES = 32
private const val SEGMENT_SIZE_BYTES = 1 shl 20

/** OWASP's count for PBKDF2-HMAC-SHA256: a few seconds on a phone, once per passphrase. */
private const val ITERATIONS = 600_000

/** The key a passphrase made, and the salt it was made with - which a backup carries, so the passphrase alone opens it. */
class BackupKey(val salt: ByteArray, val bytes: ByteArray)

/**
 * How an encrypted backup is sealed: AES-GCM in segments (Tink's streaming AEAD), so a backup of any size is
 * encrypted and checked as it streams, with a key made from the user's passphrase.
 *
 * An encrypted backup starts with [MAGIC] and its key's salt, both also bound into every segment, so neither can
 * be swapped. A plain backup is a zip, which never starts with [MAGIC].
 */
object BackupCipher {

    /** A new key from [passphrase], with a salt of its own. */
    fun newKey(passphrase: CharArray): BackupKey {
        val salt = ByteArray(SALT_LENGTH_BYTES).also(SecureRandom()::nextBytes)
        return BackupKey(salt, derive(passphrase, salt))
    }

    /** The key [passphrase] makes with [salt] - the one a backup sealed with that passphrase needs. */
    fun keyFor(passphrase: CharArray, salt: ByteArray) = BackupKey(salt, derive(passphrase, salt))

    /** [output], sealed with [key]: what is written to the stream returned is encrypted, then written to [output]. */
    fun encrypting(output: OutputStream, key: BackupKey): OutputStream {
        output.write(MAGIC)
        output.write(key.salt)
        return streamingAead(key.bytes).newEncryptingStream(output, MAGIC + key.salt)
    }

    /** The salt [input] was sealed with, or null where it is not encrypted. Reads past the header only if it is one. */
    fun saltOf(input: BufferedInputStream): ByteArray? {
        input.mark(MAGIC.size)
        val start = input.readNBytesCompat(MAGIC.size)
        if (!start.contentEquals(MAGIC)) {
            input.reset()
            return null
        }
        return input.readNBytesCompat(SALT_LENGTH_BYTES)
    }

    /**
     * The name a file whose fingerprint is [fingerprint] is kept under in the cloud while sealed with [key]: keyed,
     * so to anyone without the key it says nothing of which file it is.
     */
    fun blobName(key: BackupKey, fingerprint: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.bytes, "HmacSHA256")) }
        return mac.doFinal(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** [input], past its header, opened with [key]. A wrong key fails as the first bytes are read. */
    fun decrypting(input: InputStream, key: BackupKey): InputStream =
        streamingAead(key.bytes).newDecryptingStream(input, MAGIC + key.salt)

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LENGTH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun streamingAead(keyBytes: ByteArray): StreamingAead {
        val parameters = AesGcmHkdfStreamingParameters.builder()
            .setKeySizeBytes(KEY_LENGTH_BYTES)
            .setDerivedAesGcmKeySizeBytes(KEY_LENGTH_BYTES)
            .setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)
            .setCiphertextSegmentSizeBytes(SEGMENT_SIZE_BYTES)
            .build()
        val key = AesGcmHkdfStreamingKey.create(parameters, SecretBytes.copyFrom(keyBytes, InsecureSecretKeyAccess.get()))
        return AesGcmHkdfStreaming.create(key)
    }
}

/** Exactly [count] bytes, or fewer only at the end of the stream - `readNBytes` arrived in Android 13. */
private fun InputStream.readNBytesCompat(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = read(bytes, read, count - read)
        if (n < 0) return bytes.copyOf(read)
        read += n
    }
    return bytes
}
