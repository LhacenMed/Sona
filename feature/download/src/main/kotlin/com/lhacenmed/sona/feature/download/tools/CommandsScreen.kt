package com.lhacenmed.sona.feature.download.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.model.DownloadCommand
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
 * Saved yt-dlp arguments - YTDLnis's command templates: each named, picked in a download's options to be given after
 * everything else. Added from the button, edited by a tap, deleted many at a time from a selection.
 */
data object CommandsScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: CommandsViewModel = hiltViewModel()
        val commands by viewModel.commands.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        var editing by remember { mutableStateOf<DownloadCommand?>(null) }
        var isAdding by rememberSaveable { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }

        SonaExtendedFloatingActionButtons(
            listOf(ExtendedFloatingActionButtonContent(label = stringResource(R.string.download_commands_add), icon = Icons.Filled.Add, onClick = { isAdding = true }, isPrimary = true)),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.download_commands_title),
                onNavigateBack = navigator::back,
                actions = listOf(TopBarAction(stringResource(R.string.download_terminal_title), Icons.Filled.Terminal) { navigator.go(TerminalScreen) }),
                selection = selection.toTopBarSelection(
                    actions = listOf(
                        selection.selectAllAction(commands.map(DownloadCommand::id)),
                        TopBarAction(stringResource(R.string.download_delete), Icons.Filled.DeleteForever) { isDeleting = true },
                    ),
                    onMoreOptions = null,
                ),
            )
            if (commands.isEmpty()) {
                SonaEmptyState(title = stringResource(R.string.download_commands_empty_title), message = stringResource(R.string.download_commands_empty_message))
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
                    items(commands, key = DownloadCommand::id) { command ->
                        SonaListRow(
                            title = command.name,
                            subtitle = command.arguments,
                            selection = selection,
                            selectionKey = command.id,
                            onClick = { editing = command },
                            onOpenOptions = null,
                            cover = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                        )
                    }
                }
            }
        }

        if (isAdding || editing != null) {
            CommandDialog(
                command = editing,
                onSave = { name, arguments -> viewModel.save(editing?.id, name, arguments) },
                onDismiss = {
                    isAdding = false
                    editing = null
                },
            )
        }

        if (isDeleting) {
            val ids = selection.selectedKeys.filterIsInstance<Long>()
            SonaConfirmationDialog(
                title = stringResource(R.string.download_commands_delete_title),
                message = context.resources.getQuantityString(R.plurals.download_commands_delete_message, ids.size, ids.size),
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

/** A command's name and arguments, written - a new one's while [command] is null. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommandDialog(command: DownloadCommand?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(command?.name.orEmpty()) }
    var arguments by remember { mutableStateOf(command?.arguments.orEmpty()) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (command == null) R.string.download_commands_add else R.string.download_commands_edit),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, enabled = name.isNotBlank() && arguments.isNotBlank(), onClick = {
                onSave(name.trim(), arguments.trim())
                onDismiss()
            })
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.download_commands_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = arguments,
                onValueChange = { arguments = it },
                label = { Text(stringResource(R.string.download_commands_arguments)) },
                placeholder = { Text("--embed-subs --sub-langs all") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@HiltViewModel
internal class CommandsViewModel @Inject constructor(private val dao: DownloadDao) : ViewModel() {
    val commands: StateFlow<List<DownloadCommand>> = dao.observeCommands().map { rows -> rows.map(DownloadCommandEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a command as [name] with [arguments] - [id]'s, or a new one while it is null. */
    fun save(id: Long?, name: String, arguments: String) {
        viewModelScope.launch {
            if (id == null) dao.insertCommand(DownloadCommandEntity(name = name, arguments = arguments)) else dao.updateCommand(id, name, arguments)
        }
    }

    fun delete(ids: Collection<Long>) {
        viewModelScope.launch { dao.deleteCommands(ids) }
    }
}
