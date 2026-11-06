package com.lhacenmed.sona.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.search.searchFormOf
import com.lhacenmed.sona.core.datastore.SettingsSearchHistory
import com.lhacenmed.sona.core.navigation.DebugTools
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many past searches are kept: the latest, so what was searched for lately stays in reach. */
private const val MAX_RECENT_SEARCHES = 10

/** What the settings home offers beyond its categories: its search's history, and the debug tools in a build that has them. */
@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    debugTools: Optional<DebugTools>,
    private val searchHistory: SettingsSearchHistory,
) : ViewModel() {
    val debugTools: DebugTools? = debugTools.orElse(null)

    /** What the settings were last searched for, the latest first. */
    val recentSearches: StateFlow<List<String>> = searchHistory.queries.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), searchHistory.queries.value)

    /**
     * Keeps [query] as the latest search - once a result of it is opened, so only searches that found something are
     * kept. One read the same way as another - "Crossfade", "crossfade " - is the one search, kept as last typed.
     */
    fun recordSearch(query: String) {
        val searched = searchFormOf(query)
        if (searched.isEmpty()) return
        val searches = listOf(query.trim()) + recentSearches.value.filterNot { searchFormOf(it) == searched }
        viewModelScope.launch { searchHistory.setQueries(searches.take(MAX_RECENT_SEARCHES)) }
    }

    fun forgetSearch(query: String) {
        viewModelScope.launch { searchHistory.setQueries(recentSearches.value - query) }
    }

    fun clearSearches() {
        viewModelScope.launch { searchHistory.setQueries(emptyList()) }
    }
}
