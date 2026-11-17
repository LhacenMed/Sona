package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.designsystem.component.permission.rememberPermissionRequest
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchItem
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R

/**
 * Every permission Sona uses on this device and what it is for - one row each, read from [AppPermission] so
 * this screen lists exactly what the app asks for. Whether each is granted is its switch: on while Sona holds
 * it, off while it does not, as every other setting says which way it is set.
 */
data object PermissionsScreen : Screen {
    override val titleRes: Int get() = R.string.permissions_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsSection(stringResource(R.string.permissions_section)) {
                AppPermission.needed.forEach { permission -> PermissionItem(permission) }
            }
        }
    }
}

/**
 * [permission], and whether Sona holds it - read again every time the screen comes back into view, so a change
 * made in Android's own settings shows the moment the user returns. Turned on, it is asked for as
 * [rememberPermissionRequest] asks; turned off, it opens the page where Android lets it be taken back, which
 * an app cannot do itself.
 */
@Composable
private fun PermissionItem(permission: AppPermission) {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(permission.isGranted(context)) }
    LifecycleResumeEffect(permission) {
        isGranted = permission.isGranted(context)
        onPauseOrDispose {}
    }
    val request = rememberPermissionRequest(permission) { isGranted = it }

    SettingsSwitchItem(
        title = stringResource(permission.titleRes),
        summary = stringResource(permission.purposeRes),
        icon = permission.icon,
        checked = isGranted,
        onCheckedChange = { if (isGranted) context.startActivity(permission.settingsIntent(context)) else request() },
    )
}

internal val AppPermission.titleRes: Int
    get() = when (this) {
        AppPermission.STORAGE -> R.string.permission_storage_title
        AppPermission.NOTIFICATIONS -> R.string.permission_notifications_title
        AppPermission.APP_INSTALLS -> R.string.permission_app_installs_title
    }

internal val AppPermission.purposeRes: Int
    get() = when (this) {
        AppPermission.STORAGE -> R.string.permission_storage_purpose
        AppPermission.NOTIFICATIONS -> R.string.permission_notifications_purpose
        AppPermission.APP_INSTALLS -> R.string.permission_app_installs_purpose
    }

private val AppPermission.icon: ImageVector
    get() = when (this) {
        AppPermission.STORAGE -> SonaIcons.Folder
        AppPermission.NOTIFICATIONS -> SonaIcons.Notifications
        AppPermission.APP_INSTALLS -> SonaIcons.SystemUpdate
    }
