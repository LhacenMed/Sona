package com.lhacenmed.sona.feature.scanner

import android.Manifest
import android.os.Build

/**
 * The single runtime permission [MediaScanner] needs in order to read the device's audio library.
 *
 * On Android 13+ (API 33, Tiramisu) granular media permissions replaced blanket storage access, so
 * [Manifest.permission.READ_MEDIA_AUDIO] is what's required. Below that, reading shared storage
 * (including the MediaStore audio collection) requires the legacy [Manifest.permission.READ_EXTERNAL_STORAGE].
 *
 * Requesting the permission from the user is `:app`'s responsibility - this is only the lookup of
 * which permission string to request.
 */
fun scannerRequiredPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
