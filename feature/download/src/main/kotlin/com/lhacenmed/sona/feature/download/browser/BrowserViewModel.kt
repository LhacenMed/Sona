package com.lhacenmed.sona.feature.download.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.model.BrowserBookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The browser's bookmarks - kept with the download settings, as the browser is the downloads'. */
@HiltViewModel
internal class BrowserViewModel @Inject constructor(private val settings: DownloadSettings) : ViewModel() {

    val bookmarks: StateFlow<List<BrowserBookmark>> = settings.browserBookmarks.stateIn(viewModelScope)

    fun isBookmarked(url: String): Boolean = bookmarks.value.any { it.url == url }

    /** Keeps [bookmark] - in [replacing]'s place where it edits one, else after the rest. */
    fun save(bookmark: BrowserBookmark, replacing: BrowserBookmark?) = update { bookmarks ->
        if (replacing != null) bookmarks.map { if (it == replacing) bookmark else it } else bookmarks + bookmark
    }

    fun remove(url: String) = update { bookmarks -> bookmarks.filterNot { it.url == url } }

    private fun update(change: (List<BrowserBookmark>) -> List<BrowserBookmark>) {
        viewModelScope.launch { settings.setBrowserBookmarks(change(settings.browserBookmarks.value)) }
    }
}
