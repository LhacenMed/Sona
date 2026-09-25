package com.lhacenmed.sona.feature.settings.screen

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsSection

/**
 * Every permission Sona uses on this device, what it is for, and whether it is granted - one row each,
 * read from [AppPermission] so this screen lists exactly what the app asks for.
 *
 * Re-read every time the screen comes back into view, so a change made in Android's own settings shows
 * the moment the user returns. A row not granted asks for it: in Android's dialog where it still asks,
 * otherwise on the page where it is granted - its special-access page, or the app's details for a
 * permission the user declined for good. A granted row opens that page, where it can be taken back.
 */
object PermissionsScreen : Screen {
    override val titleRes: Int get() = R.string.permissions_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val activity = LocalActivity.current
        val grantedNow = { AppPermission.needed.filterTo(HashSet()) { it.isGranted(context) } }
        var grantedPermissions by remember { mutableStateOf(grantedNow()) }
        val refresh = { grantedPermissions = grantedNow() }
        LifecycleResumeEffect(Unit) {
            refresh()
            onPauseOrDispose {}
        }

        // The permission Android's dialog is asking about, until the user answers it.
        var askedPermission by remember { mutableStateOf<AppPermission?>(null) }
        val runtimePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            refresh()
            val permission = askedPermission ?: return@rememberLauncherForActivityResult
            askedPermission = null
            // Declined with no rationale left to give: Android will not ask again, so its page is where
            // the permission is granted from now on.
            val runtimePermission = permission.runtimePermission ?: return@rememberLauncherForActivityResult
            if (!granted && activity?.shouldShowRequestPermissionRationale(runtimePermission) == false) {
                context.startActivity(permission.settingsIntent(context))
            }
        }

        SettingsList {
            SettingsSection(stringResource(R.string.permissions_section)) {
                AppPermission.needed.forEach { permission ->
                    val isGranted = permission in grantedPermissions
                    val status = stringResource(
                        if (isGranted) R.string.permission_status_allowed else R.string.permission_status_not_allowed,
                    )
                    SettingsActionItem(
                        title = stringResource(permission.titleRes),
                        summary = stringResource(permission.purposeRes) + " • " + status,
                        onClick = {
                            val runtimePermission = permission.runtimePermission
                            if (isGranted || runtimePermission == null) {
                                context.startActivity(permission.settingsIntent(context))
                            } else {
                                askedPermission = permission
                                runtimePermissionLauncher.launch(runtimePermission)
                            }
                        },
                    )
                }
            }
        }
    }
}

private val AppPermission.titleRes: Int
    get() = when (this) {
        AppPermission.AUDIO_LIBRARY -> R.string.permission_audio_library_title
        AppPermission.NOTIFICATIONS -> R.string.permission_notifications_title
        AppPermission.FILE_DELETION -> R.string.permission_file_deletion_title
        AppPermission.APP_INSTALLS -> R.string.permission_app_installs_title
    }

private val AppPermission.purposeRes: Int
    get() = when (this) {
        AppPermission.AUDIO_LIBRARY -> R.string.permission_audio_library_purpose
        AppPermission.NOTIFICATIONS -> R.string.permission_notifications_purpose
        AppPermission.FILE_DELETION -> R.string.permission_file_deletion_purpose
        AppPermission.APP_INSTALLS -> R.string.permission_app_installs_purpose
    }
