package com.lhacenmed.sona.feature.tageditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.KeyboardAwareBackHandler
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.WindowBusyOverlay
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.tageditor.lyricseditor.DiscardChangesDialog
import com.lhacenmed.sona.feature.tageditor.lyricseditor.LyricsEditorScreen
import java.io.File

/**
 * Editing the track [trackId]'s tags - AutomaTag's editor, in Sona's design.
 *
 * It opens looking the track up already, under the likeliest reading of its names - laid out at the top, to be
 * changed and searched again. What it finds fills the screen as it arrives: the best match, applied whole with
 * one press, every tag to change by hand, every cover to choose from - the gallery's included, found or not -
 * every set of lyrics every source has, and the other matches; each list narrowed to one source in a tap.
 * Nothing reaches the file until it is saved - see [rememberTrackFileSave]; back leaves it as it was.
 *
 * Saving writes the file itself. Sona does so freely where it may manage all files; elsewhere Android asks the
 * user first, once for this file - and a file MediaStore has not indexed, which Android's request cannot name,
 * needs that access outright.
 *
 * [isInAutoTagRun], it edits the run's draft for the track instead - everything the run found, the best already
 * taken - and Done keeps it in the run, to be saved with the rest; back leaves it as the run had it.
 */
data class TagEditorScreen(val trackId: Long, val isInAutoTagRun: Boolean = false) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<TagEditorViewModel, TagEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(trackId, isInAutoTagRun) },
        )
        val draft = viewModel.draft
        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

        // A run's track the run no longer holds - saved, or let go meanwhile - has nothing left to edit.
        LaunchedEffect(viewModel) { if (isInAutoTagRun && draft == null) navigator.close() }

        // Acted on once the save has let go of the screen - back included - so leaving it after a save gets
        // through; a save that did not succeed stays, to be tried again.
        val requestSave = rememberTrackFileSave(
            track = draft?.track,
            outcome = viewModel.saveOutcome,
            save = viewModel::save,
            onSaved = {
                if (!isInAutoTagRun) context.toast("Tags saved")
                // Closed outright: going back would ask whether to leave the changes just saved.
                navigator.close()
            },
            onOutcomeHandled = viewModel::onSaveOutcomeHandled,
        )
        LifecycleStartEffect(viewModel) {
            val release = viewModel.holdPlayingTrack()
            onStopOrDispose { release() }
        }
        var isConfirmingDiscard by rememberSaveable { mutableStateOf(false) }
        val scrollState = rememberScrollState()

        // The whole window held while the file is written and the library reads it back - and back with it, so
        // the save is never left halfway, with the file changed and the lists not yet showing it. Putting the
        // keyboard away is never asking to leave - see [KeyboardAwareBackHandler].
        WindowBusyOverlay(isBusy = viewModel.isSaving)
        KeyboardAwareBackHandler(enabled = viewModel.hasChanges && !viewModel.isSaving) { isConfirmingDiscard = true }
        BackHandler(enabled = viewModel.isSaving) {}

        // The screen's button, as the lyrics editor's: growing in once there is something to keep.
        SonaExtendedFloatingActionButtons(
            listOf(
                ExtendedFloatingActionButtonContent(
                    label = if (isInAutoTagRun) "Done" else "Save",
                    icon = Icons.Filled.Check,
                    // A run's draft reaches no file here, so there is nothing to ask Android first.
                    onClick = { if (isInAutoTagRun) viewModel.save() else requestSave() },
                    isPrimary = true,
                    isVisible = viewModel.hasChanges && !viewModel.isSaving,
                ),
            ),
        )

        if (isConfirmingDiscard) {
            DiscardChangesDialog(
                onKeepEditing = { isConfirmingDiscard = false },
                // Closed outright: going back would only ask again.
                onDiscard = {
                    viewModel.discardChanges()
                    navigator.close()
                },
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(title = "Edit tags", onNavigateBack = navigator::back)
            if (draft == null) return@Column

            val track = draft.track
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Above the keyboard, so the field it opens for is scrolled into view rather than the window slid.
                    .imePadding()
                    .screenList(scrollState)
                    .verticalScroll(scrollState)
                    .padding(bottom = LocalBottomContentPadding.current),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TrackTagDraftSections(
                    draft = draft,
                    isPlaying = isPlaying,
                    onTogglePlayback = viewModel::togglePlayback,
                    onEditLyrics = { navigator.go(LyricsEditorScreen(trackId, editsTagDraft = true)) },
                )

                HorizontalDivider()
                ColumnSection(title = "File") {
                    FileFact(label = "Duration", value = formatDuration(track.durationMs))
                    FileFact(label = "Format", value = File(track.path).extension.uppercase())
                    draft.file?.bitrateKbps?.let { FileFact(label = "Bitrate", value = "$it kbps") }
                    FileFact(label = "Path", value = track.path)
                }
            }
        }
    }
}

