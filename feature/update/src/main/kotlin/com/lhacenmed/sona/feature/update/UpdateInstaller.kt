package com.lhacenmed.sona.feature.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lhacenmed.sona.core.common.permission.AppPermission
import java.io.File

/**
 * Launches the system package installer for a downloaded APK. The app must first hold the user's
 * "install unknown apps" grant, [AppPermission.APP_INSTALLS] — [canInstall] reports it and [requestPermissionIntent]
 * opens the settings screen where the user grants it once.
 */
object UpdateInstaller {

    /** True when the installer can be launched directly - the grant is already held. */
    fun canInstall(context: Context): Boolean = AppPermission.APP_INSTALLS.isGranted(context)

    /** ACTION_VIEW install intent for [apk], shared through the app's FileProvider. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Settings screen where the user grants this app permission to install unknown apps. */
    fun requestPermissionIntent(context: Context): Intent =
        AppPermission.APP_INSTALLS.settingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
