package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.YtDlp
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The downloader the [DownloadsSettingsScreen] downloads and removes, and the yt-dlp it brings up to date. */
@HiltViewModel
class DownloadsSettingsViewModel @Inject constructor(
    private val downloader: DownloaderRuntime,
    private val ytDlp: YtDlp,
) : ViewModel() {

    val downloaderState: StateFlow<RuntimeState> = downloader.state

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
}
