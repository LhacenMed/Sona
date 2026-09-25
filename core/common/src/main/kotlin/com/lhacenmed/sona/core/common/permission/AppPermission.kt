package com.lhacenmed.sona.core.common.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * Every permission Sona asks for, and the one place each is checked and asked for - so the launch,
 * the Permissions settings screen and the feature that needs it can never disagree about whether it
 * is granted or how to get it.
 *
 * Some are ordinary runtime permissions, which Android asks about in a dialog over the app
 * ([runtimePermission]). Others are special access, which Android only grants from a settings page of
 * its own ([settingsIntent]). Which one a permission is can depend on the Android version.
 */
enum class AppPermission {
    /** Reading the device's music - without it there is no library. */
    AUDIO_LIBRARY,

    /** Showing an update's download progress. The player's own controls need no permission. */
    NOTIFICATIONS,

    /** Deleting tracks' files without Android asking each time. */
    FILE_DELETION,

    /** Installing a downloaded update. */
    APP_INSTALLS,
    ;

    /** Whether this Android version has this permission at all - notifications are only asked for from 13 on. */
    val isNeeded: Boolean
        get() = this != NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether it is asked for as the app opens: what the app is hardly usable without, and asked in
     * Android's own dialog. Special access is asked for where it is first needed instead, rather than
     * sending the user out of the app before they have used it.
     */
    val isAskedAtLaunch: Boolean
        get() = this == AUDIO_LIBRARY || this == NOTIFICATIONS

    /** The runtime permission Android asks about in a dialog, or null where this is special access. */
    val runtimePermission: String?
        get() = when (this) {
            AUDIO_LIBRARY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
            FILE_DELETION -> Manifest.permission.WRITE_EXTERNAL_STORAGE.takeIf {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            }
            APP_INSTALLS -> null
        }

    fun isGranted(context: Context): Boolean = when {
        this == FILE_DELETION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager()
        this == APP_INSTALLS -> context.packageManager.canRequestPackageInstalls()
        else -> context.checkSelfPermission(checkNotNull(runtimePermission)) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Where this is granted outside the app: its own special-access page, or - for a runtime permission
     * Android no longer asks about - the app's details page, where every permission can be changed.
     */
    fun settingsIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        return when {
            this == FILE_DELETION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
            this == APP_INSTALLS -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
    }

    companion object {
        /** The permissions this Android version has, in the order the Permissions screen lists them. */
        val needed: List<AppPermission> get() = entries.filter { it.isNeeded }
    }
}
