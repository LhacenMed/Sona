package com.lhacenmed.sona.core.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the device can reach the internet - ArchiveTune's `NetworkMonitor`, one for the whole app.
 *
 * Online means some network is validated for internet access, not merely connected: a captive-portal
 * Wi-Fi reads as offline until it is signed into. Every network that carries internet is followed on
 * its own, so losing Wi-Fi while mobile data is still validated never reads as going offline.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivityManager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))

    /**
     * Online now, and every change after - followed for as long as the app runs, so its value is always
     * the current answer and every collector shares the one callback.
     */
    val isOnline: StateFlow<Boolean> = validatedNetworks()
        .stateIn(scope, SharingStarted.Eagerly, context.isNetworkOnline())

    /** What the banner over every screen says of [isOnline] - see [asNetworkBanner]. */
    val banner: StateFlow<NetworkBanner> = isOnline.asNetworkBanner()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), NetworkBanner.Hidden)

    private fun validatedNetworks(): Flow<Boolean> = callbackFlow {
        val validated = mutableSetOf<Network>()
        connectivityManager.activeNetwork
            ?.takeIf { connectivityManager.getNetworkCapabilities(it).isValidatedInternet() }
            ?.let(validated::add)
        trySend(validated.isNotEmpty())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.isValidatedInternet()) validated += network else validated -= network
                trySend(validated.isNotEmpty())
            }

            override fun onLost(network: Network) {
                validated -= network
                trySend(validated.isNotEmpty())
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}

/** Whether a validated network is up at this moment - for a one-off question asked outside any flow. */
fun Context.isNetworkOnline(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
    return connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork).isValidatedInternet()
}

private fun NetworkCapabilities?.isValidatedInternet(): Boolean =
    this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
