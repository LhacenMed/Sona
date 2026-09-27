package com.lhacenmed.sona.feature.settings.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.feature.settings.remote.RemoteList
import com.lhacenmed.sona.feature.update.github.Commit
import com.lhacenmed.sona.feature.update.github.Commits
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The development branch's recent commits - see [RemoteList]. */
@HiltViewModel
class CommitsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val commits: RemoteList<Commit> = RemoteList(
        scope = viewModelScope,
        networkMonitor = networkMonitor,
        cached = { Commits.cached(context) },
        fetch = { force -> Commits.recent(context, force) },
    )
}
