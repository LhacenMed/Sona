package com.lhacenmed.sona.feature.download.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.feature.download.DownloadQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The downloader the [DownloadsSettingsScreen] downloads and removes, the yt-dlp it brings up to date, and how downloading works. */
@HiltViewModel
class DownloadsSettingsViewModel @Inject constructor(
    private val downloader: DownloaderRuntime,
    private val ytDlp: YtDlp,
    private val settings: DownloadSettings,
    private val queue: DownloadQueue,
) : ViewModel() {

    val downloaderState: StateFlow<RuntimeState> = downloader.state

    val preferences: StateFlow<DownloaderPreferences> = settings.preferences.stateIn(viewModelScope)

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

    fun updateYtDlp() {
        if (_isUpdatingYtDlp.value) return
        _isUpdatingYtDlp.value = true
        viewModelScope.launch {
            runCatching { ytDlp.update() }.onSuccess { _ytDlpVersion.value = it }
            _isUpdatingYtDlp.value = false
        }
    }

    fun updatePreferences(change: (DownloaderPreferences) -> DownloaderPreferences) {
        viewModelScope.launch { settings.updatePreferences(change) }
    }

    /** Empties the Downloads list of everything over - their files stay - then says so to [onCleared]. */
    fun clearHistory(onCleared: () -> Unit) {
        viewModelScope.launch {
            queue.clearHistory()
            onCleared()
        }
    }

    /** Forgets every video downloaded while the archive was on, so each can be downloaded again. */
    fun clearArchive() {
        viewModelScope.launch(Dispatchers.IO) { downloader.archiveFile.delete() }
    }
}
