package com.lhacenmed.sona.feature.update.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.feature.update.UpdateRegistry

/**
 * The prompt a newer version gets on the library - ArchiveTune's launch-time update sheet: shown once a
 * session for the available update, and turning into the download dialog when the user updates.
 *
 * The registry is read only while the host is started, so a gate underneath another screen stays quiet
 * rather than prompting, or launching the installer, behind it.
 *
 * With [isAutoPromptEnabled] off the sheet waits to be asked for - Khatmah's `autoPrompt`: the update is
 * still found and kept, and the Updates screen's check still shows it.
 */
@Composable
fun UpdateGate(isAutoPromptEnabled: Boolean) {
    val context = LocalContext.current
    val available by UpdateRegistry.available.collectAsStateWithLifecycle()
    var dismissedVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var isUpdating by rememberSaveable { mutableStateOf(false) }
    val release = available ?: return

    when {
        isUpdating -> UpdateDownloadDialog(release = release, onClose = { isUpdating = false })
        isAutoPromptEnabled && dismissedVersion != release.versionName ->
            NewUpdateSheet(
                release = release,
                onUpdate = { apk ->
                    dismissedVersion = release.versionName
                    isUpdating = true
                    startUpdate(context, release, apk)
                },
                onDismissRequest = { dismissedVersion = release.versionName },
            )
    }
}
