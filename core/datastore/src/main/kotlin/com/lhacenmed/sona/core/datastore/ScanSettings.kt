package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scanDataStore by preferencesDataStore(name = "scan_state")

private val LAST_SCAN_SIGNATURE = stringPreferencesKey("last_scan_signature")

/**
 * Remembers what the device's media library looked like the last time a scan completed.
 *
 * Kept in its own DataStore file rather than in `LibrarySettings`: this is scanner bookkeeping, not
 * a user preference, and it is written on a different cadence than anything the user changes. A
 * separate file also means a signature write cannot wake up observers of the settings file.
 */
@Singleton
class ScanSettings @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.scanDataStore

    /** Opaque signature of the last completed scan - see the scanner's `ScanSignature`. */
    val lastScanSignature: Flow<String?> =
        dataStore.data.map { it[LAST_SCAN_SIGNATURE] }

    suspend fun setLastScanSignature(signature: String) {
        dataStore.edit { it[LAST_SCAN_SIGNATURE] = signature }
    }

    /** Forces the next scan to run in full - used when the user asks for a manual rescan. */
    suspend fun clearLastScanSignature() {
        dataStore.edit { it.remove(LAST_SCAN_SIGNATURE) }
    }
}
