package com.lhacenmed.sona.feature.tageditor

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.model.Track
import kotlinx.coroutines.CancellationException

/** How saving into a track's file ended: done, waiting on the user to let Sona change the file, or failed. */
sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data class NeedsConsent(val request: IntentSender) : SaveOutcome
    data class Failed(val message: String) : SaveOutcome
}

/**
 * Runs [write] - a save into a track's file - and tells how it went: where Android has not let Sona change the
 * file yet, it asks the user, once for that file; any other failure says why, or [failureMessage] where it does not.
 */
internal suspend fun saveOutcomeOf(failureMessage: String, write: suspend () -> Unit): SaveOutcome =
    try {
        write()
        SaveOutcome.Saved
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            SaveOutcome.NeedsConsent(e.userAction.actionIntent.intentSender)
        } else {
            SaveOutcome.Failed(e.message ?: failureMessage)
        }
    }

/**
 * What a screen that saves into [track]'s file needs to do so - what its save button calls, which gets whatever
 * Android wants first, then [save]s. Sona writes freely where it may manage all files; elsewhere Android asks the
 * user first, once for this file - and a file MediaStore has not indexed, which Android's request cannot name,
 * needs that access outright.
 *
 * The save's [outcome] is acted on once it arrives: [onSaved] once done, the user asked where Android wants
 * consent - saving again once given - and why where it failed; then [onOutcomeHandled] clears it.
 */
@Composable
internal fun rememberTrackFileSave(
    track: Track?,
    outcome: SaveOutcome?,
    save: () -> Unit,
    onSaved: () -> Unit,
    onOutcomeHandled: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val latestSave by rememberUpdatedState(save)
    val latestOnSaved by rememberUpdatedState(onSaved)
    val latestOnOutcomeHandled by rememberUpdatedState(onOutcomeHandled)
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) latestSave()
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (AppPermission.FILE_CHANGES.isGranted(context)) latestSave()
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latestSave()
    }

    LaunchedEffect(outcome) {
        when (outcome) {
            null -> return@LaunchedEffect
            SaveOutcome.Saved -> latestOnSaved()
            is SaveOutcome.NeedsConsent -> consentLauncher.launch(IntentSenderRequest.Builder(outcome.request).build())
            is SaveOutcome.Failed -> context.toast(outcome.message)
        }
        latestOnOutcomeHandled()
    }

    return {
        val permission = AppPermission.FILE_CHANGES
        val runtimePermission = permission.runtimePermission
        when {
            track == null -> Unit
            permission.isGranted(context) -> latestSave()
            runtimePermission != null -> writePermissionLauncher.launch(runtimePermission)
            track.isManuallyScanned -> allFilesAccessLauncher.launch(permission.settingsIntent(context))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val request = MediaStore.createWriteRequest(context.contentResolver, listOf(track.contentUri))
                consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }
            else -> latestSave()
        }
    }
}
