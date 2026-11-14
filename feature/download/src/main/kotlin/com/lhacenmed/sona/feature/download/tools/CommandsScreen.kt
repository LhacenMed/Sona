package com.lhacenmed.sona.feature.download.tools

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import com.lhacenmed.sona.core.database.entity.DownloadCommandShortcutEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.database.entity.toEntity
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaGroupRow
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaRowGroup
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
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.model.DownloadCommand
import com.lhacenmed.sona.core.model.DownloadCommandShortcut
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * Saved yt-dlp arguments - YTDLnis's command templates: each named, picked in a download's options to be given after
 * everything else - or given by itself to every audio download, every video download and every link read, for the links it
 * is for; one preferred, picked for every download to begin with. Added from the button, edited by a tap, deleted many at a
 * time from a selection; written with shortcuts, and carried between devices as a file.
 */
data object CommandsScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: CommandsViewModel = hiltViewModel()
        val commands by viewModel.commands.collectAsStateWithLifecycle()
        val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        var editing by remember { mutableStateOf<DownloadCommand?>(null) }
        var isAdding by rememberSaveable { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }
        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CommandsFileType)) { uri -> uri?.let(viewModel::export) }
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }

        SonaExtendedFloatingActionButtons(
            listOf(ExtendedFloatingActionButtonContent(label = stringResource(R.string.download_commands_add), icon = SonaIcons.Add, onClick = { isAdding = true }, isPrimary = true)),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.download_commands_title),
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(stringResource(R.string.download_shortcuts_title), SonaIcons.ShortText) { navigator.go(ShortcutsScreen) },
                    TopBarAction(stringResource(R.string.download_commands_export), SonaIcons.Export) { exporter.launch(CommandsFileName) },
                    TopBarAction(stringResource(R.string.download_commands_import), SonaIcons.Import) { importer.launch(arrayOf(CommandsFileType, "text/plain", "application/octet-stream")) },
                    TopBarAction(stringResource(R.string.download_terminal_title), SonaIcons.Terminal) { navigator.go(TerminalScreen) },
                ),
                selection = selection.toTopBarSelection(
                    actions = listOf(
                        selection.selectAllAction(commands.map(DownloadCommand::id)),
                        TopBarAction(stringResource(R.string.download_delete), SonaIcons.Delete) { isDeleting = true },
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
                            subtitle = (listOf(command.arguments) + command.uses(context)).joinToString(InfoSeparator),
                            selection = selection,
                            selectionKey = command.id,
                            onClick = { editing = command },
                            onOpenOptions = null,
                            cover = { Icon(SonaIcons.Terminal, contentDescription = null) },
                        )
                    }
                }
            }
        }

        if (isAdding || editing != null) {
            CommandDialog(
                command = editing,
                shortcuts = shortcuts,
                onSave = viewModel::save,
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

/** What a saved file of commands is - and is called, to begin with. */
private const val CommandsFileType = "application/json"
private const val CommandsFileName = "sona-commands.json"

/** When a command is given by itself, as its row tells: "Audio", "Video", "Reading links", "Preferred". */
private fun DownloadCommand.uses(context: Context): List<String> = listOfNotNull(
    context.getString(R.string.download_commands_uses_audio).takeIf { addsToAudio },
    context.getString(R.string.download_commands_uses_video).takeIf { addsToVideo },
    context.getString(R.string.download_commands_uses_reading).takeIf { addsToReading },
    context.getString(R.string.download_commands_uses_preferred).takeIf { isPreferred },
)

/**
 * A command written - a new one while [command] is null: its name and arguments, [shortcuts] a tap from being added to
 * them; when it is given by itself; and the links it is for, one pattern a line.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommandDialog(command: DownloadCommand?, shortcuts: List<DownloadCommandShortcut>, onSave: (DownloadCommand) -> Unit, onDismiss: () -> Unit) {
    var edited by remember { mutableStateOf(command ?: DownloadCommand(id = 0, name = "", arguments = "")) }
    var patterns by remember { mutableStateOf(edited.urlPatterns.joinToString("\n")) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(if (command == null) R.string.download_commands_add else R.string.download_commands_edit),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, enabled = edited.name.isNotBlank() && edited.arguments.isNotBlank(), onClick = {
                onSave(edited.copy(name = edited.name.trim(), arguments = edited.arguments.trim(), urlPatterns = patterns.lines().map(String::trim).filter(String::isNotEmpty)))
                onDismiss()
            })
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = edited.name,
                onValueChange = { edited = edited.copy(name = it) },
                label = { Text(stringResource(R.string.download_commands_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = edited.arguments,
                onValueChange = { edited = edited.copy(arguments = it) },
                label = { Text(stringResource(R.string.download_commands_arguments)) },
                placeholder = { Text("--embed-subs --sub-langs all") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (shortcuts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shortcuts.forEach { shortcut ->
                        SuggestionChip(
                            onClick = { edited = edited.copy(arguments = listOf(edited.arguments.trimEnd(), shortcut.content).filter(String::isNotEmpty).joinToString(" ")) },
                            label = { Text(shortcut.content) },
                        )
                    }
                }
            }
            SonaRowGroup(
                listOf<SonaGroupRow>(
                    { shapes -> UseRow(R.string.download_commands_adds_audio, shapes, edited.addsToAudio) { edited = edited.copy(addsToAudio = it) } },
                    { shapes -> UseRow(R.string.download_commands_adds_video, shapes, edited.addsToVideo) { edited = edited.copy(addsToVideo = it) } },
                    { shapes -> UseRow(R.string.download_commands_adds_reading, shapes, edited.addsToReading) { edited = edited.copy(addsToReading = it) } },
                    { shapes -> UseRow(R.string.download_commands_preferred, shapes, edited.isPreferred) { edited = edited.copy(isPreferred = it) } },
                ),
            )
            OutlinedTextField(
                value = patterns,
                onValueChange = { patterns = it },
                label = { Text(stringResource(R.string.download_commands_patterns)) },
                placeholder = { Text("youtube\\.com\nsoundcloud\\.com") },
                supportingText = { Text(stringResource(R.string.download_commands_patterns_summary)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UseRow(label: Int, shapes: ListItemShapes, checked: Boolean, onCheckedChange: (Boolean) -> Unit) =
    SwitchRow(label = stringResource(label), icon = null, shapes = shapes, checked = checked, onCheckedChange = onCheckedChange)

@HiltViewModel
internal class CommandsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
) : ViewModel() {
    val commands: StateFlow<List<DownloadCommand>> = dao.observeCommands().map { rows -> rows.map(DownloadCommandEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shortcuts: StateFlow<List<DownloadCommandShortcut>> = dao.observeShortcuts().map { rows -> rows.map(DownloadCommandShortcutEntity::toDomain) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves [command] - a new one while its id is 0. */
    fun save(command: DownloadCommand) {
        viewModelScope.launch { dao.upsertCommand(command.toEntity()) }
    }

    fun delete(ids: Collection<Long>) {
        viewModelScope.launch { dao.deleteCommands(ids) }
    }

    /** Writes every command and shortcut into the file at [uri], as [import] reads them back. */
    fun export(uri: Uri) {
        viewModelScope.launch {
            val written = runCatching {
                val file = JSONObject()
                    .put("commands", JSONArray(dao.commands().map { it.toDomain().toJson() }))
                    .put("shortcuts", JSONArray(dao.shortcuts().map { it.content }))
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(file.toString(2).toByteArray()) } }
            }.isSuccess
            context.toast(if (written) R.string.download_commands_exported else R.string.download_commands_file_failed)
        }
    }

    /** Adds the commands and shortcuts the file at [uri] holds - as [export] writes them - beside those already saved. */
    fun import(uri: Uri) {
        viewModelScope.launch {
            val file = runCatching {
                JSONObject(withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty() })
            }.getOrNull()
            if (file == null) {
                context.toast(R.string.download_commands_file_failed)
                return@launch
            }
            val commands = file.optJSONArray("commands")?.let { array -> List(array.length()) { array.optJSONObject(it)?.toCommand() } }.orEmpty().filterNotNull()
            val shortcuts = file.optJSONArray("shortcuts")?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty().filter(String::isNotBlank)
            dao.insertCommands(commands.map { it.toEntity() })
            dao.insertShortcuts(shortcuts.map { DownloadCommandShortcutEntity(content = it) })
            context.toast(context.resources.getQuantityString(R.plurals.download_commands_imported, commands.size, commands.size))
        }
    }

    private fun DownloadCommand.toJson() = JSONObject()
        .put("name", name)
        .put("arguments", arguments)
        .put("addsToAudio", addsToAudio)
        .put("addsToVideo", addsToVideo)
        .put("addsToReading", addsToReading)
        .put("isPreferred", isPreferred)
        .put("urlPatterns", JSONArray(urlPatterns))

    private fun JSONObject.toCommand(): DownloadCommand? {
        val name = optString("name").takeIf(String::isNotBlank) ?: return null
        val arguments = optString("arguments").takeIf(String::isNotBlank) ?: return null
        val patterns = optJSONArray("urlPatterns")
        return DownloadCommand(
            id = 0,
            name = name,
            arguments = arguments,
            addsToAudio = optBoolean("addsToAudio"),
            addsToVideo = optBoolean("addsToVideo"),
            addsToReading = optBoolean("addsToReading"),
            isPreferred = optBoolean("isPreferred"),
            urlPatterns = patterns?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty().filter(String::isNotBlank),
        )
    }
}
