package com.lhacenmed.sona.core.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.lhacenmed.sona.core.model.Track
import java.io.File

/**
 * The one address [this] track's file is opened by - to play it, read or write its tags, share or delete
 * it: its MediaStore entry, in the audio or the video collection, or the file itself for one MediaStore has
 * not indexed.
 */
val Track.contentUri: Uri
    get() = when {
        isManuallyScanned -> Uri.fromFile(File(path))
        isVideo -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
        else -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }
