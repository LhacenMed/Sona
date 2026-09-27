package com.lhacenmed.sona.feature.settings.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.feature.settings.remote.RemoteList
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Every release on the chosen channel, newest first, with its notes - see [RemoteList]. */
@HiltViewModel
class ChangelogViewModel @Inject constructor(
    @ApplicationContext context: Context,
    updateSettings: UpdateSettings,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val channel = updateSettings.channel.value

    val releases: RemoteList<Release> = RemoteList(
        scope = viewModelScope,
        networkMonitor = networkMonitor,
        cached = { Releases.cached(context, channel).ifEmpty { null } },
        fetch = { force -> Releases.all(context, channel, force) },
    )
}
