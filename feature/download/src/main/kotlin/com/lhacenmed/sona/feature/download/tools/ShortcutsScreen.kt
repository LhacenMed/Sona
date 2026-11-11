package com.lhacenmed.sona.feature.download.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadCommandShortcutEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsTextFieldDialog
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.model.DownloadCommandShortcut
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Snippets of yt-dlp arguments - YTDLnis's template shortcuts: each a tap from being added to a command as it is written.
 * Added from the button, deleted many at a time from a selection.
 */
data object ShortcutsScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: ShortcutsViewModel = hiltViewModel()
        val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        var isAdding by rememberSaveable { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }

        SonaExtendedFloatingActionButtons(
            listOf(ExtendedFloatingActionButtonContent(label = stringResource(R.string.download_shortcuts_add), icon = Icons.Filled.Add, onClick = { isAdding = true }, isPrimary = true)),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.download_shortcuts_title),
                onNavigateBack = navigator::back,
                selection = selection.toTopBarSelection(
                    actions = listOf(
                        selection.selectAllAction(shortcuts.map(DownloadCommandShortcut::id)),
                        TopBarAction(stringResource(R.string.download_delete), Icons.Filled.DeleteForever) { isDeleting = true },
                    ),
                    onMoreOptions = null,
                ),
            )
            if (shortcuts.isEmpty()) {
                SonaEmptyState(title = stringResource(R.string.download_shortcuts_empty_title), message = stringResource(R.string.download_shortcuts_empty_message))
                return@Column
            }
            val listState = rememberLazyListState()
            FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
                LazyColumn(
                    state = listState,
                    overscrollEffect = overscrollEffect,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                ) {
                    items(shortcuts, key = DownloadCommandShortcut::id) { shortcut ->
                        SonaListRow(
                            title = shortcut.content,
                            subtitle = "",
                            selection = selection,
                            selectionKey = shortcut.id,
                            // A shortcut is all its text: a tap picks it, as while selecting.
                            onClick = { selection.toggle(shortcut.id) },
                            onOpenOptions = null,
                            cover = { Icon(Icons.Filled.ShortText, contentDescription = null) },
                        )
                    }
                }
            }
        }

        if (isAdding) {
            SettingsTextFieldDialog(
                title = stringResource(R.string.download_shortcuts_add),
                initialValue = "",
                onConfirm = { content ->
                    viewModel.add(content)
                    isAdding = false
                },
                onDismiss = { isAdding = false },
            )
        }

        if (isDeleting) {
            val ids = selection.selectedKeys.filterIsInstance<Long>()
            SonaConfirmationDialog(
                title = stringResource(R.string.download_shortcuts_delete_title),
                message = context.resources.getQuantityString(R.plurals.download_shortcuts_delete_message, ids.size, ids.size),
                confirmLabel = stringResource(R.string.download_delete),
                successMessage = stringResource(R.string.download_deleted),
                failureMessage = stringResource(R.string.download_delete_failed),
                onDismiss = { isDeleting = false },
                operation = { onFinished ->
                    viewModel.delete(ids)
                    selection.clear()
                    onFinished(true)
                },
            )
        }
    }
}

@HiltViewModel
internal class ShortcutsViewModel @Inject constructor(private val dao: DownloadDao) : ViewModel() {
    val shortcuts: StateFlow<List<DownloadCommandShortcut>> = dao.observeShortcuts().map { rows -> rows.map(DownloadCommandShortcutEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(content: String) {
        content.trim().takeIf(String::isNotEmpty)?.let { viewModelScope.launch { dao.insertShortcuts(listOf(DownloadCommandShortcutEntity(content = it))) } }
    }

    fun delete(ids: Collection<Long>) {
        viewModelScope.launch { dao.deleteShortcuts(ids) }
    }
}
