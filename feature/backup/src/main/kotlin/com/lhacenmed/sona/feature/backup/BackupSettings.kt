package com.lhacenmed.sona.feature.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.feature.backup.archive.BackupKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val SCHEDULE = stringPreferencesKey("schedule")
private val KEY_SALT = stringPreferencesKey("key_salt")
private val SEALED_KEY = stringPreferencesKey("sealed_key")
private val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
private val LAST_BACKUP_CONTENT = stringPreferencesKey("last_backup_content")

private const val KEYSTORE = "AndroidKeyStore"
private const val DEVICE_KEY_ALIAS = "sona_backup_key"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12

/** How often backups are made without being asked for. */
enum class BackupSchedule(val intervalDays: Long?) {
    OFF(null),
    DAILY(1),
    WEEKLY(7),
}

/**
 * How this device makes its backups - in the no-backup directory, so never in a backup itself: the encryption
 * key must not travel with what it seals, and a restore should not change how the device it lands on backs up.
 *
 * The key is kept sealed by one of the Android Keystore's, which never leaves the device, so automatic backups
 * can be encrypted without asking for the passphrase each time.
 *
 * Followed from the moment the app starts - which reads the schedule - so a screen opens on what is stored.
 */
@Singleton
class BackupSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(context.noBackupFilesDir, "backup.preferences_pb") },
    )

    val schedule: StateFlow<BackupSchedule> = dataStore.data
        .map { preferences -> preferences[SCHEDULE]?.let { name -> BackupSchedule.entries.firstOrNull { it.name == name } } ?: BackupSchedule.OFF }
        .stateIn(scope, SharingStarted.Eagerly, BackupSchedule.OFF)

    val isEncrypted: StateFlow<Boolean> = dataStore.data.map { it[SEALED_KEY] != null }.stateIn(scope, SharingStarted.Eagerly, false)

    /** When the last backup was made, or null before the first. */
    val lastBackupAt: StateFlow<Long?> = dataStore.data.map { it[LAST_BACKUP_AT] }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setSchedule(schedule: BackupSchedule) {
        dataStore.edit { it[SCHEDULE] = schedule.name }
    }

    /** The key backups are sealed with, or null while they are not encrypted. */
    internal suspend fun key(): BackupKey? {
        val preferences = dataStore.data.first()
        val salt = preferences[KEY_SALT] ?: return null
        val sealed = preferences[SEALED_KEY] ?: return null
        return BackupKey(salt.fromBase64(), unseal(sealed.fromBase64()))
    }

    /** Encrypts backups from now on with [key], or - for null - stops encrypting them. */
    internal suspend fun setKey(key: BackupKey?) {
        dataStore.edit { preferences ->
            if (key == null) {
                preferences.remove(KEY_SALT)
                preferences.remove(SEALED_KEY)
            } else {
                preferences[KEY_SALT] = key.salt.toBase64()
                preferences[SEALED_KEY] = seal(key.bytes).toBase64()
            }
        }
    }

    /** What the last backup held, as one fingerprint - for an automatic backup to skip one that would hold the same. */
    internal suspend fun lastBackupContent(): String? = dataStore.data.first()[LAST_BACKUP_CONTENT]

    internal suspend fun recordBackup(at: Long, content: String) {
        dataStore.edit {
            it[LAST_BACKUP_AT] = at
            it[LAST_BACKUP_CONTENT] = content
        }
    }
}

private fun seal(bytes: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, deviceKey()) }
    return cipher.iv + cipher.doFinal(bytes)
}

private fun unseal(sealed: ByteArray): ByteArray {
    val spec = GCMParameterSpec(GCM_TAG_BITS, sealed, 0, GCM_IV_BYTES)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, deviceKey(), spec) }
    return cipher.doFinal(sealed, GCM_IV_BYTES, sealed.size - GCM_IV_BYTES)
}

/** The Keystore key sealing the backup key, made the first time it is needed. */
private fun deviceKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getKey(DEVICE_KEY_ALIAS, null) as SecretKey?)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(DEVICE_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
}

private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
