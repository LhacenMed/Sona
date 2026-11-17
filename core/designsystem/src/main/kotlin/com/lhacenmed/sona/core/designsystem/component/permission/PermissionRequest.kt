package com.lhacenmed.sona.core.designsystem.component.permission

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.lhacenmed.sona.core.common.permission.AppPermission

/**
 * Asks for [permission] the way Android grants it - the one way every screen asks - and tells [onResult]
 * whether Sona holds it afterwards. What is returned starts the asking.
 *
 * Already granted, [onResult] hears so at once. A runtime permission is asked for in Android's dialog; declined
 * with no rationale left to give, Android will not ask again, so the page where it is granted is opened instead.
 * Special access goes straight to its own page, and is read again as the user comes back.
 */
@Composable
fun rememberPermissionRequest(permission: AppPermission, onResult: (granted: Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val latestOnResult by rememberUpdatedState(onResult)
    val runtimePermission = permission.runtimePermission
    val dialogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && runtimePermission != null && activity?.shouldShowRequestPermissionRationale(runtimePermission) == false) {
            context.startActivity(permission.settingsIntent(context))
        }
        latestOnResult(granted)
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        latestOnResult(permission.isGranted(context))
    }
    return {
        when {
            permission.isGranted(context) -> latestOnResult(true)
            runtimePermission != null -> dialogLauncher.launch(runtimePermission)
            else -> settingsLauncher.launch(permission.settingsIntent(context))
        }
    }
}
