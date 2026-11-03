@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.debug.database

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.debug.DataTable
import com.lhacenmed.sona.feature.debug.DebugSelectionBar
import com.lhacenmed.sona.feature.debug.DebugViewModel
import com.lhacenmed.sona.feature.debug.DeleteConfirmation
import com.lhacenmed.sona.feature.debug.Loaded
import com.lhacenmed.sona.feature.debug.Message
import com.lhacenmed.sona.feature.debug.PageBar
import com.lhacenmed.sona.feature.debug.TableRow
import com.lhacenmed.sona.feature.debug.rememberLoaded
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import kotlinx.coroutines.launch

/** Every database, each with its SQL console and its tables. */
data object DatabasesScreen : Screen {
    override fun title(context: Context): String = "Databases"

    @Composable
    override fun Content() {
        val viewModel = hiltViewModel<DebugViewModel>()
        val navigator = LocalNavigator.current
        SettingsList {
            Loaded(rememberLoaded(Unit) { viewModel.databases.files() }) { files ->
                files.forEach { file ->
                    SettingsSection(file.substringAfterLast('/')) {
                        SettingsNavigationItem(
                            title = "SQL console",
                            summary = file,
                            onClick = { navigator.go(SqlConsoleScreen(file)) },
                        )
                        Loaded(rememberLoaded(file) { viewModel.databases.tables(file) }) { tables ->
                            tables.forEach { table ->
                                SettingsNavigationItem(
                                    title = table.name,
                                    summary = "${table.rowCount} rows" + if (table.isView) " - view" else "",
                                    onClick = { navigator.go(TableScreen(file, table.name, table.isView, table.schema)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A table's rows, narrowed by a `WHERE` of the user's. A row opened shows every column in full, to change; rows selected
 * can be deleted. A view has no rows of its own to do either to.
 */
internal data class TableScreen(val file: String, val table: String, val isView: Boolean, val schema: String?) : Screen {

    @Composable
    override fun Content() {
        val viewModel = hiltViewModel<DebugViewModel>()
        val selection = rememberSelectionState()
        var filterDraft by rememberSaveable { mutableStateOf("") }
        var filter by rememberSaveable { mutableStateOf("") }
        var offset by rememberSaveable { mutableIntStateOf(0) }
        var version by remember { mutableIntStateOf(0) }
        var openRow by remember { mutableStateOf<TableRow?>(null) }
        var isSchemaShown by remember { mutableStateOf(false) }
        var isConfirmingDelete by remember { mutableStateOf(false) }
        val result = rememberLoaded(filter, offset, version) { viewModel.databases.page(file, table, isView, filter, offset) }
        val page = result?.getOrNull()

        Column(Modifier.fillMaxSize()) {
            DebugSelectionBar(
                title = table,
                selection = selection,
                selectableKeys = page?.rows?.mapNotNull(TableRow::rowId).orEmpty(),
                onDelete = { isConfirmingDelete = true },
                actions = listOf(TopBarAction(label = "Schema", icon = Icons.Filled.Code) { isSchemaShown = true }),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filterDraft,
                    onValueChange = { filterDraft = it },
                    label = { Text("WHERE") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(shapes = buttonPressShapes(), onClick = { filter = filterDraft; offset = 0 }) { Text("Apply") }
            }
            Loaded(result) { loaded ->
                PageBar(offset, loaded.rows.size, loaded.hasMore) { offset = it }
                DataTable(
                    page = loaded,
                    selection = selection.takeUnless { isView },
                    onRowClick = if (isView) null else { row -> openRow = row },
                )
            }
        }

        openRow?.let { row ->
            if (page != null) {
                RowDialog(
                    columns = page.columns,
                    row = row,
                    onSave = { changes ->
                        changes.forEach { (column, value) -> viewModel.databases.setCell(file, table, row.rowId!!, column, value) }
                    },
                    onDismiss = { openRow = null },
                    onSaved = {
                        openRow = null
                        version++
                    },
                )
            }
        }
        if (isConfirmingDelete) {
            DeleteConfirmation(
                what = "${selection.count} rows",
                onDismiss = { isConfirmingDelete = false },
                delete = {
                    viewModel.databases.deleteRows(file, table, selection.selectedKeys.filterIsInstance<Long>())
                    selection.clear()
                    version++
                },
            )
        }
        if (isSchemaShown) {
            SonaDialog(
                onDismissRequest = { isSchemaShown = false },
                title = table,
                buttons = { actionButton(label = "Close", onClick = { isSchemaShown = false }) },
            ) {
                Text(schema.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A row in full, each column editable. A failure stays on screen, with the row as it was left. */
@Composable
private fun RowDialog(
    columns: List<String>,
    row: TableRow,
    onSave: suspend (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val edits = remember(row) { mutableStateMapOf<String, String>() }
    var failure by remember(row) { mutableStateOf<String?>(null) }

    SonaDialog(
        onDismissRequest = onDismiss,
        title = "Row ${row.rowId}",
        buttons = {
            actionButton(label = "Cancel", onClick = onDismiss)
            actionButton(
                label = "Save",
                onClick = { scope.launch { runCatching { onSave(edits.toMap()) }.onSuccess { onSaved() }.onFailure { failure = it.toString() } } },
                enabled = edits.isNotEmpty(),
            )
        },
    ) {
        failure?.let { Message(it) }
        columns.forEachIndexed { index, column ->
            val original = row.cells[index]
            OutlinedTextField(
                value = edits[column] ?: original.orEmpty(),
                onValueChange = { edits[column] = it },
                label = { Text(column) },
                placeholder = { if (original == null) Text("NULL") },
                // What a blob holds is not text: only its size is here to be seen.
                readOnly = original?.startsWith("<blob ") == true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
    }
}

/** Any statement, on one database - a change reaches every screen showing it, as any other would. */
internal data class SqlConsoleScreen(val file: String) : Screen {
    override fun title(context: Context): String = "SQL - ${file.substringAfterLast('/')}"

    @Composable
    override fun Content() {
        val viewModel = hiltViewModel<DebugViewModel>()
        val scope = rememberCoroutineScope()
        var sql by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<Result<SqlResult>?>(null) }

        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = sql,
                onValueChange = { sql = it },
                label = { Text("SQL") },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Button(
                shapes = buttonPressShapes(),
                enabled = sql.isNotBlank(),
                onClick = { scope.launch { result = runCatching { viewModel.databases.run(file, sql) } } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Run") }
            result?.let { finished ->
                Loaded(finished) { outcome ->
                    when (outcome) {
                        is SqlResult.Rows -> DataTable(outcome.page)
                        is SqlResult.Changed -> Message("${outcome.rowCount} rows changed")
                    }
                }
            }
        }
    }
}
