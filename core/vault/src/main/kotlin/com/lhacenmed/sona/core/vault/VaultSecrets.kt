package com.lhacenmed.sona.core.vault

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The PIN and the security answer, kept only as salted PBKDF2 hashes - never readable back, and checked in a
 * few milliseconds. The files themselves need no encryption: the vault's directory is closed to every other
 * app, so the PIN guards the one way in, which is through Sona.
 */
internal object VaultSecrets {
    private const val ITERATIONS = 20_000
    private const val HASH_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private val random = SecureRandom()

    fun newSalt(): String = ByteArray(SALT_LENGTH_BYTES).also(random::nextBytes).toBase64()

    fun hash(secret: String, salt: String): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt.fromBase64(), ITERATIONS, HASH_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toBase64()
    }

    /** In constant time, so how long a wrong guess takes says nothing about how close it was. */
    fun matches(secret: String, salt: String, hash: String): Boolean =
        MessageDigest.isEqual(hash(secret, salt).fromBase64(), hash.fromBase64())

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
}
