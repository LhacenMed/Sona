package com.lhacenmed.sona.feature.download

import android.content.Context
import android.net.Uri
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
import com.lhacenmed.sona.core.downloader.DownloaderMissingException
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.LinkPreview
import com.lhacenmed.sona.core.downloader.LinkPreviews
import com.lhacenmed.sona.core.downloader.MediaFormat
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.MediaResolver
import com.lhacenmed.sona.core.downloader.PlaylistEntry
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.failureOf
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.ClipRange
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadCommand
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the link sheet shows of the link given - null while there is none. */
sealed interface LinkSheet {
    /** Being read - shown meanwhile as [preview] has it, where the link is one shown before it is read. */
    data class Reading(val preview: LinkPreview?) : LinkSheet

    data class Found(val info: MediaInfo) : LinkSheet

    /** [url] could not be read - by the app, or by yt-dlp, which is here - for [failure], [detail] in yt-dlp's own words. */
    data class Unreadable(val url: String, val failure: DownloadFailure, val detail: String?) : LinkSheet

    /**
     * Downloading it as [kind] needs the downloader, which is not here - [info] the link as read, where the app read it itself;
     * null where only yt-dlp reads its site. [savesAsAudio] where its sound alone downloads without it.
     */
    data class NeedsDownloader(val url: String, val info: MediaInfo?, val kind: DownloadKind, val savesAsAudio: Boolean = false) : LinkSheet
}

/** Every download, as the list sections it: what is still to download, then what was finished today, then before. */
data class DownloadSections(val downloading: List<Download>, val today: List<Download>, val earlier: List<Download>) {
    val isEmpty: Boolean get() = downloading.isEmpty() && today.isEmpty() && earlier.isEmpty()
    val all: List<Download> get() = downloading + today + earlier
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
    dao: DownloadDao,
) : ViewModel() {

    val running: StateFlow<Map<Long, RunningDownload>> = queue.running

    val preferences: StateFlow<DownloaderPreferences> = settings.preferences.stateIn(viewModelScope)

    private val _query = MutableStateFlow<String?>(null)

    /** What the downloads are searched for - null while they are not. */
    val query: StateFlow<String?> = _query.asStateFlow()

    private val downloads = queue.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every download as the list shows it - null until it has been read, so an empty list is never shown for a moment. */
    val sections: StateFlow<DownloadSections?> = combine(downloads, _query) { all, query ->
        all ?: return@combine null
        val matching = if (query.isNullOrBlank()) all else all.filter { it.matches(query.trim()) }
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

    private val _options = MutableStateFlow(DownloadOptions())

    /** What the next download asks beyond the choices - for it alone, let go once it is queued. */
    val options: StateFlow<DownloadOptions> = _options.asStateFlow()

    private val _pickedFormat = MutableStateFlow<MediaFormat?>(null)

    /** The link's own format picked for the next download - for the kind it is, picture or sound. */
    val pickedFormat: StateFlow<MediaFormat?> = _pickedFormat.asStateFlow()

    private val _startAt = MutableStateFlow<Long?>(null)

    /** When the next download is to start - null for as soon as it can. */
    val startAt: StateFlow<Long?> = _startAt.asStateFlow()

    val commands: StateFlow<List<DownloadCommand>> = dao.observeCommands().map { rows -> rows.map(DownloadCommandEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloaderState: StateFlow<RuntimeState> = downloader.state

    private val _linkEntry = MutableStateFlow<String?>(null)

    /** The link being typed in the bar - null while none is. */
    val linkEntry: StateFlow<String?> = _linkEntry.asStateFlow()

    private val _linkSheet = MutableStateFlow<LinkSheet?>(null)
    val linkSheet: StateFlow<LinkSheet?> = _linkSheet.asStateFlow()

    private var link: String? = null
    private var readJob: Job? = null

    init {
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
        _linkEntry.value = null
        resetOptions()
        readJob?.cancel()
        resolver.cached(url)?.let {
            _linkSheet.value = LinkSheet.Found(it.info)
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
        }
    }

    /** What the clipboard holds, pasted: its link opened - or, with none, the bar ready to have one typed. */
    fun onPaste(clipboard: String?) {
        linkIn(clipboard)?.let(::open) ?: run { _linkEntry.value = "" }
    }

    /** The link as typed in the bar - opened as soon as it is one. */
    fun onLinkEntryChange(text: String) {
        _linkEntry.value = text
        linkIn(text)?.let(::open)
    }

    fun onLinkEntryClose() {
        _linkEntry.value = null
    }

    fun onDismissLink() {
        readJob?.cancel()
        _linkSheet.value = null
        link = null
        resetOptions()
    }

    /**
     * Downloads the link - or the playlist's [entries] picked - as [kind], kept as the kind to start from next time. Where it
     * needs the downloader and the downloader is not here, the sheet offers it instead.
     */
    fun onDownload(kind: DownloadKind, entries: List<PlaylistEntry>? = null) {
        val url = link ?: return
        val info = (_linkSheet.value as? LinkSheet.Found)?.info ?: (_linkSheet.value as? LinkSheet.NeedsDownloader)?.info
        val chosen = choices.value.copy(kind = kind)
        if (chosen != choices.value) viewModelScope.launch { settings.setChoices(chosen) }
        if (downloader.state.value != RuntimeState.Installed && !(info != null && savesWithoutDownloader(info, chosen))) {
            val savesAsAudio = info != null && kind == DownloadKind.VIDEO && savesWithoutDownloader(info, chosen.copy(kind = DownloadKind.AUDIO))
            _linkSheet.value = LinkSheet.NeedsDownloader(url, info, kind, savesAsAudio)
            return
        }
        val options = _options.value.copy(formatId = _pickedFormat.value?.takeIf { it.hasVideo == (kind == DownloadKind.VIDEO) }?.selector)
        val requests = entries?.map { DownloadRequest(it.url, it.title, it.thumbnailUrl, it.uploader, chosen, options.copy(formatId = null)) }
            ?: listOf(DownloadRequest(url, info?.title, info?.thumbnailUrl, info?.uploader, chosen, options))
        queue.add(requests, _startAt.value)
        context.toast(
            if (requests.size == 1) {
                context.getString(R.string.download_queued_one, requests.single().title ?: url)
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

    fun onChoicesChange(choices: DownloadChoices) {
        viewModelScope.launch { settings.setChoices(choices) }
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
        _options.value = DownloadOptions()
        _pickedFormat.value = null
        _startAt.value = null
    }

    fun onQueryChange(query: String?) {
        _query.value = query
    }

    fun onCancel(ids: Collection<Long>) = queue.cancel(ids)

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

    /** Plays what [download] saved, as the library plays its own - or says it is not in the library yet. */
    fun onPlay(download: Download) {
        val tracks = tracksOf(download)
        if (tracks.isEmpty()) context.toast(R.string.download_not_in_library) else playbackController.playTracks(tracks, startIndex = 0)
    }
}

/** When it ended - or, for one that never started, when it was asked for. */
private val Download.endedAt: Long get() = finishedAt ?: createdAt

private fun Download.matches(query: String): Boolean =
    listOfNotNull(title, uploader, site, url).any { it.contains(query, ignoreCase = true) }

/** The site a link is on, as the user knows it - "vimeo.com". */
internal fun hostOf(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url

/** What picks this format: a picture alone given the best sound to go with it. */
private val MediaFormat.selector: String get() = if (hasVideo && !hasAudio) "$id+ba/$id" else id
