package com.lhacenmed.sona.core.vault

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val PIN_SALT = stringPreferencesKey("pin_salt")
private val PIN_HASH = stringPreferencesKey("pin_hash")
private val PIN_LENGTH = intPreferencesKey("pin_length")
private val SECURITY_QUESTION = stringPreferencesKey("security_question")
private val ANSWER_SALT = stringPreferencesKey("answer_salt")
private val ANSWER_HASH = stringPreferencesKey("answer_hash")
private val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
private val LOCKED_UNTIL = longPreferencesKey("locked_until")
private val SECRET_CODE_SALT = stringPreferencesKey("secret_code_salt")
private val SECRET_CODE_HASH = stringPreferencesKey("secret_code_hash")
private val IS_HIDDEN = booleanPreferencesKey("is_hidden")
private val UNLOCKS_WITH_BIOMETRICS = booleanPreferencesKey("unlocks_with_biometrics")

/** What a tab's list is sorted by, and which way - the videos', or the tracks'. */
private fun sortCriterionKey(isVideo: Boolean) = stringPreferencesKey(if (isVideo) "videos_sort_criterion" else "tracks_sort_criterion")
private fun sortDirectionKey(isVideo: Boolean) = stringPreferencesKey(if (isVideo) "videos_sort_direction" else "tracks_sort_direction")

/** A secret code, as kept: salted and hashed, as the PIN is. */
internal data class VaultSecretCode(val salt: String, val hash: String)

/** What the vault was created with, how its wrong guesses stand, and how it is reached and opened. */
internal data class VaultCredentials(
    val pinSalt: String,
    val pinHash: String,
    val pinLength: Int,
    val securityQuestion: String,
    val answerSalt: String,
    val answerHash: String,
    val lockedUntilEpochMs: Long,
    val secretCode: VaultSecretCode?,
    val isHidden: Boolean,
    val unlocksWithBiometrics: Boolean,
)

/** The vault's PIN, security question and lockout - in the vault's own directory, so never in a backup. */
@Singleton
internal class VaultSettings @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(context.vaultDirectory, "vault.preferences_pb") },
    )

    /** Null until a vault is created. Emits once read, so a caller can tell "not created" from "not known yet". */
    val credentials: Flow<VaultCredentials?> = dataStore.data.map { prefs ->
        VaultCredentials(
            pinSalt = prefs[PIN_SALT] ?: return@map null,
            pinHash = prefs[PIN_HASH] ?: return@map null,
            pinLength = prefs[PIN_LENGTH] ?: return@map null,
            securityQuestion = prefs[SECURITY_QUESTION] ?: return@map null,
            answerSalt = prefs[ANSWER_SALT] ?: return@map null,
            answerHash = prefs[ANSWER_HASH] ?: return@map null,
            lockedUntilEpochMs = prefs[LOCKED_UNTIL] ?: 0L,
            secretCode = prefs[SECRET_CODE_SALT]?.let { salt -> prefs[SECRET_CODE_HASH]?.let { hash -> VaultSecretCode(salt, hash) } },
            isHidden = prefs[IS_HIDDEN] ?: false,
            unlocksWithBiometrics = prefs[UNLOCKS_WITH_BIOMETRICS] ?: false,
        )
    }

    /** One edit, so a vault is never found half-created. */
    suspend fun create(pinSalt: String, pinHash: String, pinLength: Int, question: String, answerSalt: String, answerHash: String) {
        dataStore.edit { prefs ->
            prefs[PIN_SALT] = pinSalt
            prefs[PIN_HASH] = pinHash
            prefs[PIN_LENGTH] = pinLength
            prefs[SECURITY_QUESTION] = question
            prefs[ANSWER_SALT] = answerSalt
            prefs[ANSWER_HASH] = answerHash
        }
    }

    suspend fun setPin(pinSalt: String, pinHash: String, pinLength: Int) {
        dataStore.edit { prefs ->
            prefs[PIN_SALT] = pinSalt
            prefs[PIN_HASH] = pinHash
            prefs[PIN_LENGTH] = pinLength
        }
    }

    /** Keeps [code] as the secret code - or, while null, has none, and so stops hiding the vault, which only it reveals. */
    suspend fun setSecretCode(code: VaultSecretCode?) {
        dataStore.edit { prefs ->
            if (code == null) {
                prefs.remove(SECRET_CODE_SALT)
                prefs.remove(SECRET_CODE_HASH)
                prefs.remove(IS_HIDDEN)
            } else {
                prefs[SECRET_CODE_SALT] = code.salt
                prefs[SECRET_CODE_HASH] = code.hash
            }
        }
    }

    suspend fun setHidden(hidden: Boolean) {
        dataStore.edit { it[IS_HIDDEN] = hidden }
    }

    suspend fun setUnlocksWithBiometrics(enabled: Boolean) {
        dataStore.edit { it[UNLOCKS_WITH_BIOMETRICS] = enabled }
    }

    /** Counts a wrong PIN or answer, locking guesses out until [lockedUntil] says for the new count. */
    suspend fun recordFailedAttempt(lockedUntil: (failedAttempts: Int) -> Long) {
        dataStore.edit { prefs ->
            val failedAttempts = (prefs[FAILED_ATTEMPTS] ?: 0) + 1
            prefs[FAILED_ATTEMPTS] = failedAttempts
            prefs[LOCKED_UNTIL] = lockedUntil(failedAttempts)
        }
    }

    suspend fun clearFailedAttempts() {
        dataStore.edit { prefs ->
            prefs.remove(FAILED_ATTEMPTS)
            prefs.remove(LOCKED_UNTIL)
        }
    }

    /** How the videos' list - or, unless [isVideo], the tracks' - was last sorted; null until it ever was. */
    fun sortOrder(isVideo: Boolean): Flow<SortOrder?> = dataStore.data.map { prefs ->
        val criterion = prefs[sortCriterionKey(isVideo)] ?: return@map null
        val direction = prefs[sortDirectionKey(isVideo)] ?: return@map null
        runCatching { SortOrder(SortCriterion.valueOf(criterion), SortDirection.valueOf(direction)) }.getOrNull()
    }

    suspend fun setSortOrder(isVideo: Boolean, order: SortOrder) {
        dataStore.edit { prefs ->
            prefs[sortCriterionKey(isVideo)] = order.criterion.name
            prefs[sortDirectionKey(isVideo)] = order.direction.name
        }
    }
}
