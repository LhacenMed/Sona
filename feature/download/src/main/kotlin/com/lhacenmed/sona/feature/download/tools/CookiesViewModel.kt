package com.lhacenmed.sona.feature.download.tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.downloader.BrowserCookies
import com.lhacenmed.sona.core.downloader.CookieSite
import com.lhacenmed.sona.core.model.DownloaderPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The browser's cookies, as the cookie settings list, remove, clear and export them - see [BrowserCookies]. */
@HiltViewModel
internal class CookiesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cookies: BrowserCookies,
    private val settings: DownloadSettings,
) : ViewModel() {

    /** Every site the browser holds cookies for. */
    val sites: StateFlow<List<CookieSite>> = cookies.sites

    val preferences: StateFlow<DownloaderPreferences> = settings.preferences.stateIn(viewModelScope)

    /** Forgets [site]'s cookies. */
    fun remove(site: CookieSite) {
        viewModelScope.launch { cookies.remove(site) }
    }

    /** Forgets every site's cookies. */
    fun clear() {
        viewModelScope.launch { cookies.clear() }
    }

    /** Writes the cookies to [uri], saying whether it could to [onExported]. */
    fun export(uri: Uri, onExported: (Boolean) -> Unit) {
        viewModelScope.launch {
            onExported(runCatching { context.contentResolver.openOutputStream(uri)?.use { cookies.export(it) } != null }.getOrDefault(false))
        }
    }

    fun updatePreferences(change: (DownloaderPreferences) -> DownloaderPreferences) {
        viewModelScope.launch { settings.updatePreferences(change) }
    }
}
