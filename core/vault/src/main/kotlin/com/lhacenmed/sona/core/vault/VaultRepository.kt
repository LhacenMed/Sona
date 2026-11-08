package com.lhacenmed.sona.core.vault

import android.net.Uri
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.lifecycle.isAppInForeground
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.VideoSubtitle
import com.lhacenmed.sona.core.model.VideoSubtitleStore
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.vault.data.VaultItem
import com.lhacenmed.sona.core.vault.data.VaultItemDao
import com.lhacenmed.sona.core.vault.data.VaultResumePosition
import com.lhacenmed.sona.core.vault.data.VaultResumePositionDao
import com.lhacenmed.sona.core.vault.data.VaultVideoSubtitle
import com.lhacenmed.sona.core.vault.data.VaultVideoSubtitleDao
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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
        /** Whether a secret code typed into the library's search reveals the way in - see [VaultRepository.matchesSecretCode]. */
        val hasSecretCode: Boolean,
        /** Whether the Settings' way in is gone, the secret code the one way left. */
        val isHidden: Boolean,
        /** Whether the device's fingerprint or face opens it, beside its PIN. */
        val unlocksWithBiometrics: Boolean,
    ) : VaultState
}

/** What a PIN or a security answer was found to be. */
enum class VaultAccessResult { Granted, WrongSecret, LockedOut }

/** The fewest characters a secret code holds - and so the fewest a search is compared with it at. */
const val MIN_SECRET_CODE_LENGTH = 4

/** A secret code as it is compared: its case and the spaces around it do not count. */
private fun String.normalizedSecretCode(): String = trim().lowercase()

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
    private val resumePositionDao: VaultResumePositionDao,
    private val videoSubtitleDao: VaultVideoSubtitleDao,
    private val librarySettings: LibrarySettings,
    @ApplicationScope scope: CoroutineScope,
) {
    private val isUnlocked = MutableStateFlow(false)

    val state: StateFlow<VaultState> = combine(settings.credentials, isUnlocked) { credentials, unlocked ->
        if (credentials == null) {
            VaultState.NotCreated
        } else {
            VaultState.Created(
                pinLength = credentials.pinLength,
                securityQuestion = credentials.securityQuestion,
                lockedUntilEpochMs = credentials.lockedUntilEpochMs,
                isUnlocked = unlocked,
                hasSecretCode = credentials.secretCode != null,
                isHidden = credentials.isHidden,
                unlocksWithBiometrics = credentials.unlocksWithBiometrics,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, VaultState.Loading)

    init {
        scope.launch { isAppInForeground().collect { inForeground -> if (!inForeground) lock() } }
    }

    fun lock() {
        isUnlocked.value = false
    }

    /**
     * Creates the vault, open - it is only ever created from inside the Private Folder, by whoever just chose
     * its PIN. Opened before its credentials are written, so it is never seen created and locked on the way.
     */
    suspend fun create(pin: String, question: String, answer: String) = withContext(Dispatchers.Default) {
        isUnlocked.value = true
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

    /**
     * Opens the vault on the device's fingerprint or face - the system's own check, given; only while the user turned
     * it on, so this is no way in otherwise.
     */
    suspend fun unlockWithBiometrics() {
        if (settings.credentials.first()?.unlocksWithBiometrics == true) isUnlocked.value = true
    }

    /**
     * Whether [text] is the secret code - typed into the library's search, it reveals the way in. Compared as it is
     * kept: without the case or the spaces around it. False while there is none.
     */
    suspend fun matchesSecretCode(text: String): Boolean {
        val code = settings.credentials.first()?.secretCode ?: return false
        val typed = text.normalizedSecretCode()
        return typed.length >= MIN_SECRET_CODE_LENGTH && withContext(Dispatchers.Default) { VaultSecrets.matches(typed, code.salt, code.hash) }
    }

    /**
     * Opens the vault on its secret code, typed into the library's search - a key as the PIN is, WhatsApp's chat lock's
     * way: whoever knows the code needs nothing more. Whether it opened.
     */
    suspend fun unlockWithSecretCode(text: String): Boolean =
        matchesSecretCode(text).also { matches -> if (matches) isUnlocked.value = true }

    /** Keeps [code] as the secret code, from inside the open vault - or, while null, has none. */
    suspend fun setSecretCode(code: String?) {
        check(isUnlocked.value) { "The secret code is set from inside the vault only" }
        val kept = code?.let {
            withContext(Dispatchers.Default) {
                val salt = VaultSecrets.newSalt()
                VaultSecretCode(salt, VaultSecrets.hash(it.normalizedSecretCode(), salt))
            }
        }
        settings.setSecretCode(kept)
    }

    /** Hides the vault, or shows it again - from inside it, and only with a secret code to find it by. */
    suspend fun setHidden(hidden: Boolean) {
        check(isUnlocked.value) { "The vault is hidden from inside it only" }
        if (!hidden || settings.credentials.first()?.secretCode != null) settings.setHidden(hidden)
    }

    suspend fun setUnlocksWithBiometrics(enabled: Boolean) {
        check(isUnlocked.value) { "Biometrics are turned on from inside the vault only" }
        settings.setUnlocksWithBiometrics(enabled)
    }

    /** PIN and answer guesses share one lockout, so the answer is no way around the PIN's. */
    private suspend fun verify(matches: (VaultCredentials) -> Boolean): VaultAccessResult {
        val credentials = checkNotNull(settings.credentials.first()) { "No vault to unlock" }
        val now = System.currentTimeMillis()
        if (now < credentials.lockedUntilEpochMs) return VaultAccessResult.LockedOut
        if (!withContext(Dispatchers.Default) { matches(credentials) }) {
            // A free guess locks nothing, so it leaves no time behind for the keypad to count down to.
            settings.recordFailedAttempt { failedAttempts -> lockoutMillisFor(failedAttempts).let { if (it == 0L) 0L else now + it } }
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

    /**
     * The videos - or, unless [isVideo], the tracks - in the order their list is sorted by, its names compared as the
     * library compares its own; sorted again as they, that order or the library's name sorting change.
     */
    fun items(isVideo: Boolean): Flow<List<VaultItem>> =
        combine(itemDao.observe(isVideo), sortOrder(isVideo), librarySettings.intelligentSortingEnabled.flow, VaultSort::sort)
            .flowOn(Dispatchers.Default)

    /** The section each item of the videos' list - or, unless [isVideo], the tracks' - sits in under its sort: what its fast scroller's popup names. */
    fun sections(isVideo: Boolean): Flow<(VaultItem) -> String?> =
        combine(sortOrder(isVideo), librarySettings.intelligentSortingEnabled.flow) { order, intelligent ->
            val sectionOf: (VaultItem) -> String? = { item -> VaultSort.section(item, order, intelligent) }
            sectionOf
        }

    /** What the folder's lists can be sorted by. */
    val sortCriteria: List<SortCriterion> get() = VaultSort.criteria

    /** How the videos' list - or, unless [isVideo], the tracks' - is sorted, now and as that changes. */
    fun sortOrder(isVideo: Boolean): Flow<SortOrder> = settings.sortOrder(isVideo).map(VaultSort::resolve).distinctUntilChanged()

    suspend fun setSortOrder(isVideo: Boolean, order: SortOrder) = settings.setSortOrder(isVideo, order)

    /** Counts a listen of the item [itemId] that lasted long enough to mean it - see `ListenHistory`. */
    suspend fun countPlay(itemId: Long) = itemDao.countPlay(itemId)

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
            width = item.width,
            height = item.height,
            sizeBytes = file.length(),
        )
    }

    /** Where each video part-way through was left, by its id - what its row's progress line shows. */
    val resumePositionsMs: Flow<Map<Long, Long>> =
        resumePositionDao.observeAll().map { rows -> rows.associate { it.itemId to it.positionMs } }

    /** Read by the player as it starts, written as each video is left - see `ResumePositionRecorder`. */
    suspend fun resumePositions(): Map<Long, Long> =
        resumePositionDao.getAll().associate { it.itemId to it.positionMs }

    suspend fun saveResumePosition(itemId: Long, positionMs: Long) {
        resumePositionDao.upsert(VaultResumePosition(itemId, positionMs))
    }

    suspend fun forgetResumePosition(itemId: Long) {
        resumePositionDao.delete(itemId)
    }

    /** Its videos' subtitles, kept in the vault with the rest of it. */
    val videoSubtitles: VideoSubtitleStore = object : VideoSubtitleStore {
        override val directory: File get() = files.subtitleDirectory

        override suspend fun read(videoId: Long): VideoSubtitle =
            videoSubtitleDao.get(videoId)?.let { VideoSubtitle(it.attachedName, it.offsetMs, it.choice) } ?: VideoSubtitle()

        override suspend fun write(videoId: Long, subtitle: VideoSubtitle) =
            videoSubtitleDao.upsert(VaultVideoSubtitle(videoId, subtitle.attachedName, subtitle.offsetMs, subtitle.choice))
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
            width = track.width,
            height = track.height,
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
        videoSubtitles.deleteAttachedFile(item.id)
    }

    /** So an answer is told apart by its words alone, not by case or stray spaces. */
    private fun String.normalizedAnswer(): String = trim().lowercase()
}
