package com.lhacenmed.sona.feature.update.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.feature.update.NetworkMonitor
import com.lhacenmed.sona.feature.update.R
import com.lhacenmed.sona.feature.update.UpdateInstaller
import com.lhacenmed.sona.feature.update.UpdateRegistry
import com.lhacenmed.sona.feature.update.UpdateService
import com.lhacenmed.sona.feature.update.UpdateState

/**
 * The update prompt. Appears only once [UpdateRegistry.available] is set and walks the user through
 * download → install while mirroring the live [UpdateState]. Dismissing during a download leaves the
 * foreground service running (the notification carries the progress); re-opening the app re-reads
 * the same flow and resumes the dialog where it left off.
 *
 * Whether it appears *on its own* is [autoPrompt]'s to say. With that off the update is still found,
 * saved and staged exactly as before — this simply waits to be asked, which the Updates screen's
 * manual check does through [UpdateRegistry.requestPrompt]. Being asked also outranks a dismissal
 * earlier in the session: asking twice should not be answered with silence.
 *
 * The registry is read only while the host is started, so a gate on a screen underneath another
 * stays quiet rather than answering a request, or launching the installer, behind it.
 */
@Composable
fun UpdateGate(autoPrompt: Boolean) {
    val update  by UpdateRegistry.available.collectAsStateWithLifecycle()
    val state   by UpdateRegistry.state.collectAsStateWithLifecycle()
    val asked   by UpdateRegistry.promptRequest.collectAsStateWithLifecycle()
    val context  = LocalContext.current
    var dismissed by rememberSaveable { mutableStateOf(false) }

    // Being asked for outright clears an earlier dismissal, so the request is never swallowed.
    LaunchedEffect(asked) { if (asked) dismissed = false }

    val available = update ?: return
    if (!asked && (!autoPrompt || dismissed)) return

    // Closing the dialog also answers the request that opened it.
    val dismiss = {
        dismissed = true
        UpdateRegistry.clearPromptRequest()
    }

    val downloading = state is UpdateState.Connecting || state is UpdateState.Downloading

    // Starts the download only when online; otherwise surfaces a "connect to the internet" prompt
    // (shown via the Error state, which flips the button to Retry). Used by Update and Retry alike.
    val startDownload = {
        if (NetworkMonitor.isOnline(context)) UpdateService.start(context)
        else UpdateRegistry.update(UpdateState.Error(context.getString(R.string.update_offline)))
    }

    // Auto-launch the installer the instant a *fresh* download finishes, so the user never has to
    // tap Install. Gated on having seen the download in flight this session (sawDownloading) so a
    // relaunch that merely restores a staged APK shows the dialog instead of installing unbidden,
    // and on the install grant — without it we fall through to the manual Install button.
    var sawDownloading by remember { mutableStateOf(false) }
    var autoInstalled  by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (val s = state) {
            UpdateState.Connecting, is UpdateState.Downloading -> sawDownloading = true
            is UpdateState.Downloaded ->
                if (sawDownloading && !autoInstalled && UpdateInstaller.canInstall(context)) {
                    autoInstalled = true
                    context.startActivity(UpdateInstaller.installIntent(context, s.apk))
                }
            else -> Unit
        }
    }

    // Read here rather than inside the group: a group builds its items outside composition.
    val laterLabel = stringResource(R.string.update_later)
    val installLabel = stringResource(R.string.update_install)
    val retryLabel = stringResource(R.string.update_retry)
    val downloadLabel = stringResource(R.string.update_download)

    SonaDialog(
        // A download in flight is non-cancelable from the scrim; the buttons drive it instead.
        onDismissRequest = { if (!downloading) dismiss() },
        title = stringResource(R.string.update_title),
        // A download in flight holds its buttons, which stay where they are: the notification's Stop is
        // how it is cancelled.
        buttons = {
            actionButton(label = laterLabel, onClick = dismiss, enabled = !downloading)
            when (val s = state) {
                is UpdateState.Downloaded -> actionButton(label = installLabel, onClick = {
                    // Grant present → launch the installer; otherwise send the user to grant it once.
                    if (UpdateInstaller.canInstall(context)) {
                        context.startActivity(UpdateInstaller.installIntent(context, s.apk))
                    } else {
                        context.startActivity(UpdateInstaller.requestPermissionIntent(context))
                    }
                })

                is UpdateState.Error -> actionButton(label = retryLabel, onClick = startDownload)

                else -> actionButton(label = downloadLabel, onClick = startDownload, enabled = !downloading)
            }
        },
    ) {
        Text(stringResource(R.string.update_message, available.versionName))
        if (available.notes.isNotBlank()) {
            // Bounded and scrollable of its own: notes run to several lines, and a long one would
            // otherwise take the whole dialog's scroll away from the progress under it.
            ReleaseNotes(
                markdown = available.notes,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
        when (val s = state) {
            is UpdateState.Downloading -> {
                val p = s.progress
                if (p != null) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                }
                Text(s.log, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            }
            UpdateState.Connecting ->
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            is UpdateState.Error ->
                Text(s.message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp))
            else -> Unit
        }
    }
}
