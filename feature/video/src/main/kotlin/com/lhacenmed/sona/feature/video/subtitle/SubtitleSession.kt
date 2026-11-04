package com.lhacenmed.sona.feature.video.subtitle

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.text.Cue
import com.lhacenmed.sona.core.datastore.VideoSettings
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.VideoSubtitle
import com.lhacenmed.sona.core.model.VideoSubtitleStore
import com.lhacenmed.sona.feature.playback.MediaTrack
import com.lhacenmed.sona.feature.playback.PlaybackController
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The video being watched, the [store] its space keeps its subtitles in, and whether files beside it are looked for. */
internal data class WatchedVideo(val track: Track, val store: VideoSubtitleStore, val looksBesideIt: Boolean)

/** What the current video could show and shows of subtitles. */
internal data class SubtitleUiState(
    val attached: SubtitleFile? = null,
    val sideFiles: List<SubtitleFile> = emptyList(),
    val tracks: List<MediaTrack> = emptyList(),
    val source: SubtitleSource = SubtitleSource.Off,
    val offsetMs: Long = 0L,
    /** The lines of the file in [source], once read. */
    val timeline: SubtitleTimeline<Cue>? = null,
)

/** What is known of the video playing, read as it started. */
private data class LoadedVideo(
    val video: WatchedVideo,
    val stored: VideoSubtitle,
    val attached: SubtitleFile?,
    val sideFiles: List<SubtitleFile>,
)

/** The lines read from [subtitle]. */
private class ReadSubtitle(val subtitle: SubtitleFile, val timeline: SubtitleTimeline<Cue>?)

/**
 * The subtitles of whichever video is watched: what it has, which of those it shows, and everything the user changes
 * about them - each kept with the video as it is changed.
 *
 * Lines from a file are read here and shown against the player's position, leaving the player out of it - nothing
 * changes in the queue or stops the picture, whether a file is attached, chosen or moved in time. Only the file's own
 * tracks are the player's to play, so it is told to turn those off while a file is showing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SubtitleSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playbackController: PlaybackController,
    private val settings: VideoSettings,
    private val video: Flow<WatchedVideo?>,
) {
    private val loaded = MutableStateFlow<LoadedVideo?>(null)

    private val failures = Channel<Unit>(Channel.CONFLATED)

    /** Each time a subtitle file could not be read. */
    val readFailures: Flow<Unit> = failures.receiveAsFlow()

    /** What the player has for the video [loaded] holds - nothing yet while it is still on the one before. */
    private val tracks = combine(loaded, playbackController.playbackState) { loaded, playback ->
        playback.subtitleTracks.takeIf { loaded != null && playback.currentTrackId == loaded.video.track.id }
    }.distinctUntilChanged()

    private val choices: StateFlow<SubtitleUiState?> = combine(
        loaded,
        tracks,
        settings.subtitlesOn.flow,
        settings.subtitleLanguage.flow,
    ) { loaded, tracks, isOn, language ->
        if (loaded == null || tracks == null) return@combine null
        SubtitleUiState(
            attached = loaded.attached,
            sideFiles = loaded.sideFiles,
            tracks = tracks,
            source = chooseSubtitleSource(loaded.stored.choice, loaded.attached, loaded.sideFiles, tracks, isOn, language),
            offsetMs = loaded.stored.offsetMs,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val read = choices
        .map { (it?.source as? SubtitleSource.FromFile)?.subtitle }
        .distinctUntilChanged()
        .mapLatest { subtitle -> subtitle?.let { ReadSubtitle(it, readTimeline(it)) } }

    val state: StateFlow<SubtitleUiState> = combine(choices, read) { choices, read ->
        val shown = (choices?.source as? SubtitleSource.FromFile)?.subtitle
        // Lines only of the file showing now, never for a moment those of the one before.
        choices?.copy(timeline = read?.takeIf { it.subtitle == shown }?.timeline) ?: SubtitleUiState()
    }.stateIn(scope, SharingStarted.Eagerly, SubtitleUiState())

    init {
        scope.launch {
            video.distinctUntilChanged().collectLatest { watched ->
                loaded.value = null
                if (watched != null) loaded.value = withContext(Dispatchers.IO) { load(watched) }
            }
        }
        // Told once for each thing that decides it. A track selected is the same choice, not a new one to apply again.
        scope.launch {
            choices.filterNotNull()
                .map { it.source to it.tracks.map { track -> track.copy(isSelected = false) } }
                .distinctUntilChanged()
                .collect { (source, _) -> applyToPlayer(source) }
        }
    }

    /** Shows [source] from now on, for this video and - as far as it has one - the ones after it. */
    fun choose(source: SubtitleSource) {
        val current = loaded.value ?: return
        val previousFile = (state.value.source as? SubtitleSource.FromFile)?.subtitle
        val isAnotherFile = source is SubtitleSource.FromFile && previousFile != null && previousFile != source.subtitle
        keep(current) { it.copy(choice = source.storedChoice, offsetMs = if (isAnotherFile) 0L else it.offsetMs) }
        val language = when (source) {
            is SubtitleSource.FromFile -> source.subtitle.language
            is SubtitleSource.Embedded -> source.track.language
            SubtitleSource.Off, SubtitleSource.PlayerDefault -> null
        }
        scope.launch {
            settings.setSubtitlesOn(source != SubtitleSource.Off)
            language?.let { settings.setSubtitleLanguage(it) }
        }
    }

    /** Moves the lines of the file showing to [offsetMs] from where it says they are. */
    fun setOffset(offsetMs: Long) {
        loaded.value?.let { current -> keep(current) { it.copy(offsetMs = offsetMs) } }
    }

    /** Copies the file at [uri] to the video being watched, in place of any before it, and shows it. */
    fun attach(uri: Uri) {
        val current = loaded.value ?: return
        val id = current.video.track.id
        scope.launch {
            val attached = withContext(Dispatchers.IO) { copyIn(uri, current.video) }
            if (attached == null) {
                failures.trySend(Unit)
                return@launch
            }
            loaded.update { it?.takeIf { video -> video.video.track.id == id }?.copy(attached = attached) ?: it }
            keep(current) { VideoSubtitle(attachedName = attached.name, choice = SubtitleSource.FromFile(attached).storedChoice) }
            settings.setSubtitlesOn(true)
        }
    }

    /** Writes [change] down for the video it is about - not any other, should the queue have moved on since. */
    private fun keep(current: LoadedVideo, change: (VideoSubtitle) -> VideoSubtitle) {
        val id = current.video.track.id
        val now = loaded.updateAndGet { loaded ->
            if (loaded?.video?.track?.id == id) loaded.copy(stored = change(loaded.stored)) else loaded
        }
        val stored = now?.takeIf { it.video.track.id == id }?.stored ?: change(current.stored)
        // In the order they were made: the last of a run of changes is the one left stored.
        scope.launch { current.video.store.write(id, stored) }
    }

    private fun applyToPlayer(source: SubtitleSource) {
        when (source) {
            SubtitleSource.PlayerDefault -> playbackController.followDefaultSubtitleTrack()
            is SubtitleSource.Embedded -> playbackController.selectSubtitleTrack(source.track)
            SubtitleSource.Off, is SubtitleSource.FromFile -> playbackController.selectSubtitleTrack(null)
        }
    }

    private suspend fun load(video: WatchedVideo): LoadedVideo {
        val stored = video.store.read(video.track.id)
        return LoadedVideo(
            video = video,
            stored = stored,
            attached = stored.attachedName?.let { attachedFileOf(video, it) },
            sideFiles = if (video.looksBesideIt) sideFilesOf(video.track) else emptyList(),
        )
    }

    /** The subtitles beside [track]'s file - none where the app may not list its folder, without all-files access. */
    private fun sideFilesOf(track: Track): List<SubtitleFile> {
        val videoFile = File(track.path)
        val folder = videoFile.parentFile ?: return emptyList()
        val siblings = runCatching { folder.list() }.getOrNull()?.toList().orEmpty()
        return sideSubtitlesOf(videoFile.name, siblings).map { side ->
            val file = File(folder, side.fileName)
            SubtitleFile(file, side.fileName, side.language, isAttached = false, modifiedAt = file.lastModified())
        }
    }

    private fun attachedFileOf(video: WatchedVideo, name: String): SubtitleFile? {
        val file = video.store.attachedFile(video.track.id, name)
        return SubtitleFile(file, name, language = null, isAttached = true, modifiedAt = file.lastModified()).takeIf { file.isFile }
    }

    private suspend fun readTimeline(subtitle: SubtitleFile): SubtitleTimeline<Cue>? {
        val timeline = withContext(Dispatchers.IO) {
            runCatching { subtitle.file.inputStream().use { it.readSubtitleBytes() } }
                .getOrNull()
                ?.let { parseSubtitle(it, subtitle.file.extension) }
        }
        if (timeline == null) failures.trySend(Unit)
        return timeline
    }

    /**
     * The file at [uri], its copy kept for [video] - or null where it is too large or is not subtitles at all, leaving
     * whatever was attached before as it was.
     */
    private fun copyIn(uri: Uri, video: WatchedVideo): SubtitleFile? = try {
        val name = displayNameOf(uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readSubtitleBytes() }
        if (name == null || bytes == null || parseSubtitle(bytes, name.substringAfterLast('.', "")) == null) {
            null
        } else {
            val target = video.store.attachedFile(video.track.id, name)
            val incoming = File(target.path + ".incoming").apply { writeBytes(bytes) }
            video.store.deleteAttachedFile(video.track.id)
            if (incoming.renameTo(target)) attachedFileOf(video, name) else null.also { incoming.delete() }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun displayNameOf(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
