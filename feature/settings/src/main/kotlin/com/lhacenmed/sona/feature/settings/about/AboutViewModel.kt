package com.lhacenmed.sona.feature.settings.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.feature.settings.remote.RemoteList
import com.lhacenmed.sona.feature.update.github.Contributor
import com.lhacenmed.sona.feature.update.github.Contributors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** About's state - ArchiveTune's `AboutViewModel`: the repository's contributors - see [RemoteList]. */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext context: Context,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val contributors: RemoteList<Contributor> = RemoteList(
        scope = viewModelScope,
        networkMonitor = networkMonitor,
        cached = { Contributors.cached(context) },
        fetch = { force -> Contributors.all(context, force) },
    )
}
