package com.lhacenmed.sona.feature.scanner.mediastore

import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Tells when MediaStore's audio collection changes - a file added, removed or re-tagged in any
 * folder of any volume.
 *
 * The shape of Budget's `StatusFolderObserver`, watching MediaStore instead of directories: inotify
 * sees only the one directory it is given, so following every folder of the library that way would
 * mean a watch per directory on the whole device, re-registered for each new one. MediaStore already
 * indexes every folder and says when its index changes, which is exactly the set of files
 * [MediaStoreQuerier] reads - so one registration covers all of them.
 *
 * Each emission says only that something changed, not what: the library's rows are derived from one
 * another (an album's count, an artist's covers), so a change is applied by re-reading and diffing,
 * never patched in file by file.
 *
 * Lifecycle: cold - the observer registers on collection and unregisters on cancellation.
 */
class MediaStoreChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val changes: Flow<Unit> = callbackFlow {
        // No handler: onChange arrives on a binder thread, and trySend is safe from any thread.
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
