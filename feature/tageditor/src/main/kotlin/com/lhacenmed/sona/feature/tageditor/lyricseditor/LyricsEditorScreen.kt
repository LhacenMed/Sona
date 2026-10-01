package com.lhacenmed.sona.feature.tageditor.lyricseditor

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.WindowBusyOverlay
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.tageditor.rememberTrackFileSave

/**
 * The lyrics editor on its own, opened from the lyrics sheet for the track [trackId] - see [LyricsEditor]. It starts
 * from the lyrics the file holds and saves them back into it - asking Android first where it must, as the tag editor
 * does - then goes back to the sheet, which shows them at once. The playing track repeats while it is in front.
 */
data class LyricsEditorScreen(val trackId: Long) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<LyricsEditorViewModel, LyricsEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(trackId) },
        )
        val editor = viewModel.editor

        LifecycleStartEffect(viewModel) {
            val release = viewModel.holdPlayingTrack()
            onStopOrDispose { release() }
        }
        val requestSave = rememberTrackFileSave(
            track = editor?.track,
            outcome = viewModel.saveOutcome,
            save = viewModel::save,
            onSaved = {
                context.toast("Lyrics saved")
                // Closed outright: going back would ask whether to leave the changes just saved.
                navigator.close()
            },
            onOutcomeHandled = viewModel::onSaveOutcomeHandled,
        )

        if (editor == null) {
            // A moment's read of the file: the bar is already where it will be.
            SonaTopAppBar(title = "Lyrics editor", onNavigateBack = navigator::back)
            return
        }
        LyricsEditor(
            state = editor,
            confirmAction = TopBarAction(label = "Save", icon = Icons.Filled.Check, enabled = editor.hasChanges && !viewModel.isSaving) {
                requestSave()
            },
            onLeave = navigator::close,
        )
        // The whole window held while the file is written, and back with it, so the save is never left halfway.
        WindowBusyOverlay(isBusy = viewModel.isSaving)
        BackHandler(enabled = viewModel.isSaving) {}
    }
}
