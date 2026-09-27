package com.lhacenmed.sona.core.common.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** What the connection banner shows - ArchiveTune's `NetworkBannerUiState`. */
sealed interface NetworkBanner {
    data object Hidden : NetworkBanner

    data object Offline : NetworkBanner

    data object BackOnline : NetworkBanner
}

/** How long going offline must last before it is said, so a blip between networks never is. */
private const val OfflineDebounceMillis = 750L
private const val OfflineShownMillis = 3_000L
private const val BackOnlineShownMillis = 2_500L

/**
 * The banner for a connection going up and down - ArchiveTune's `asNetworkBannerUiState`: going offline is
 * said once it has lasted a moment, then the banner goes; coming back is said only after going offline was.
 * Starting online says nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<Boolean>.asNetworkBanner(): Flow<NetworkBanner> {
    var hasShownOffline = false
    return distinctUntilChanged().transformLatest { isOnline ->
        when {
            !isOnline -> {
                delay(OfflineDebounceMillis)
                hasShownOffline = true
                emit(NetworkBanner.Offline)
                delay(OfflineShownMillis)
                emit(NetworkBanner.Hidden)
            }
            hasShownOffline -> {
                hasShownOffline = false
                emit(NetworkBanner.BackOnline)
                delay(BackOnlineShownMillis)
                emit(NetworkBanner.Hidden)
            }
            else -> emit(NetworkBanner.Hidden)
        }
    }
}
