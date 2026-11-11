package com.lhacenmed.sona.feature.tageditor.autotag

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.topBarBackground
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.explorer.FolderPickerSheet
import com.lhacenmed.sona.feature.explorer.FolderPickerTab
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.tageditor.R
import com.lhacenmed.sona.feature.tageditor.TagEditorScreen
import com.lhacenmed.sona.feature.tageditor.rememberFileChangeRequest
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * Tagging many tracks at once - the tracks [trackIds] names, or the whole library where it names none.
 *
 * It opens on what a run would cover - every folder, to be narrowed - and what it would find: tags, covers and
 * lyrics, replacing what the files hold or only filling their blanks. Finding starts a run that carries on wherever
 * the user goes - see [AutoTagger] - and this screen follows it: each track listed under what the run made of it as
 * it is looked up, those matched convincingly picked to be saved; a press picks or unpicks a track, and its pen
 * opens it in the tag editor, on everything found for it. Nothing reaches a file until the picked tracks are saved, in one go.
 *
 * While a run lasts, this screen shows it, from wherever it is opened; once it ends - saved whole, or finished with -
 * the screen is back where it opened, ready for another.
 */
data class AutoTagScreen(val trackIds: List<Long>? = null) : Screen {

    @Composable
    override fun Content() {
        val viewModel = hiltViewModel<AutoTagViewModel, AutoTagViewModel.Factory>(
            creationCallback = { factory -> factory.create(trackIds) },
        )
        val session = viewModel.session

        if (session == null) AutoTagSetup(viewModel) else AutoTagReview(viewModel, session)
    }
}

@Composable
private fun AutoTagSetup(viewModel: AutoTagViewModel) {
    val navigator = LocalNavigator.current
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val choices = viewModel.choices
    var isChoosingFolders by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    SonaExtendedFloatingActionButtons(
        listOf(
            ExtendedFloatingActionButtonContent(
                label = stringResource(R.string.autotag_find_tags),
                icon = SonaIcons.AutoFixHigh,
                onClick = viewModel::start,
                isPrimary = true,
                isVisible = tracks.isNotEmpty() && choices.findsAnything,
            ),
        ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SonaTopAppBar(title = stringResource(R.string.autotag_title), onNavigateBack = navigator::back)
        FastScroller(scrollState, Modifier.fillMaxSize()) { overscrollEffect ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, overscrollEffect = overscrollEffect)
                    .padding(bottom = LocalBottomContentPadding.current),
            ) {
                ColumnSection(title = stringResource(R.string.autotag_tracks_section)) {
                    TracksRow(
                        trackCount = tracks.size,
                        folders = folders,
                        leftOut = viewModel.leftOut,
                        onClick = { isChoosingFolders = true },
                    )
                }
                HorizontalDivider()
                ColumnSection(title = stringResource(R.string.autotag_find_section)) {
                    FindChoices(choices = choices, onChange = { viewModel.choices = it })
                    AdvancedChoices(choices = choices, onChange = { viewModel.choices = it })
                }
                Text(
                    stringResource(R.string.autotag_find_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SonaComponentStyle.ContentHorizontalPadding, vertical = 16.dp),
                )
            }
        }
    }

    if (isChoosingFolders) {
        val resources = LocalContext.current.resources
        FolderPickerSheet(
            title = stringResource(R.string.autotag_pick_title),
            tabs = listOf(FolderPickerTab(stringResource(R.string.autotag_pick_tab), viewModel.coverableTracks.value)),
            initiallyPicked = viewModel.picked,
            confirmLabel = { count -> resources.getQuantityString(R.plurals.autotag_tag_tracks, count, count) },
            onConfirm = viewModel::keep,
            onDismissRequest = { isChoosingFolders = false },
        )
    }
}

@Composable
private fun AutoTagReview(viewModel: AutoTagViewModel, session: AutoTagSession) {
    val navigator = LocalNavigator.current
    val requestSave = rememberFileChangeRequest(onGranted = viewModel::save)
    var isConfirmingFinish by rememberSaveable { mutableStateOf(false) }
    val sections = rememberSectionListState()
    val listState = rememberLazyListState()
    val picked = session.pickedItems

    SonaExtendedFloatingActionButtons(
        listOf(
            ExtendedFloatingActionButtonContent(
                label = pluralStringResource(R.plurals.autotag_save_tracks, picked.size, picked.size),
                icon = SonaIcons.Save,
                onClick = { requestSave(picked.map { it.track }) },
                isPrimary = true,
                isVisible = session.phase == AutoTagPhase.REVIEWING && picked.isNotEmpty(),
            ),
        ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // The bar and the run's progress are one section, lifting as one once the list leaves its top - the library's
        // bar, shortcuts and tabs the same way - so the rows scrolling under the progress never read as part of it.
        Column(modifier = Modifier.topBarBackground()) {
            SonaTopAppBar(
                title = stringResource(R.string.autotag_title),
                drawsBackground = false,
                onNavigateBack = navigator::back,
                // Not the selection bar a list's own selection takes: back would drop every pick the run made.
                actions = listOf(session.picks.selectAllAction(session.changedTrackIds)),
            )
            RunProgress(
                session = session,
                onStop = viewModel::stop,
                onFinish = { if (picked.isEmpty()) viewModel.discard() else isConfirmingFinish = true },
            )
        }
        // Read here, for the list's builder, which runs outside composition.
        val resources = LocalContext.current.resources
        val tryAgainLabel = stringResource(R.string.autotag_try_again)
        FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
            ) {
                val groups = AutoTagOutcome.entries.mapNotNull { outcome -> session.groups[outcome]?.let { outcome to it } }
                groups.forEachIndexed { index, (outcome, items) ->
                    section(
                        key = outcome.name,
                        title = resources.getString(R.string.autotag_outcome_count, resources.getString(outcome.titleRes), items.size),
                        state = sections,
                        hasDividerAbove = index > 0,
                        actions = listOfNotNull(
                            TopBarAction(label = tryAgainLabel, icon = SonaIcons.Refresh, onClick = viewModel::retryUnreachable)
                                .takeIf { outcome == AutoTagOutcome.UNREACHABLE && session.phase == AutoTagPhase.REVIEWING },
                        ),
                    ) {
                        items(items, key = { it.track.id }) { item ->
                            AutoTagRow(item = item, picks = session.picks, onOpen = { navigator.go(TagEditorScreen(item.track.id, isInAutoTagRun = true)) })
                        }
                    }
                }
            }
        }
    }

    if (isConfirmingFinish) {
        // Read here rather than inside the buttons: they are built outside composition.
        val finishWithoutSavingButton0 = stringResource(R.string.autotag_keep_reviewing)
        val finishWithoutSavingButton1 = stringResource(R.string.autotag_finish)
        SonaDialog(
            onDismissRequest = { isConfirmingFinish = false },
            title = stringResource(R.string.autotag_finish_title),
            buttons = {
                actionButton(label = finishWithoutSavingButton0, onClick = { isConfirmingFinish = false })
                actionButton(label = finishWithoutSavingButton1, onClick = viewModel::discard)
            },
        ) {
            Text(
                pluralStringResource(R.plurals.autotag_finish_message, picked.size, picked.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
