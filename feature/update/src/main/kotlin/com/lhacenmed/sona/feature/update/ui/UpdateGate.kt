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
 * The prompt a newer version gets, over whatever the user is on - ArchiveTune's launch-time update sheet:
 * raised as soon as the update is found, and turning into the download dialog when the user updates. Every
 * activity lays one over its content, so an update found while the user is in Settings - after switching
 * channel, say - is offered there and then.
 *
 * Once a session for each version, across every activity: putting it away is kept in [UpdateRegistry], so
 * the next activity does not raise it again, and a newer version found later is prompted afresh.
 *
 * The registry is read only while the host is started, so a gate underneath another activity stays quiet
 * rather than prompting, or launching the installer, behind it.
 *
 * With [isAutoPromptEnabled] off the sheet waits to be asked for - Khatmah's `autoPrompt`: the update is
 * still found and kept, and the Updates screen still shows it.
 */
@Composable
fun UpdateGate(isAutoPromptEnabled: Boolean) {
    val context = LocalContext.current
    val available by UpdateRegistry.available.collectAsStateWithLifecycle()
    val dismissedVersion by UpdateRegistry.dismissedVersion.collectAsStateWithLifecycle()
    var isUpdating by rememberSaveable { mutableStateOf(false) }
    val release = available ?: return

    when {
        isUpdating -> UpdateDownloadDialog(release = release, onClose = { isUpdating = false })
        isAutoPromptEnabled && dismissedVersion != release.versionName ->
            NewUpdateSheet(
                release = release,
                onUpdate = { apk ->
                    UpdateRegistry.dismissPrompt(release.versionName)
                    isUpdating = true
                    startUpdate(context, release, apk)
                },
                onDismissRequest = { UpdateRegistry.dismissPrompt(release.versionName) },
            )
    }
}
