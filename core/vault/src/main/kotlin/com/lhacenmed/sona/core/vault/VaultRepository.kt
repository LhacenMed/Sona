package com.lhacenmed.sona.core.vault

import android.net.Uri
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.lifecycle.isAppInForeground
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.vault.data.VaultItem
import com.lhacenmed.sona.core.vault.data.VaultItemDao
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the Private Folder stands - one value every screen of it is drawn from. */
sealed interface VaultState {
    /** Not read yet - drawn as nothing, so a created vault never flashes its setup first. */
    data object Loading : VaultState

    data object NotCreated : VaultState

    data class Created(
        val pinLength: Int,
        val securityQuestion: String,
        /** Wrong guesses are refused until then - 0 when they are not. */
        val lockedUntilEpochMs: Long,
        val isUnlocked: Boolean,
    ) : VaultState
}

/** What a PIN or a security answer was found to be. */
enum class VaultAccessResult { Granted, WrongSecret, LockedOut }

/**
 * Wrong guesses Android's lock screen's way: a few free, then a wait that grows with each one after.
 * The lockout is kept on disk, so closing the app does not end it.
 */
private fun lockoutMillisFor(failedAttempts: Int): Long = when {
    failedAttempts < 5 -> 0L
    failedAttempts == 5 -> 30_000L
    failedAttempts == 6 -> 60_000L
    failedAttempts == 7 -> 120_000L
    failedAttempts == 8 -> 300_000L
    failedAttempts == 9 -> 600_000L
    else -> 1_800_000L
}

/**
 * The Private Folder: its PIN, and the tracks and videos moved into it - the one way every other module
 * reaches it.
 *
 * Unlocked only in memory, and only while the app is in front: leaving it locks the folder.
 */
@Singleton
class VaultRepository @Inject internal constructor(
    private val settings: VaultSettings,
    private val files: VaultFiles,
    private val itemDao: VaultItemDao,
    @ApplicationScope scope: CoroutineScope,
) {
    private val isUnlocked = MutableStateFlow(false)

    val state: StateFlow<VaultState> = combine(settings.credentials, isUnlocked) { credentials, unlocked ->
        if (credentials == null) {
            VaultState.NotCreated
        } else {
            VaultState.Created(credentials.pinLength, credentials.securityQuestion, credentials.lockedUntilEpochMs, unlocked)
        }
    }.stateIn(scope, SharingStarted.Eagerly, VaultState.Loading)

    init {
        scope.launch { isAppInForeground().collect { inForeground -> if (!inForeground) lock() } }
    }

    fun lock() {
        isUnlocked.value = false
    }

    /** Creates the vault, locked - opening it is still [unlock]'s to do. */
    suspend fun create(pin: String, question: String, answer: String) = withContext(Dispatchers.Default) {
        val pinSalt = VaultSecrets.newSalt()
        val answerSalt = VaultSecrets.newSalt()
        settings.create(
            pinSalt = pinSalt,
            pinHash = VaultSecrets.hash(pin, pinSalt),
            pinLength = pin.length,
            question = question.trim(),
            answerSalt = answerSalt,
            answerHash = VaultSecrets.hash(answer.normalizedAnswer(), answerSalt),
        )
    }

    suspend fun unlock(pin: String): VaultAccessResult =
        verify { VaultSecrets.matches(pin, it.pinSalt, it.pinHash) }.also { if (it == VaultAccessResult.Granted) isUnlocked.value = true }

    /** Checks the answer to the security question without opening anything - the first step of a forgotten PIN. */
    suspend fun verifySecurityAnswer(answer: String): VaultAccessResult = verify { it.answerMatches(answer) }

    /** Replaces a forgotten PIN with [newPin] - [answer] checked again, so this is no way around it - and opens the vault. */
    suspend fun resetPin(answer: String, newPin: String): VaultAccessResult {
        val result = verify { it.answerMatches(answer) }
        if (result == VaultAccessResult.Granted) {
            setPin(newPin)
            isUnlocked.value = true
        }
        return result
    }

    /** Replaces the PIN from inside the open vault, once the current one has been given again. */
    suspend fun changePin(newPin: String) {
        check(isUnlocked.value) { "The PIN is changed from inside the vault only" }
        setPin(newPin)
    }

    /** PIN and answer guesses share one lockout, so the answer is no way around the PIN's. */
    private suspend fun verify(matches: (VaultCredentials) -> Boolean): VaultAccessResult {
        val credentials = checkNotNull(settings.credentials.first()) { "No vault to unlock" }
        val now = System.currentTimeMillis()
        if (now < credentials.lockedUntilEpochMs) return VaultAccessResult.LockedOut
        if (!withContext(Dispatchers.Default) { matches(credentials) }) {
            settings.recordFailedAttempt { failedAttempts -> now + lockoutMillisFor(failedAttempts) }
            return VaultAccessResult.WrongSecret
        }
        settings.clearFailedAttempts()
        return VaultAccessResult.Granted
    }

    private suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        val pinSalt = VaultSecrets.newSalt()
        settings.setPin(pinSalt, VaultSecrets.hash(pin, pinSalt), pin.length)
    }

    private fun VaultCredentials.answerMatches(answer: String): Boolean =
        VaultSecrets.matches(answer.normalizedAnswer(), answerSalt, answerHash)

    fun items(isVideo: Boolean): Flow<List<VaultItem>> = itemDao.observe(isVideo)

    /** Every item, by its id. */
    val itemsById: Flow<Map<Long, VaultItem>> = itemDao.observeAll().map { items -> items.associateBy(VaultItem::id) }

    /** Every item as the Track the players show it as - see [trackOf] - by its id. */
    val tracksById: Flow<Map<Long, Track>> = itemsById.map { items -> items.mapValues { (_, item) -> trackOf(item) } }

    /**
     * [item] as the players take a track: its own id, title, artist, album and length, its file for path and
     * cover - which the app's covers already thumbnail - and none of the library's ids, which it has no part in.
     */
    fun trackOf(item: VaultItem): Track {
        val file = files.fileOf(item)
        return Track(
            id = item.id,
            mediaStoreId = 0L,
            title = item.title,
            artist = item.artist,
            artistId = 0L,
            album = item.album,
            albumId = 0L,
            genre = null,
            genreId = null,
            path = file.path,
            folderPath = file.parent.orEmpty(),
            durationMs = item.durationMs,
            trackNumber = null,
            discNumber = null,
            year = null,
            dateAddedSeconds = item.addedAt / 1000,
            coverArtUri = Uri.fromFile(file).toString(),
            isManuallyScanned = true,
            isVideo = item.isVideo,
        )
    }

    /** Copies [track]'s file in from [source]. The library's copy is the caller's to delete once this returns. */
    suspend fun add(track: Track, source: InputStream): VaultItem = withContext(Dispatchers.IO) {
        val original = File(track.path)
        val fileName = files.import(source, original.extension)
        val item = VaultItem(
            fileName = fileName,
            originalFileName = original.name,
            isVideo = track.isVideo,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            addedAt = System.currentTimeMillis(),
        )
        item.copy(id = itemDao.insert(item))
    }

    /** Puts [item] back in shared storage, where the library picks it up, and out of the vault. */
    suspend fun moveOut(item: VaultItem) = withContext(Dispatchers.IO) {
        files.export(item)
        delete(item)
    }

    suspend fun delete(item: VaultItem) = withContext(Dispatchers.IO) {
        itemDao.delete(item)
        files.delete(item)
    }

    /** So an answer is told apart by its words alone, not by case or stray spaces. */
    private fun String.normalizedAnswer(): String = trim().lowercase()
}
