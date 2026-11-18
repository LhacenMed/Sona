package com.lhacenmed.sona.feature.onboarding

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.OnboardingPath
import com.lhacenmed.sona.core.datastore.OnboardingProgress
import com.lhacenmed.sona.core.datastore.OnboardingSettings
import com.lhacenmed.sona.core.datastore.OnboardingStep
import com.lhacenmed.sona.core.datastore.QuickPlaySettings
import com.lhacenmed.sona.core.datastore.ThemeChoices
import com.lhacenmed.sona.core.datastore.ThemeSettings
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.model.DEFAULT_DOWNLOAD_FOLDER
import com.lhacenmed.sona.core.model.ThemeMode
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.feature.backup.BackupDevice
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupRepository
import com.lhacenmed.sona.feature.backup.BackupSchedule
import com.lhacenmed.sona.feature.backup.BackupSettings
import com.lhacenmed.sona.feature.backup.OpenedBackup
import com.lhacenmed.sona.feature.backup.RestorePreview
import com.lhacenmed.sona.feature.backup.StoredBackup
import com.lhacenmed.sona.feature.backup.isBackupFileName
import com.lhacenmed.sona.feature.backup.drive.DriveAccess
import com.lhacenmed.sona.feature.backup.work.BackupFailure
import com.lhacenmed.sona.feature.backup.drive.DriveBackups
import com.lhacenmed.sona.feature.backup.drive.DriveConnection
import com.lhacenmed.sona.feature.backup.work.BackupRun
import com.lhacenmed.sona.feature.backup.work.BackupWork
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How downloading is set up: whether the library offers it, and whether it saves to the usual folder. */
data class DownloadSetup(val isOn: Boolean, val savesToDefaultFolder: Boolean, val folder: String)

/** A folder the library's music is in, with how much - and whether it counts, or is excluded. */
data class MusicFolder(val path: String, val name: String, val trackCount: Int, val isIncluded: Boolean)

/** Where a backup to restore is: one of those in the connected account's Drive, or a file on this phone. */
sealed interface BackupSource {
    /** [picked] of [backups] - every one Sona keeps in [account]'s Drive, the newest first. */
    data class Drive(val account: String, val backups: List<StoredBackup>, val picked: StoredBackup) : BackupSource

    data class File(val name: String) : BackupSource

    /**
     * The backup a restore was started from before the setup was last on screen - the app closed while it ran, or ended
     * before it was seen to end. Restoring it again carries on from where it stopped, with nothing to open first.
     */
    data object Earlier : BackupSource
}

/** What connecting a Google account is for: finding backups to restore, backing up from now on, or a restore to carry on. */
enum class DrivePurpose { FIND_BACKUPS, BACK_UP, CARRY_ON_RESTORE }

/** Where restoring a backup stands. */
sealed interface RestoreState {
    /** Choosing where the backup is - [isConnecting] while Google's account sheet is up; [couldNotConnect] after it failed. */
    data class ChoosingSource(val isConnecting: Boolean = false, val couldNotConnect: Boolean = false) : RestoreState

    /** Looking through [account]'s Drive for Sona's backups. */
    data class Searching(val account: String) : RestoreState

    /** [account]'s Drive holds no backup of Sona's - or could not be read: [couldNotRead]. */
    data class NoBackups(val account: String, val couldNotRead: Boolean) : RestoreState

    /**
     * A backup chosen, from [source], and [status] how far it is - what restoring it would do, [preview], once it is open;
     * restoring, at [progress], once it is; stopped, for [failure] where it is one the user can act on.
     */
    data class Chosen(
        val source: BackupSource,
        val status: RestoreStatus,
        val preview: RestorePreview? = null,
        val progress: BackupProgress? = null,
        val failure: BackupFailure? = null,
    ) : RestoreState {

        /** The device the backup was made on, as far as is known: from Drive's list, or from the backup once open. */
        val device: BackupDevice? get() = preview?.device ?: (source as? BackupSource.Drive)?.picked?.device
    }

    /** Restored - from Drive, where it [isFromDrive]. */
    data class Restored(val isFromDrive: Boolean) : RestoreState
}

enum class RestoreStatus { OPENING, NEEDS_PASSPHRASE, WRONG_PASSPHRASE, READY, UNSUPPORTED, UNREADABLE, RESTORING, FAILED }

/** Whether a restore under way - or just done - holds the setup on its step: no back, no skip, until it moves on itself. */
val RestoreState.holdsStep: Boolean
    get() = this is RestoreState.Restored || (this is RestoreState.Chosen && status == RestoreStatus.RESTORING)

/**
 * The first-launch setup: which step it is on, and every choice it offers - each read from, and written straight to, the
 * setting it is, so leaving at any step keeps what was chosen and Settings shows the same.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onboardingSettings: OnboardingSettings,
    private val librarySettings: LibrarySettings,
    private val quickPlaySettings: QuickPlaySettings,
    private val downloadSettings: DownloadSettings,
    private val themeSettings: ThemeSettings,
    private val updateSettings: UpdateSettings,
    private val backupRepository: BackupRepository,
    private val backupSettings: BackupSettings,
    private val driveAccess: DriveAccess,
    private val driveBackups: DriveBackups,
    private val mediaScanner: MediaScanner,
    private val downloaderRuntime: DownloaderRuntime,
    libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _progress = MutableStateFlow(onboardingSettings.progress.value ?: OnboardingProgress(OnboardingPath.FRESH, OnboardingStep.WELCOME))
    val progress: StateFlow<OnboardingProgress> = _progress.asStateFlow()

    private val tracks = combine(libraryRepository.tracks, libraryRepository.videos) { music, videos ->
        music.itemsOrEmpty + videos.itemsOrEmpty
    }

    val trackCount: StateFlow<Int> = tracks.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Each folder's count as last seen - an excluded folder's tracks leave the library, but its row stays to bring them back. */
    private val folderCounts = LinkedHashMap<String, Int>()

    val folders: StateFlow<List<MusicFolder>> = combine(tracks, librarySettings.excludedFolders.flow) { all, excluded ->
        all.groupingBy { it.folderPath }.eachCount().forEach { (path, count) -> folderCounts[path] = count }
        folderCounts.entries
            .sortedByDescending { it.value }
            .map { (path, count) -> MusicFolder(path, displayNameOf(path), count, path !in excluded) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showsShuffleButton: StateFlow<Boolean> = quickPlaySettings.showButton.stateIn(viewModelScope)

    val downloads: StateFlow<DownloadSetup> = downloadSettings.preferences.flow.map { preferences ->
        DownloadSetup(
            isOn = preferences.showsLibraryButton,
            savesToDefaultFolder = preferences.audioFolder == DEFAULT_DOWNLOAD_FOLDER && preferences.videoFolder == DEFAULT_DOWNLOAD_FOLDER,
            folder = preferences.audioFolder,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadSetup(true, true, DEFAULT_DOWNLOAD_FOLDER))

    /** Where the downloader - what downloading takes beyond the app - stands on this device. */
    val downloader: StateFlow<RuntimeState> = downloaderRuntime.state

    val theme: StateFlow<ThemeChoices> = themeSettings.choices.stateIn(viewModelScope)

    val updateChannel: StateFlow<UpdateChannel> = updateSettings.channel.stateIn(viewModelScope)

    private val _restore = MutableStateFlow<RestoreState>(RestoreState.ChoosingSource())
    val restore: StateFlow<RestoreState> = _restore.asStateFlow()

    /** Whether this device backs up on its own - to Drive, once connected. */
    val keepsBackingUp: StateFlow<Boolean> = backupSettings.schedule
        .map { it != BackupSchedule.OFF }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isDriveConnected: StateFlow<Boolean> = driveAccess.isConnected

    /** How the chosen backup is read - from Drive, or a file - kept to read it again with a passphrase. */
    private var readBackup: (suspend () -> InputStream)? = null
    private var restoring: Job? = null

    /** What the Drive connection under way is for. */
    private var connectingFor = DrivePurpose.FIND_BACKUPS

    init {
        // A restore the setup started before is picked up where it stands: still running - the app closed meanwhile, the
        // restore carrying on without it - or ended while no one was looking. Nothing of it is ever lost from sight.
        if (_progress.value.step == OnboardingStep.RESTORE) {
            viewModelScope.launch {
                when (val run = BackupWork.restoreRun(context).first()) {
                    is BackupRun.Running -> follow(RestoreState.Chosen(BackupSource.Earlier, RestoreStatus.RESTORING, progress = run.progress))
                    is BackupRun.Succeeded -> _restore.value = RestoreState.Restored(isFromDrive = driveAccess.isConnected.value)
                    is BackupRun.Failed -> _restore.value = RestoreState.Chosen(BackupSource.Earlier, RestoreStatus.FAILED, failure = run.reason)
                    BackupRun.Stopped, BackupRun.Paused -> _restore.value = RestoreState.Chosen(BackupSource.Earlier, RestoreStatus.FAILED)
                    null -> Unit
                }
            }
        }
    }

    // Moving between steps - each step written down as it is reached, so the setup resumes there.

    fun getStarted() = advance(OnboardingPath.FRESH, from = OnboardingStep.WELCOME)

    /** Starts restoring from the beginning - where to restore from - whatever an earlier visit left on its step. */
    fun startRestore() {
        if (!_restore.value.holdsStep) _restore.value = RestoreState.ChoosingSource()
        advance(OnboardingPath.RESTORE, from = OnboardingStep.WELCOME)
    }

    /**
     * Leaves restoring for the setup a new user goes through, from the step after access - nothing restored. What restoring
     * showed stays as it was, so its step leaves the screen as it stood - see [startRestore].
     */
    fun startFresh() = go(OnboardingPath.FRESH, OnboardingStep.FOLDERS)

    /**
     * Skips what is on screen: from the first step, the whole setup - every setting as it is - to its last; from any other,
     * that step - restoring's, nothing restored, to the last.
     */
    fun skip() {
        val (path, step) = _progress.value
        if (step == OnboardingStep.WELCOME) go(OnboardingPath.FRESH, OnboardingStep.DONE) else advance(path, from = step)
    }

    /** Straight to [step] of the way the setup is on - from the progress, one of its segments pressed. */
    fun goTo(step: OnboardingStep) {
        val (path, current) = _progress.value
        if (step in path.steps && step != current && !(current == OnboardingStep.RESTORE && _restore.value.holdsStep)) go(path, step)
    }

    fun next() {
        val (path, step) = _progress.value
        advance(path, from = step)
    }

    /** On along [path] from [from] - past a step with nothing left to ask. */
    private fun advance(path: OnboardingPath, from: OnboardingStep) {
        val steps = path.steps.drop(path.steps.indexOf(from) + 1)
        (steps.firstOrNull { !it.hasNothingToAsk } ?: steps.lastOrNull())?.let { go(path, it) }
    }

    /** Whether there is nothing left to ask on a step: access, with every permission already given. */
    private val OnboardingStep.hasNothingToAsk: Boolean
        get() = this == OnboardingStep.ACCESS && AppPermission.needed.all { it.isGranted(context) }

    /** Back a step - false on the first, where back leaves the app. Within restoring, back to choosing where from. */
    fun back(): Boolean {
        val (path, step) = _progress.value
        val restore = _restore.value
        if (step == OnboardingStep.RESTORE && restore.holdsStep) return true
        if (step == OnboardingStep.RESTORE && restore !is RestoreState.ChoosingSource) {
            _restore.value = RestoreState.ChoosingSource()
            return true
        }
        // Past a step with nothing left to ask, as going forward does. The path stays: on the first step it is chosen again.
        val previous = path.steps.take(path.steps.indexOf(step)).lastOrNull { !it.hasNothingToAsk || it == OnboardingStep.WELCOME } ?: return false
        go(path, previous)
        return true
    }

    fun finish() {
        viewModelScope.launch { onboardingSettings.finish() }
    }

    private fun go(path: OnboardingPath, step: OnboardingStep) {
        val progress = OnboardingProgress(path, step)
        _progress.value = progress
        viewModelScope.launch { onboardingSettings.setProgress(progress) }
    }

    // The steps' choices.

    fun setFolderIncluded(path: String, included: Boolean) {
        viewModelScope.launch {
            if (included) librarySettings.removeExcludedFolders(setOf(path)) else librarySettings.addExcludedFolders(setOf(path))
            mediaScanner.requestScan()
        }
    }

    fun setShowsShuffleButton(shows: Boolean) {
        viewModelScope.launch { quickPlaySettings.setShowButton(shows) }
    }

    fun setDownloadsOn(on: Boolean) {
        viewModelScope.launch { downloadSettings.updatePreferences { it.copy(showsLibraryButton = on) } }
    }

    /** Fetches the downloader - on the app's own time, so it carries on whatever the setup moves on to. */
    fun downloadDownloader() = downloaderRuntime.download()

    fun stopDownloadingDownloader() = downloaderRuntime.cancel()

    /** Saves every download - sound and video - to [folder]. */
    fun setDownloadFolder(folder: String) {
        viewModelScope.launch { downloadSettings.updatePreferences { it.copy(audioFolder = folder, videoFolder = folder) } }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themeSettings.setThemeMode(mode) }
    }

    /**
     * Where the app's colours come from with nothing playing: the device's wallpaper, or a preset - each its own setting,
     * as Settings has them. Settings' dynamic colours - the wallpaper's and the cover's at once - are split into the two,
     * so changing one leaves the other as it was.
     */
    fun setWallpaperColors(on: Boolean) = changeColors { themeSettings.setWallpaperColors(on) }

    /** Colours from the playing cover, over whichever the app has otherwise - see [setWallpaperColors]. */
    fun setCoverColors(on: Boolean) = changeColors { themeSettings.setCoverColors(on) }

    fun setAccent(paletteId: String) = changeColors {
        themeSettings.setWallpaperColors(false)
        themeSettings.setColorPalette(paletteId)
    }

    private fun changeColors(change: suspend () -> Unit) {
        viewModelScope.launch {
            val theme = themeSettings.choices.value
            if (theme.dynamicColors) {
                themeSettings.setDynamicColors(false)
                themeSettings.setWallpaperColors(true)
                themeSettings.setCoverColors(true)
            }
            change()
        }
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch { updateSettings.setChannel(channel) }
    }

    /** Turns automatic updates on or off, as the step's two buttons say, and moves on. */
    fun chooseAutoUpdate(on: Boolean) {
        viewModelScope.launch { updateSettings.setAutoUpdate(on) }
        next()
    }

    // Restoring.

    /**
     * Connects a Google account - through Google's own account sheet, and its consent screen the first time, which
     * [onConsent] opens and [finishConnectingDrive] hears back from - for [purpose].
     */
    fun connectDrive(onConsent: (PendingIntent) -> Unit, purpose: DrivePurpose) {
        connectingFor = purpose
        if (purpose == DrivePurpose.FIND_BACKUPS) _restore.value = RestoreState.ChoosingSource(isConnecting = true)
        viewModelScope.launch {
            when (val connection = runCatching { driveAccess.connect() }.getOrNull()) {
                is DriveConnection.NeedsConsent -> onConsent(connection.consent)
                DriveConnection.Connected -> onDriveConnected()
                null -> onDriveNotConnected(failed = true)
            }
        }
    }

    /** Finishes connecting with what Google's consent screen returned - nothing, where the user [wasCancelled] it. */
    fun finishConnectingDrive(data: Intent?, wasCancelled: Boolean) {
        viewModelScope.launch {
            when {
                wasCancelled -> onDriveNotConnected(failed = false)
                driveAccess.finishConnecting(data) -> onDriveConnected()
                else -> onDriveNotConnected(failed = true)
            }
        }
    }

    private suspend fun onDriveConnected() {
        when (connectingFor) {
            DrivePurpose.FIND_BACKUPS -> searchDrive()
            DrivePurpose.BACK_UP -> backupSettings.setSchedule(BackupSchedule.WEEKLY)
            DrivePurpose.CARRY_ON_RESTORE -> restore()
        }
    }

    /** Back to where connecting was asked from - saying so where it [failed], rather than was turned away from. */
    private fun onDriveNotConnected(failed: Boolean) {
        if (connectingFor == DrivePurpose.FIND_BACKUPS) _restore.value = RestoreState.ChoosingSource(couldNotConnect = failed)
    }

    /** Looks for Sona's backups in the connected account's Drive, and opens the newest. */
    private suspend fun searchDrive() {
        val account = driveAccess.account.first { it != null } ?: return
        _restore.value = RestoreState.Searching(account)
        val backups = try {
            driveBackups.list()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        _restore.value = if (backups.isNullOrEmpty()) {
            RestoreState.NoBackups(account, couldNotRead = backups == null)
        } else {
            open(BackupSource.Drive(account, backups, backups.first()))
        }
    }

    /** Picks another of the backups in Drive. */
    fun pickDriveBackup(backup: StoredBackup) {
        val source = (_restore.value as? RestoreState.Chosen)?.source as? BackupSource.Drive ?: return
        if (source.picked != backup) _restore.value = open(source.copy(picked = backup))
    }

    fun openBackupFile(uri: Uri) {
        val name = nameOf(uri)
        // Among plain bytes, only its name tells a backup apart: anything else is not read at all.
        if (!isBackupFileName(name)) {
            _restore.value = RestoreState.Chosen(BackupSource.File(name), RestoreStatus.UNREADABLE)
            return
        }
        _restore.value = open(BackupSource.File(name)) {
            context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        }
    }

    fun submitPassphrase(passphrase: String) {
        val chosen = _restore.value as? RestoreState.Chosen ?: return
        val read = readBackup ?: return
        _restore.value = openWith(chosen.source, read, passphrase.toCharArray())
    }

    private fun open(source: BackupSource.Drive): RestoreState = open(source) { driveBackups.open(source.picked) }

    private fun open(source: BackupSource, read: suspend () -> InputStream): RestoreState {
        readBackup = read
        return openWith(source, read, passphrase = null)
    }

    /** Opens the backup [read] reads, from [source] - reading as it starts; the state it is in once read follows. */
    private fun openWith(source: BackupSource, read: suspend () -> InputStream, passphrase: CharArray?): RestoreState {
        viewModelScope.launch {
            val opened = backupRepository.open(open = read, passphrase = passphrase)
            // Another backup chosen meanwhile is the one that counts.
            if ((_restore.value as? RestoreState.Chosen)?.source != source) return@launch
            _restore.value = RestoreState.Chosen(
                source = source,
                preview = (opened as? OpenedBackup.Ready)?.preview,
                status = when (opened) {
                    is OpenedBackup.Ready -> RestoreStatus.READY
                    OpenedBackup.NeedsPassphrase -> RestoreStatus.NEEDS_PASSPHRASE
                    OpenedBackup.WrongPassphrase -> RestoreStatus.WRONG_PASSPHRASE
                    OpenedBackup.Unsupported -> RestoreStatus.UNSUPPORTED
                    OpenedBackup.Unreadable -> RestoreStatus.UNREADABLE
                },
            )
        }
        return RestoreState.Chosen(source, RestoreStatus.OPENING)
    }

    /**
     * Restores the backup opened, every file it can bring back included - or, stopped, carries on from where it stopped -
     * then follows it to its end. It runs on its own, in the background with its notification, so it ends whether or not the
     * setup is on screen meanwhile; the setup picks it up again if not - see [follow].
     */
    fun restore() {
        val chosen = _restore.value as? RestoreState.Chosen ?: return
        if (restoring?.isActive == true) return
        when (chosen.status) {
            RestoreStatus.READY -> {
                val preview = chosen.preview ?: return
                follow(chosen) {
                    backupRepository.chooseFilesToRestore(preview.files.fingerprintsOf(preview.files.all))
                    BackupWork.restore(context)
                }
            }
            RestoreStatus.FAILED -> follow(chosen) { BackupWork.restore(context) }
            else -> Unit
        }
    }

    /**
     * Follows the restore [start] starts - or the one already under way - step by step, to how it ends: restored, and, from
     * Drive, backing up there from now on, as the account it came from is connected; or stopped, for the reason it gives.
     */
    private fun follow(chosen: RestoreState.Chosen, start: suspend () -> Unit = {}) {
        restoring?.cancel()
        _restore.value = chosen.copy(status = RestoreStatus.RESTORING, progress = chosen.progress, failure = null)
        restoring = viewModelScope.launch {
            start()
            // The latest run may be one from before: what counts is what follows this one running.
            val outcome = BackupWork.restoreRun(context)
                .dropWhile { it !is BackupRun.Running }
                .onEach { run ->
                    if (run is BackupRun.Running) _restore.value = chosen.copy(status = RestoreStatus.RESTORING, progress = run.progress, failure = null)
                }
                .first { it != null && it !is BackupRun.Running }
            _restore.value = when (outcome) {
                is BackupRun.Succeeded -> {
                    val isFromDrive = chosen.source is BackupSource.Drive || (chosen.source == BackupSource.Earlier && driveAccess.isConnected.value)
                    if (isFromDrive) backupSettings.setSchedule(BackupSchedule.WEEKLY)
                    RestoreState.Restored(isFromDrive)
                }
                is BackupRun.Failed -> chosen.copy(status = RestoreStatus.FAILED, progress = latestProgress(), failure = outcome.reason)
                else -> chosen.copy(status = RestoreStatus.FAILED, progress = latestProgress())
            }
        }
    }

    /** How far the restore on screen had come - for one stopped to show where. */
    private fun latestProgress(): BackupProgress? = (_restore.value as? RestoreState.Chosen)?.progress

    /** Backs up to the connected Drive every week from now on - or stops. */
    fun setKeepsBackingUp(keeps: Boolean) {
        viewModelScope.launch { backupSettings.setSchedule(if (keeps) BackupSchedule.WEEKLY else BackupSchedule.OFF) }
    }

    /** Moves on from a restore done - what it showed staying, so its step leaves the screen as it stood. */
    fun finishRestore() = next()

    private fun nameOf(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
}

/** A folder as the setup names it: its path from the storage it is on - "Music/Rock" - or its own name past that. */
private fun displayNameOf(path: String): String =
    StorageRoot.find(path)?.let { path.substring(it.range.last + 1) }?.takeIf { it.isNotEmpty() } ?: path.substringAfterLast('/')

/** The start of a path on shared storage: the device's own, `/storage/emulated/0/`, or a card's, `/storage/1A2B-3C4D/`. */
private val StorageRoot = Regex("^/storage/(emulated/\\d+|[^/]+)/")
