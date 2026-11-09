package com.lhacenmed.sona.feature.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.YtDlp
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadChoices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the link typed or shared leads to, as it is looked up. */
sealed interface LinkLookup {
    data object None : LinkLookup
    data object Looking : LinkLookup
    data class Found(val info: MediaInfo) : LinkLookup
    data class Failed(val message: String) : LinkLookup
}

/** How long typing has to pause for the link to be looked up. */
private const val LookUpDelayMs = 600L

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
    private val settings: DownloadSettings,
    private val downloader: DownloaderRuntime,
    private val ytDlp: YtDlp,
) : ViewModel() {

    val downloads: StateFlow<List<Download>> = queue.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val running: StateFlow<RunningDownload?> = queue.running

    /** How the user downloads - every change kept, for the next download and a Quick download alike. */
    val choices: StateFlow<DownloadChoices> = settings.choices.stateIn(viewModelScope)

    /** Whether the downloader is here - what downloading, and looking a link up, wait on. */
    val downloaderState: StateFlow<RuntimeState> = downloader.state

    private val _link = MutableStateFlow("")
    val link: StateFlow<String> = _link.asStateFlow()

    private val _lookup = MutableStateFlow<LinkLookup>(LinkLookup.None)
    val lookup: StateFlow<LinkLookup> = _lookup.asStateFlow()

    private var lookUpJob: Job? = null

    init {
        // Downloaded while a link waits, the link is looked up at once.
        viewModelScope.launch {
            downloader.state.drop(1).filter { it == RuntimeState.Installed }.collect { onLinkChange(_link.value) }
        }
    }

    fun onDownloadDownloader() = downloader.download()

    fun onCancelDownloader() = downloader.cancel()

    /** The link as typed, pasted or shared - looked up once typing pauses on one. */
    fun onLinkChange(text: String) {
        _link.value = text
        lookUpJob?.cancel()
        val url = linkIn(text)
        if (url == null || downloader.state.value != RuntimeState.Installed) {
            _lookup.value = LinkLookup.None
            return
        }
        lookUpJob = viewModelScope.launch {
            delay(LookUpDelayMs)
            _lookup.value = LinkLookup.Looking
            _lookup.value = try {
                LinkLookup.Found(ytDlp.info(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LinkLookup.Failed(e.message ?: context.getString(R.string.download_failed))
            }
        }
    }

    fun onChoicesChange(choices: DownloadChoices) {
        viewModelScope.launch { settings.setChoices(choices) }
    }

    /** Queues the link with the choices as they stand, and clears it for the next. */
    fun onDownload() {
        val url = linkIn(_link.value) ?: return
        queue.add(url, choices.value, (_lookup.value as? LinkLookup.Found)?.info?.title)
        lookUpJob?.cancel()
        _link.value = ""
        _lookup.value = LinkLookup.None
    }

    fun onCancel(download: Download) = queue.cancel(download)

    fun onRetry(download: Download) = queue.retry(download)

    fun onClearFinished() = queue.clearFinished()
}
