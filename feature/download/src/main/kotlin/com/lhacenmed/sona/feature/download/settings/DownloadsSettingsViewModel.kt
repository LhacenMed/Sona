package com.lhacenmed.sona.feature.download.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.KeyCheck
import com.lhacenmed.sona.core.downloader.ServiceUsage
import com.lhacenmed.sona.core.downloader.TranscriptionUsage
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.TranscriptionService
import com.lhacenmed.sona.feature.download.DownloadQueue
import com.lhacenmed.sona.feature.download.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What every page of the download settings works with: the downloader it downloads and removes, the yt-dlp it brings up to
 * date, the archive it edits, and how downloading works.
 */
@HiltViewModel
class DownloadsSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: DownloaderRuntime,
    private val ytDlp: YtDlp,
    private val settings: DownloadSettings,
    private val queue: DownloadQueue,
    private val transcriptionUsage: TranscriptionUsage,
) : ViewModel() {

    val downloaderState: StateFlow<RuntimeState> = downloader.state

    val preferences: StateFlow<DownloaderPreferences> = settings.preferences.stateIn(viewModelScope)

    /** The choices every download starts from, as its options sheet has them - SponsorBlock's on or off among them. */
    val choices: StateFlow<DownloadChoices> = settings.choices.stateIn(viewModelScope)

    /**
     * Whether the Supadata key given works, and its credits - checked as soon as a key is given or changed, and each time the
     * page is opened. Null with no key.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val supadataKeyCheck: StateFlow<KeyCheck?> = settings.preferences.flow
        .map { it.supadataKey }
        .distinctUntilChanged()
        .transformLatest { key ->
            if (key.isBlank()) return@transformLatest emit(null)
            emit(KeyCheck.Checking)
            emit(transcriptionUsage.check(TranscriptionService.SUPADATA, key))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether [service] takes [key] - asked as a key is saved, so one that does not work is never kept. */
    suspend fun checkKey(service: TranscriptionService, key: String): KeyCheck = transcriptionUsage.check(service, key)

    /** Groq's requests left today, as its last answer said - a transcription's, or a key's check. */
    val groqRequests: StateFlow<ServiceUsage.DailyRequests?> = transcriptionUsage.groqRequests

    private val _ytDlpVersion = MutableStateFlow<String?>(null)

    /** The version of yt-dlp in use - null while the downloader is not here, or until it has been read. */
    val ytDlpVersion: StateFlow<String?> = _ytDlpVersion.asStateFlow()

    private val _isUpdatingYtDlp = MutableStateFlow(false)
    val isUpdatingYtDlp: StateFlow<Boolean> = _isUpdatingYtDlp.asStateFlow()

    init {
        viewModelScope.launch {
            downloader.state.collectLatest { state ->
                _ytDlpVersion.value = if (state == RuntimeState.Installed) ytDlp.version() else null
            }
        }
    }

    fun downloadDownloader() = downloader.download()
    fun cancelDownloader() = downloader.cancel()
    fun removeDownloader() = downloader.remove()

    /** Brings yt-dlp up to date on the channel chosen, saying - as Seal does - what it is now, or that it could not. */
    fun updateYtDlp() {
        if (_isUpdatingYtDlp.value) return
        _isUpdatingYtDlp.value = true
        viewModelScope.launch {
            runCatching { ytDlp.update(preferences.value.ytDlpChannel) }
                .onSuccess { version ->
                    _ytDlpVersion.value = version
                    context.toast(context.getString(R.string.downloads_settings_ytdlp_up_to_date, version))
                }
                .onFailure { context.toast(R.string.downloads_settings_ytdlp_update_failed) }
            _isUpdatingYtDlp.value = false
        }
    }

    fun updatePreferences(change: (DownloaderPreferences) -> DownloaderPreferences) {
        viewModelScope.launch { settings.updatePreferences(change) }
    }

    /** Changes the choices every download starts from as [change] makes them - SponsorBlock, subtitles - see [choices]. */
    fun updateChoices(change: (DownloadChoices) -> DownloadChoices) {
        viewModelScope.launch { settings.setChoices(change(settings.choices.value)) }
    }

    /** Empties the Downloads list of everything over - their files stay - then says so to [onCleared]. */
    fun clearHistory(onCleared: () -> Unit) {
        viewModelScope.launch {
            queue.clearHistory()
            onCleared()
        }
    }

    /** What the archive holds, as yt-dlp wrote it - a site and a video's id to a line - handed to [onRead]. */
    fun readArchive(onRead: (String) -> Unit) {
        viewModelScope.launch {
            onRead(withContext(Dispatchers.IO) { downloader.archiveFile.takeIf { it.exists() }?.readText().orEmpty() })
        }
    }

    /** Writes [content] as the archive, in place of what it held: a line taken out lets its video be downloaded again. */
    fun writeArchive(content: String) {
        viewModelScope.launch(Dispatchers.IO) { downloader.archiveFile.writeText(content) }
    }
}
