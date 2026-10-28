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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.designsystem.component.sheet.FolderChoice
import com.lhacenmed.sona.core.designsystem.component.sheet.FolderPickerTab
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaFolderPickerSheet
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.topBarBackground
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.tageditor.TagEditorScreen
import com.lhacenmed.sona.feature.tageditor.rememberFileChangeRequest

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
                label = "Find tags",
                icon = Icons.Filled.AutoFixHigh,
                onClick = viewModel::start,
                isPrimary = true,
                isVisible = tracks.isNotEmpty() && choices.findsAnything,
            ),
        ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SonaTopAppBar(title = "Auto-tag", onNavigateBack = navigator::back)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenList(scrollState)
                .verticalScroll(scrollState)
                .padding(bottom = LocalBottomContentPadding.current),
        ) {
            ColumnSection(title = "Tracks") {
                TracksRow(
                    trackCount = tracks.size,
                    folders = folders,
                    leftOutFolders = viewModel.leftOutFolders,
                    onClick = { isChoosingFolders = true },
                )
            }
            HorizontalDivider()
            ColumnSection(title = "Find") {
                FindChoices(choices = choices, onChange = { viewModel.choices = it })
                AdvancedChoices(choices = choices, onChange = { viewModel.choices = it })
            }
            Text(
                "The best match is picked for every track. Nothing is written until you have looked over what was found and saved it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SonaComponentStyle.ContentHorizontalPadding, vertical = 16.dp),
            )
        }
    }

    if (isChoosingFolders) {
        SonaFolderPickerSheet(
            title = "Folders to tag",
            tabs = listOf(FolderPickerTab("Folders", folders.map { (folder, count) -> FolderChoice(folder, tracksLabel(count)) })),
            initiallySelected = folders.map { it.first.path }.filterNot { it in viewModel.leftOutFolders }.toSet(),
            confirmLabel = { count -> if (count == 1) "Tag 1 folder" else "Tag $count folders" },
            onConfirm = viewModel::keepFolders,
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
                label = "Save ${tracksLabel(picked.size)}",
                icon = Icons.Filled.Save,
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
                title = "Auto-tag",
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().screenList(listState),
            contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
        ) {
            val groups = AutoTagOutcome.entries.mapNotNull { outcome -> session.groups[outcome]?.let { outcome to it } }
            groups.forEachIndexed { index, (outcome, items) ->
                section(
                    key = outcome.name,
                    title = "${outcome.title} · ${items.size}",
                    state = sections,
                    hasDividerAbove = index > 0,
                    actions = listOfNotNull(
                        TopBarAction(label = "Try again", icon = Icons.Filled.Refresh, onClick = viewModel::retryUnreachable)
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

    if (isConfirmingFinish) {
        SonaDialog(
            onDismissRequest = { isConfirmingFinish = false },
            title = "Finish without saving?",
            buttons = {
                actionButton(label = "Keep reviewing", onClick = { isConfirmingFinish = false })
                actionButton(label = "Finish", onClick = viewModel::discard)
            },
        ) {
            Text(
                "${tracksLabel(picked.size)} picked to be saved will keep their tags as they are.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
