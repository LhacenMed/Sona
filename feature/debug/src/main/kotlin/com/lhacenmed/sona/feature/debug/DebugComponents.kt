@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.debug

import android.database.Cursor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SELECTED_ROW_TINT_ALPHA
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.dragSelection
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.rememberDragSelection
import com.lhacenmed.sona.core.designsystem.component.selectableRow
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes

/** How many rows a table shows at a time. */
internal const val PAGE_SIZE = 100

private val CellWidth = 180.dp

/** A row of a table: [cells] in its columns' order, and the [rowId] that names it where it can be changed. */
internal data class TableRow(val rowId: Long?, val cells: List<String?>)

internal data class RowPage(val columns: List<String>, val rows: List<TableRow>, val hasMore: Boolean)

/** A cell as text - a blob by its size, as bytes say nothing on a screen. */
internal fun Cursor.cellText(column: Int): String? = when (getType(column)) {
    Cursor.FIELD_TYPE_NULL -> null
    Cursor.FIELD_TYPE_BLOB -> "<blob ${getBlob(column).size} bytes>"
    else -> getString(column)
}

/**
 * What [load] gives - null until it has, then its result, a failure included, for a tool to show rather than to hide.
 * Loaded again whenever a key changes.
 */
@Composable
internal fun <T> rememberLoaded(vararg keys: Any?, load: suspend () -> T): Result<T>? =
    produceState<Result<T>?>(null, *keys) { value = runCatching { load() } }.value

/** [result] drawn by [content], once there is one: a failure says what it was. */
@Composable
internal fun <T> Loaded(result: Result<T>?, content: @Composable (T) -> Unit) {
    when {
        result == null -> Message("Loading...")
        result.isSuccess -> content(result.getOrThrow())
        else -> Message(result.exceptionOrNull().toString())
    }
}

@Composable
internal fun Message(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(16.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

/**
 * [page]'s rows under their columns, scrolling sideways together - each opening through [onRowClick] where given, and
 * selected by a long press or a drag across them, by their row ids, where given a [selection].
 */
@Composable
internal fun DataTable(
    page: RowPage,
    modifier: Modifier = Modifier,
    selection: SelectionState? = null,
    onRowClick: ((TableRow) -> Unit)? = null,
) {
    val sideways = rememberScrollState()
    val listState = rememberLazyListState()
    val dragSelection = selection?.let {
        rememberDragSelection(it, listState, remember(page) { page.rows.mapNotNull(TableRow::rowId) })
    }
    val selectedTint = MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_TINT_ALPHA)
    Column(modifier = modifier) {
        Row(Modifier.horizontalScroll(sideways).padding(vertical = 8.dp)) {
            page.columns.forEach { column -> Cell(column, isHeader = true) }
        }
        HorizontalDivider()
        FastScroller(listState, Modifier.weight(1f)) { overscrollEffect ->
            LazyColumn(
                state = listState,
                modifier = if (dragSelection != null) Modifier.fillMaxSize().dragSelection(dragSelection) else Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                overscrollEffect = overscrollEffect,
            ) {
                itemsIndexed(page.rows) { _, row ->
                    Row(
                        Modifier
                            .then(if (selection?.isSelected(row.rowId ?: Unit) == true) Modifier.background(selectedTint) else Modifier)
                            .horizontalScroll(sideways)
                            .then(
                                when {
                                    selection != null && row.rowId != null ->
                                        Modifier.selectableRow(selection, row.rowId, dragSelection, onClick = { onRowClick?.invoke(row) })
                                    onRowClick != null -> Modifier.clickable { onRowClick(row) }
                                    else -> Modifier
                                },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        row.cells.forEach { cell -> Cell(cell ?: "NULL", isHeader = false) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, isHeader: Boolean) {
    Text(
        text = text,
        modifier = Modifier.width(CellWidth).padding(horizontal = 8.dp),
        fontFamily = FontFamily.Monospace,
        fontWeight = if (isHeader) FontWeight.Bold else null,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Back and on through a table, with where it is between. */
@Composable
internal fun PageBar(offset: Int, rowCount: Int, hasMore: Boolean, onOffsetChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(shapes = buttonPressShapes(), enabled = offset > 0, onClick = { onOffsetChange((offset - PAGE_SIZE).coerceAtLeast(0)) }) { Text("Back") }
        Text(if (rowCount == 0) "No rows" else "${offset + 1} - ${offset + rowCount}", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(shapes = buttonPressShapes(), enabled = hasMore, onClick = { onOffsetChange(offset + PAGE_SIZE) }) { Text("Next") }
    }
}
