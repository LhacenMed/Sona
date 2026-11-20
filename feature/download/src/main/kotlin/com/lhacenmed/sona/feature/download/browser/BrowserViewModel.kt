package com.lhacenmed.sona.feature.download.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.model.BrowserBookmark
import com.lhacenmed.sona.core.model.SearchEngine
import com.lhacenmed.sona.core.model.ShieldsPreferences
import com.lhacenmed.sona.feature.download.browser.shields.FilterList
import com.lhacenmed.sona.feature.download.browser.shields.Shields
import com.lhacenmed.sona.core.downloader.BrowserCookies
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.feature.download.DownloadQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The browser's bookmarks, its search engine and its Shields - kept with the download settings, as the browser is the downloads'. */
@HiltViewModel
internal class BrowserViewModel @Inject constructor(
    private val settings: DownloadSettings,
    val shields: Shields,
    val cookies: BrowserCookies,
    private val queue: DownloadQueue,
) : ViewModel() {

    /** The browser left: every download that failed for want of signing in tries again, signed in as the browser now is. */
    fun onLeft() = queue.retryFailed(DownloadFailure.LOGIN_REQUIRED)

    val bookmarks: StateFlow<List<BrowserBookmark>> = settings.browserBookmarks.stateIn(viewModelScope)

    fun isBookmarked(url: String): Boolean = bookmarks.value.any { it.url == url }

    /** Keeps [bookmark] - in [replacing]'s place where it edits one, else after the rest. */
    fun save(bookmark: BrowserBookmark, replacing: BrowserBookmark?) = update { bookmarks ->
        if (replacing != null) bookmarks.map { if (it == replacing) bookmark else it } else bookmarks + bookmark
    }

    fun remove(url: String) = update { bookmarks -> bookmarks.filterNot { it.url == url } }

    val searchEngine: StateFlow<SearchEngine> = settings.browserSearchEngine.stateIn(viewModelScope)

    fun onSearchEngineChange(engine: SearchEngine) {
        viewModelScope.launch { settings.setBrowserSearchEngine(engine) }
    }

    val shieldsPreferences: StateFlow<ShieldsPreferences> = settings.browserShields.stateIn(viewModelScope)

    /** Brave's filter lists - read from the device for the settings, where no browser fetched them yet. */
    val filterLists: StateFlow<List<FilterList>> = shields.lists.catalog.also { shields.lists.readCatalog() }

    private fun update(change: (List<BrowserBookmark>) -> List<BrowserBookmark>) {
        viewModelScope.launch { settings.setBrowserBookmarks(change(settings.browserBookmarks.value)) }
    }
}
