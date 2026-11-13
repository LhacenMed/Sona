package com.lhacenmed.sona.feature.download

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import java.util.Locale
import com.lhacenmed.sona.core.downloader.pickedCaptions
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.downloader.DirectTransfer
import com.lhacenmed.sona.core.downloader.DownloadWorkspaces
import com.lhacenmed.sona.core.downloader.DownloaderMissingException
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.LinkPreview
import com.lhacenmed.sona.core.downloader.LinkPreviews
import com.lhacenmed.sona.core.downloader.LiveDownload
import com.lhacenmed.sona.core.downloader.LiveDownloads
import com.lhacenmed.sona.core.downloader.MediaFormat
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.MediaResolver
import com.lhacenmed.sona.core.downloader.PlaylistEntry
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.failureOf
import com.lhacenmed.sona.core.model.ClipRange
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadBatch
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadCommand
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.preferredFor
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.tageditor.SongSummary
import com.lhacenmed.sona.feature.tageditor.SongTagging
import com.lhacenmed.sona.feature.tageditor.VideoSong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the link sheet shows of the link given - null while there is none. */
sealed interface LinkSheet {
    /** Being read - shown meanwhile as [preview] has it, where the link is one shown before it is read. */
    data class Reading(val preview: LinkPreview?) : LinkSheet

    data class Found(val info: MediaInfo) : LinkSheet

    /**
     * [requests], as [kind], asked for while some may be in the library already - as the check of it goes, see [LibraryCheck]:
     * those found are downloaded again only where picked, the rest as they were asked - to start at [startAt].
     */
    data class AlreadySaved(val requests: List<DownloadRequest>, val kind: DownloadKind, val startAt: Long?) : LinkSheet

    /** [url] could not be read - by the app, or by yt-dlp, which is here - for [failure], [detail] in yt-dlp's own words. */
    data class Unreadable(val url: String, val failure: DownloadFailure, val detail: String?) : LinkSheet

    /**
     * Downloading it as [kind] needs the downloader, which is not here - [info] the link as read, where the app read it itself;
     * null where only yt-dlp reads its site. [savesAsAudio] where its sound alone downloads without it.
     */
    data class NeedsDownloader(val url: String, val info: MediaInfo?, val kind: DownloadKind, val savesAsAudio: Boolean = false) : LinkSheet
}

/** Every download, as the lists section it: what is still to download, then what was finished today, then before. */
data class DownloadSections(val downloading: List<Download>, val today: List<Download>, val earlier: List<Download>) {
    /** What is over - the history. */
    val finished: List<Download> get() = today + earlier
}

/** What the history is narrowed to: songs, audio files, videos, subtitles, or what failed. */
enum class HistoryFilter(@StringRes val labelRes: Int) {
    SONGS(R.string.download_filter_songs),
    AUDIO(R.string.download_kind_audio),
    VIDEO(R.string.download_kind_video),
    SUBTITLES(R.string.download_kind_subtitles),
    FAILED(R.string.download_filter_failed);

    fun matches(download: Download): Boolean = when (this) {
        SONGS -> download.choices.kind == DownloadKind.AUDIO && download.choices.savesAsSong
        AUDIO -> download.choices.kind == DownloadKind.AUDIO && !download.choices.savesAsSong
        VIDEO -> download.choices.kind == DownloadKind.VIDEO
        SUBTITLES -> download.choices.kind == DownloadKind.SUBTITLES
        FAILED -> download.status == DownloadStatus.FAILED
    }
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
    private val settings: DownloadSettings,
    private val downloader: DownloaderRuntime,
    private val resolver: MediaResolver,
    private val linkPreviews: LinkPreviews,
    private val directTransfer: DirectTransfer,
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController,
    private val workspaces: DownloadWorkspaces,
    private val live: LiveDownloads,
    private val libraryCopies: LibraryCopies,
    dao: DownloadDao,
) : ViewModel() {

    val running: StateFlow<Map<Long, RunningDownload>> = queue.running

    /** The downloads running, as they may be played while they download. */
    val liveDownloads: StateFlow<Map<Long, LiveDownload>> = live.downloads

    val preferences: StateFlow<DownloaderPreferences> = settings.preferences.stateIn(viewModelScope)

    private val _query = MutableStateFlow<String?>(null)

    /** What the downloads are searched for - null while they are not. */
    val query: StateFlow<String?> = _query.asStateFlow()

    private val _filter = MutableStateFlow<HistoryFilter?>(null)

    /** What the history is narrowed to - null for every download. */
    val filter: StateFlow<HistoryFilter?> = _filter.asStateFlow()

    /** Every download, the newest first - null until it has been read. */
    val downloads: StateFlow<List<Download>?> = queue.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The saved downloads whose every file is gone from the device - deleted, or moved where the library does not follow -
     * looked at again whenever the library changes: a file moved or renamed that it follows is still the download's own.
     */
    val missingFiles: StateFlow<Set<Long>> = combine(downloads, libraryRepository.tracks, libraryRepository.videos) { all, _, _ -> all.orEmpty() }
        .map { all ->
            all.filter { it.status == DownloadStatus.DONE && it.savedPaths.isNotEmpty() && it.savedPaths.none { path -> File(path).exists() } }.mapTo(HashSet(), Download::id)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Every download as the lists show it - null until it has been read, so an empty list is never shown for a moment. */
    val sections: StateFlow<DownloadSections?> = combine(downloads, _query, _filter) { all, query, filter ->
        all ?: return@combine null
        val matching = all.filter { (query.isNullOrBlank() || it.matches(query.trim())) && (filter == null || filter.matches(it)) }
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val (finished, unfinished) = matching.partition { it.status.isFinished }
        val (today, earlier) = finished.sortedByDescending { it.endedAt }.partition { it.endedAt >= startOfToday }
        DownloadSections(downloading = unfinished.sortedBy { it.createdAt }, today = today, earlier = earlier)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** How the user downloads - the last kind used, and every change kept, for the next download and a Quick download alike. */
    val choices: StateFlow<DownloadChoices> = settings.choices.stateIn(viewModelScope)

    private val _sheetChoices = MutableStateFlow(settings.choices.value)

    /** How the link open in the sheet is to download: the user's own [choices], as changed for it alone. */
    val sheetChoices: StateFlow<DownloadChoices> = _sheetChoices.asStateFlow()

    private val _options = MutableStateFlow(DownloadOptions())

    /** What the next download asks beyond the choices - for it alone, let go once it is queued. */
    val options: StateFlow<DownloadOptions> = _options.asStateFlow()

    private val _pickedFormat = MutableStateFlow<MediaFormat?>(null)

    /** The link's own format picked for the next download - for the kind it is, picture or sound. */
    val pickedFormat: StateFlow<MediaFormat?> = _pickedFormat.asStateFlow()

    private val _startAt = MutableStateFlow<Long?>(null)

    /** When the next download is to start - null for as soon as it can. */
    val startAt: StateFlow<Long?> = _startAt.asStateFlow()

    /** The saved commands - kept at hand from the start, as the one preferred is what every link opens with. */
    val commands: StateFlow<List<DownloadCommand>> = dao.observeCommands().map { rows -> rows.map(DownloadCommandEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloaderState: StateFlow<RuntimeState> = downloader.state

    private val _linkEntry = MutableStateFlow("")

    /** The link being typed in the link field - emptied as it opens. */
    val linkEntry: StateFlow<String> = _linkEntry.asStateFlow()

    private val _linkSheet = MutableStateFlow<LinkSheet?>(null)
    val linkSheet: StateFlow<LinkSheet?> = _linkSheet.asStateFlow()

    private val _song = MutableStateFlow<VideoSong?>(null)

    /** The song the link open in the sheet is to be saved as, where its sound is saved as a song - found as soon as it is read. */
    val song: StateFlow<VideoSong?> = _song.asStateFlow()

    private var link: String? = null
    private var readJob: Job? = null

    /** The link whose song was looked for - looked for once, whatever the sheet's choices and its read go through. */
    private var songLookedFor: String? = null

    init {
        // A link is pasted from here: what reading it would wait on first is done now.
        resolver.warmUp(settings.preferences.value)
        // The downloader got for the link waiting on it: the download it was got for goes on by itself.
        viewModelScope.launch {
            downloader.state.filter { it == RuntimeState.Installed }.collect {
                (_linkSheet.value as? LinkSheet.NeedsDownloader)?.let { waiting ->
                    if (waiting.info == null) open(waiting.url) else onDownload(waiting.kind)
                }
            }
        }
    }

    /** A link pasted, shared or typed: read, and shown in the sheet. */
    fun open(url: String) {
        link = url
        _linkEntry.value = ""
        resetOptions()
        readJob?.cancel()
        resolver.cached(url)?.let {
            _linkSheet.value = LinkSheet.Found(it.info)
            onFound(url, it.info)
            readJob = viewModelScope.launch { completeShown(url) }
            return
        }
        _linkSheet.value = LinkSheet.Reading(preview = null)
        readJob = viewModelScope.launch {
            // Nezzel's early preview, all at once beside the full read, which is what downloads it: what the link showed when
            // last opened, or a YouTube video's picture, from the device; then its title and channel as soon as its site's
            // oEmbed answers. The picture first shown stays, so nothing flickers as the rest lands.
            var shown: LinkPreview? = null
            fun show(preview: LinkPreview) {
                shown = preview.copy(thumbnailUrl = shown?.thumbnailUrl ?: preview.thumbnailUrl)
                if (_linkSheet.value is LinkSheet.Reading) _linkSheet.value = LinkSheet.Reading(shown)
            }
            val preview = launch {
                linkPreviews.known(url)?.let(::show)
                if (shown?.title == null) linkPreviews.read(url)?.let(::show)
            }
            _linkSheet.value = try {
                val read = resolver.resolve(url, settings.preferences.value).info
                preview.cancel()
                val info = shown?.thumbnailUrl?.let { read.copy(thumbnailUrl = it) } ?: read
                linkPreviews.remember(url, info)
                LinkSheet.Found(info)
            } catch (e: CancellationException) {
                throw e
            } catch (_: DownloaderMissingException) {
                LinkSheet.NeedsDownloader(url, info = null, kind = choices.value.kind)
            } catch (e: Exception) {
                LinkSheet.Unreadable(url, failureOf(e), e.message)
            }
            (_linkSheet.value as? LinkSheet.Found)?.let { onFound(url, it.info) }
            completeShown(url)
        }
    }

    /**
     * The video shown, completed as its download would be - what the app's own read of it leaves to its watch page, its chapters
     * and its Most replayed graph - and shown so, nothing else of it changing. The download then has nothing left to read.
     */
    private suspend fun completeShown(url: String) {
        val shown = (_linkSheet.value as? LinkSheet.Found)?.info?.takeUnless { it.isPlaylist } ?: return
        val full = try {
            resolver.resolveForDownload(url, settings.preferences.value).info
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        if (link == url && full.chaptersJson != shown.chaptersJson) _linkSheet.value = LinkSheet.Found(shown.copy(chaptersJson = full.chaptersJson))
    }

    /** [url] read as [info]: its song looked for, and the library looked through for it, at once - and downloaded, where asked for already. */
    private fun onFound(url: String, info: MediaInfo) {
        findSong()
        startCheck(url, info)
        if (_downloadsOnceRead.value && !info.isPlaylist) {
            _downloadsOnceRead.value = false
            onDownload()
        }
    }

    private val _downloadsOnceRead = MutableStateFlow(false)

    /** Whether Download was pressed while the link was still read: it downloads the moment it is - see [onDownloadOnceRead]. */
    val downloadsOnceRead: StateFlow<Boolean> = _downloadsOnceRead.asStateFlow()

    /** Download, pressed while the link is still read: done the moment it is read, as the sheet then stands. */
    fun onDownloadOnceRead() {
        _downloadsOnceRead.value = true
    }

    /**
     * Looks for the song the video found is, once, as soon as it is found - whatever it is to be downloaded as: so it is there
     * the moment a song is asked for, and the library is looked through for it before anything downloads.
     */
    private fun findSong() {
        val info = (_linkSheet.value as? LinkSheet.Found)?.info?.takeUnless { it.isPlaylist } ?: return
        if (songLookedFor == link) return
        songLookedFor = link
        _song.value?.close()
        // YouTube Music names the song itself; any other video is read by its title and channel.
        _song.value = SongTagging.songFor(info.track ?: info.title, info.artist ?: info.uploader, info.durationSeconds, info.thumbnailUrl, viewModelScope)
    }

    /** What the clipboard holds, pasted: its link opened. Whether it held one - with none, one is typed instead. */
    fun onPaste(clipboard: String?): Boolean = linkIn(clipboard)?.also(::open) != null

    /** The link as typed in the link field - opened as soon as it is one. */
    fun onLinkEntryChange(text: String) {
        _linkEntry.value = text
        linkIn(text)?.let(::open)
    }

    fun onDismissLink() {
        readJob?.cancel()
        _downloadsOnceRead.value = false
        checkJob?.cancel()
        _check.value = null
        _linkSheet.value = null
        link = null
        resetOptions()
    }

    /**
     * Downloads the link - or the playlist's [entries] picked - as the sheet's choices say, as [kind]: kept as the kind to start
     * from next time. Where it needs the downloader and the downloader is not here, the sheet offers it instead.
     */
    fun onDownload(kind: DownloadKind = _sheetChoices.value.kind, entries: List<PlaylistEntry>? = null) {
        val url = link ?: return
        val info = (_linkSheet.value as? LinkSheet.Found)?.info ?: (_linkSheet.value as? LinkSheet.NeedsDownloader)?.info
        val chosen = _sheetChoices.value.copy(kind = kind)
        // Captions alone are a download of their own: what to start from next time stays sound or picture.
        if (kind != choices.value.kind && kind.isMedia) remember { it.copy(kind = kind) }
        if (downloader.state.value != RuntimeState.Installed && !(info != null && savesWithoutDownloader(info, chosen))) {
            val savesAsAudio = info != null && kind == DownloadKind.VIDEO && savesWithoutDownloader(info, chosen.copy(kind = DownloadKind.AUDIO))
            _linkSheet.value = LinkSheet.NeedsDownloader(url, info, kind, savesAsAudio)
            return
        }
        val options = _options.value.copy(
            formatId = _pickedFormat.value?.takeIf { kind.isMedia && it.hasVideo == (kind == DownloadKind.VIDEO) }?.selector,
            song = _song.value?.takeIf { kind == DownloadKind.AUDIO && chosen.savesAsSong }?.plan,
            // Captions alone are taken as the sheet showed them - its speech transcribed among them where it has none.
            subtitleLanguages = _options.value.subtitleLanguages ?: info?.takeIf { kind == DownloadKind.SUBTITLES && !it.isPlaylist }
                ?.pickedCaptions(chosen, _options.value, Locale.getDefault().language, settings.preferences.value.transcribes),
        )
        // A playlist's videos listed as one, under its own name.
        val batch = entries?.let { DownloadBatch("playlist:${System.currentTimeMillis()}", info?.title ?: hostOf(url)) }
        val requests = entries?.map { DownloadRequest(it.url, it.title, it.thumbnailUrl, it.uploader, chosen, options.copy(formatId = null), batch) }
            ?: listOf(DownloadRequest(url, info?.title, info?.thumbnailUrl, info?.uploader, chosen, options))
        val startAt = _startAt.value
        // Nothing in the library is downloaded again untold: where the check is over and found none of it, it downloads now;
        // otherwise what it finds - as it goes on finding it - is shown at once.
        val check = _check.value
        if (check == null || !kind.isMedia || (check.isDone && requests.none { it.url in check.of(kind) })) {
            queueRequests(requests, startAt)
        } else {
            _downloadsOnceChecked.value = false
            _linkSheet.value = LinkSheet.AlreadySaved(requests, kind, startAt)
        }
    }

    /**
     * Downloads what [sheet] holds: every request not in the library, and of those that are, the ones [againUrls] names - saved
     * all the same.
     */
    fun onDownloadAgain(sheet: LinkSheet.AlreadySaved, againUrls: Set<String>) {
        val copies = _check.value?.of(sheet.kind).orEmpty()
        val requests = sheet.requests.mapNotNull { request ->
            when {
                request.url !in copies -> request
                request.url in againUrls -> request.copy(options = request.options.copy(downloadsAgain = true))
                else -> null
            }
        }
        if (requests.isEmpty()) onDismissLink() else queueRequests(requests, sheet.startAt)
    }

    /**
     * Downloads what [sheet] holds as soon as the check is over: at once where it finds none of it, else left for the user to pick
     * among what it found.
     */
    fun onDownloadOnceChecked(sheet: LinkSheet.AlreadySaved) {
        _downloadsOnceChecked.value = true
        if (_check.value?.isDone == true) settleOnceChecked(sheet)
    }

    private fun settleOnceChecked(sheet: LinkSheet.AlreadySaved) {
        if (!_downloadsOnceChecked.value) return
        _downloadsOnceChecked.value = false
        val copies = _check.value?.of(sheet.kind).orEmpty()
        if (sheet.requests.none { it.url in copies }) queueRequests(sheet.requests, sheet.startAt)
    }

    /** The link open - what its sheet's check is of. */
    val openLink: String? get() = link

    private val _check = MutableStateFlow<LibraryCheck?>(null)

    /** What the library holds of the link open, as it is looked for - null with no link read. See [LibraryCheck]. */
    val libraryCheck: StateFlow<LibraryCheck?> = _check.asStateFlow()

    private val _downloadsOnceChecked = MutableStateFlow(false)

    /** Whether what was asked downloads as soon as the check is over - see [onDownloadOnceChecked]. */
    val downloadsOnceChecked: StateFlow<Boolean> = _downloadsOnceChecked.asStateFlow()

    private var checkJob: Job? = null

    /**
     * Looks through the library for [url], read as [info] - a video, or each of a playlist's - as every kind it may be downloaded
     * as, saying each copy as it is found: first what it surely is, from what is read at once; then, once its song is found, the
     * song it is. So switching kind answers at once, and nothing waits on the slowest of it.
     */
    private fun startCheck(url: String, info: MediaInfo) {
        checkJob?.cancel()
        _check.value = LibraryCheck()
        checkJob = viewModelScope.launch {
            suspend fun find(kind: DownloadKind, queries: List<CopyQuery>) = libraryCopies.find(queries) { found, copies -> _check.update { it?.with(kind, found, copies) } }
            val entries = info.entries
            DownloadKind.entries.filter(DownloadKind::isMedia).forEach { kind ->
                find(kind, entries?.map { CopyQuery(it.url, it.id, kind, it.title, it.title, it.uploader, it.durationSeconds) } ?: listOf(copyQueryOf(url, info, kind, song = null)))
            }
            if (entries == null) _song.value?.awaitFound()?.let { song -> find(DownloadKind.AUDIO, listOf(copyQueryOf(url, info, DownloadKind.AUDIO, song))) }
            _check.update { it?.copy(isDone = true) }
            (_linkSheet.value as? LinkSheet.AlreadySaved)?.let(::settleOnceChecked)
        }
    }

    /** What the library is searched for, for [url] read as [info], downloaded as [kind] - as [song], where it is found as one. */
    private fun copyQueryOf(url: String, info: MediaInfo?, kind: DownloadKind, song: SongSummary?): CopyQuery = CopyQuery(
        url, info?.id ?: videoIdOf(url), kind, info?.title,
        song?.title ?: info?.track ?: info?.title, song?.artist ?: info?.artist ?: info?.uploader, info?.durationSeconds,
    )

    /** Queues [requests], to start at [startAt], and says so. */
    private fun queueRequests(requests: List<DownloadRequest>, startAt: Long?) {
        queue.add(requests, startAt)
        context.toast(
            if (requests.size == 1) {
                context.getString(R.string.download_queued_one, requests.single().title ?: requests.single().url)
            } else {
                context.resources.getQuantityString(R.plurals.download_queued_many, requests.size, requests.size)
            },
        )
        onDismissLink()
    }

    /**
     * Whether [info] downloads as [choices] with the app alone: a song or a video served in one piece - every song of a YouTube
     * playlist's, each read by NewPipe as it downloads.
     */
    private fun savesWithoutDownloader(info: MediaInfo, choices: DownloadChoices): Boolean =
        if (info.isPlaylist) choices.kind == DownloadKind.AUDIO && info.site?.startsWith("Youtube") == true
        else directTransfer.formatFor(info, choices, _options.value) != null

    fun onGetDownloader() = downloader.download()

    /** The sheet's choices as [change] makes them - for this download alone, or, [forEveryDownload], kept as the user's own too. */
    fun onSheetChoicesChange(forEveryDownload: Boolean = false, change: (DownloadChoices) -> DownloadChoices) {
        _sheetChoices.value = change(_sheetChoices.value)
        if (forEveryDownload) remember(change)
    }

    /**
     * The sound in [language] for this download - null for the one recorded. [alwaysOriginal] keeps the one recorded as what
     * every download takes; else this language, where a video has it.
     */
    fun onAudioTrackChange(language: String?, alwaysOriginal: Boolean) {
        _sheetChoices.value = _sheetChoices.value.copy(audioLanguage = language)
        remember { it.copy(audioLanguage = if (alwaysOriginal) null else language) }
    }

    /**
     * The captions [codes] picked for this download - none while empty - saved as files beside it where [asFile]. Whether to
     * take captions, and how, is kept for every download.
     */
    fun onSubtitlesChange(codes: List<String>, asFile: Boolean) {
        _options.value = _options.value.copy(subtitleLanguages = codes)
        // Picked to save alone, they say nothing of how a video is downloaded.
        if (_sheetChoices.value.kind == DownloadKind.VIDEO) {
            onSheetChoicesChange(forEveryDownload = true) { it.copy(captions = codes.isNotEmpty(), subtitlesAsFile = asFile) }
        }
    }

    /** [kind] saved in the folder of Download [name] says - this download, and every one after it. */
    fun onFolderChange(kind: DownloadKind, name: String) {
        viewModelScope.launch { settings.updatePreferences { it.withFolder(kind, name) } }
    }

    /** Downloads waiting for Wi-Fi, or any network that is not metered - this one, and every one after it. */
    fun onWifiOnlyChange(wifiOnly: Boolean) {
        viewModelScope.launch { settings.updatePreferences { it.copy(wifiOnly = wifiOnly) } }
    }

    /** Keeps the user's own choices as [change] makes them. */
    private fun remember(change: (DownloadChoices) -> DownloadChoices) {
        viewModelScope.launch { settings.setChoices(change(settings.choices.value)) }
    }

    fun onPickFormat(format: MediaFormat?) {
        _pickedFormat.value = format
    }

    fun onClipChange(clip: ClipRange?) {
        _options.value = _options.value.copy(clip = clip)
    }

    fun onSplitChaptersChange(splits: Boolean) {
        _options.value = _options.value.copy(splitChapters = splits)
    }

    fun onCommandChange(command: DownloadCommand?) {
        _options.value = _options.value.copy(commandArguments = command?.arguments)
    }

    fun onStartAtChange(startAt: Long?) {
        _startAt.value = startAt
    }

    private fun resetOptions() {
        _song.value?.close()
        songLookedFor = null
        _song.value = null
        _sheetChoices.value = settings.choices.value
        _options.value = DownloadOptions(commandArguments = link?.let { commands.value.preferredFor(it)?.arguments })
        _pickedFormat.value = null
        _startAt.value = null
    }

    fun onQueryChange(query: String?) {
        _query.value = query
    }

    /** The history narrowed to [filter] - or, picked again, widened back to every download. */
    fun onFilterChange(filter: HistoryFilter) {
        _filter.value = filter.takeUnless { it == _filter.value }
    }

    /** Takes everything over off the history - their files stay where they were saved. */
    suspend fun onClearHistory() = queue.clearHistory()

    fun onCancel(ids: Collection<Long>) = queue.cancel(ids)

    fun onPause(ids: Collection<Long>) = queue.pause(ids)

    /** Downloads [ids] again, though they are in the library already. */
    fun onDownloadAnyway(ids: Collection<Long>) = queue.retryAnyway(ids)

    /** How far each paused download had got, by id. */
    val held: StateFlow<Map<Long, HeldProgress>> = queue.held

    fun onResume(ids: Collection<Long>) = queue.resume(ids)

    fun onRetry(download: Download) = queue.retry(listOf(download.id))

    fun onRetryAtBestFormat(download: Download) = queue.retryAtBestFormat(download.id)

    /** Downloads [downloadsToRepeat] again: a finished one as a new download, its record kept; one that failed, as itself. */
    fun onDownloadAgain(downloadsToRepeat: List<Download>) {
        val (saved, unsaved) = downloadsToRepeat.partition { it.status == DownloadStatus.DONE }
        queue.add(saved.map { DownloadRequest(it.url, it.title, it.thumbnailUrl, it.uploader, it.choices, it.options) })
        unsaved.filter { it.status.isFinished }.takeIf { it.isNotEmpty() }?.let { queue.retry(it.map(Download::id)) }
    }

    /** Stops [ids] where they run, then deletes their files and takes them off the list. Whether every file went. */
    suspend fun onDelete(ids: Collection<Long>): Boolean {
        queue.cancel(ids)
        return queue.deleteWithFiles(ids)
    }

    /** The library's tracks [download] saved, in the order it saved them - none until the library has read them. */
    fun tracksOf(download: Download): List<Track> {
        val byPath = (libraryRepository.tracks.value.itemsOrEmpty + libraryRepository.videos.value.itemsOrEmpty).associateBy { it.path }
        return download.savedPaths.mapNotNull(byPath::get)
    }

    /** Plays every file [downloads] saved, in their order - a batch's, as one list. */
    fun onPlayAll(downloads: List<Download>) {
        val tracks = downloads.flatMap(::tracksOf)
        if (tracks.isEmpty()) context.toast(R.string.download_not_in_library) else playbackController.playTracks(tracks, startIndex = 0)
    }

    /** Plays [download] as it downloads - played on from the file it saves. */
    fun onPlayDownloading(download: Download) =
        playbackController.playDownloading(download.id, download.title ?: hostOf(download.url), isVideo = download.choices.kind == DownloadKind.VIDEO)

    /**
     * Plays what [download] saved, as the library plays its own - or says it is not in the library yet. Captions alone are
     * opened instead, in an app that reads them.
     */
    fun onPlay(download: Download) {
        if (!download.choices.kind.isMedia) return openSaved(download)
        val tracks = tracksOf(download)
        if (tracks.isEmpty()) context.toast(R.string.download_not_in_library) else playbackController.playTracks(tracks, startIndex = 0)
    }

    /** Opens the first file [download] saved in an app the user picks - by the address Android indexed it at, which it shares. */
    private fun openSaved(download: Download) {
        val path = download.savedPaths.firstOrNull() ?: return
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri ->
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri ?: return@scanFile, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** When it ended - or, for one that never started, when it was asked for. */
private val Download.endedAt: Long get() = finishedAt ?: createdAt

private fun Download.matches(query: String): Boolean =
    listOfNotNull(title, uploader, site, url).any { it.contains(query, ignoreCase = true) }

/** The site a link is on, as the user knows it - "vimeo.com". */
internal fun hostOf(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url

/** What picks this format: a picture alone given the best sound to go with it - the sound first, see YtDlpArguments. */
private val MediaFormat.selector: String get() = if (hasVideo && !hasAudio) "ba+$id/$id" else id
