package com.lhacenmed.sona.feature.download

import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.settings.DownloadsSettingsScreen
import kotlinx.coroutines.launch

/**
 * Where links become songs and videos: one pasted, typed or shared - [link] - opens in a sheet, is checked at a glance, and
 * is downloaded as audio or video with a tap. Beneath, one list: what is downloading, then what was saved, today and before -
 * played with a tap, put right where it failed, and deleted many at a time. The settings are in the bar.
 */
data class DownloadScreen(val link: String? = null) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: DownloadViewModel = hiltViewModel()
        val sections by viewModel.sections.collectAsStateWithLifecycle()
        val query by viewModel.query.collectAsStateWithLifecycle()
        val linkEntry by viewModel.linkEntry.collectAsStateWithLifecycle()
        val linkSheet by viewModel.linkSheet.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val selection = rememberSelectionState()
        var openedId by rememberSaveable { mutableStateOf<Long?>(null) }
        var isChoosingSelectionOptions by remember { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }

        // The link this was opened with, opened once - not again as the screen comes back.
        var hasTakenLink by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(link) {
            if (!hasTakenLink && link != null) viewModel.open(link)
            hasTakenLink = true
        }

        val listed = sections?.all.orEmpty()
        val selected = listed.filter { selection.isSelected(it.id) }

        SonaExtendedFloatingActionButtons(
            listOf(
                ExtendedFloatingActionButtonContent(
                    label = stringResource(R.string.download_paste_link),
                    icon = Icons.Filled.ContentPaste,
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)
                        viewModel.onPaste(clipboard?.toString())
                    },
                    isPrimary = true,
                    isVisible = !selection.isActive && linkEntry == null,
                ),
            ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.download_title),
                onNavigateBack = navigator::back,
                actions = listOfNotNull(
                    // Nothing to search before anything is downloaded.
                    TopBarAction(stringResource(R.string.download_search), Icons.Filled.Search) { viewModel.onQueryChange("") }.takeIf { listed.isNotEmpty() || query != null },
                    TopBarAction(stringResource(R.string.download_settings), Icons.Filled.Settings) { navigator.go(DownloadsSettingsScreen) },
                ),
                search = when {
                    linkEntry != null -> TopBarSearch(
                        query = linkEntry.orEmpty(),
                        onQueryChange = viewModel::onLinkEntryChange,
                        onClose = viewModel::onLinkEntryClose,
                        hint = stringResource(R.string.download_link_hint),
                    )
                    query != null -> TopBarSearch(query = query.orEmpty(), onQueryChange = viewModel::onQueryChange, onClose = { viewModel.onQueryChange(null) })
                    else -> null
                },
                selection = selection.toTopBarSelection(
                    actions = listOf(
                        selection.selectAllAction(listed.map(Download::id)),
                        TopBarAction(stringResource(R.string.download_delete), Icons.Filled.Delete) { isDeleting = true },
                    ),
                    onMoreOptions = { isChoosingSelectionOptions = true },
                ),
            )
            val shown = sections
            when {
                // Still being read: nothing yet, rather than an empty state for a moment.
                shown == null -> Unit
                shown.isEmpty && !query.isNullOrBlank() -> SonaEmptyState(
                    title = stringResource(R.string.download_search_empty, query.orEmpty()),
                    message = stringResource(R.string.download_search_empty_message),
                )
                shown.isEmpty -> SonaEmptyState(title = stringResource(R.string.download_empty_title), message = stringResource(R.string.download_empty_message))
                else -> DownloadList(shown, viewModel, selection, onOpen = { openedId = it.id })
            }
        }

        linkSheet?.let { LinkSheet(it, viewModel) }

        // Followed by its id as it goes, from downloading to saved.
        listed.firstOrNull { it.id == openedId }?.let { download ->
            DownloadSheet(download, viewModel, onDismiss = { openedId = null })
        }

        if (isChoosingSelectionOptions) {
            SelectionOptionsSheet(
                selected = selected,
                viewModel = viewModel,
                onShareLinks = {
                    val links = selected.joinToString("\n") { it.url }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, links), null))
                },
                onDone = selection::clear,
                onDismiss = { isChoosingSelectionOptions = false },
            )
        }

        if (isDeleting) {
            val ids = selected.map(Download::id)
            SonaConfirmationDialog(
                title = context.resources.getQuantityString(R.plurals.download_delete_title, ids.size, ids.size),
                message = stringResource(R.string.download_delete_message),
                confirmLabel = stringResource(R.string.download_delete),
                successMessage = context.resources.getQuantityString(R.plurals.download_deleted_count, ids.size, ids.size),
                failureMessage = stringResource(R.string.download_delete_failed),
                onDismiss = { isDeleting = false },
                operation = { onFinished ->
                    scope.launch {
                        val isDeleted = viewModel.onDelete(ids)
                        selection.clear()
                        onFinished(isDeleted)
                    }
                },
            )
        }
    }
}
