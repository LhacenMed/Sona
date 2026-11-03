package com.lhacenmed.sona.feature.debug.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalDragSelection
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberDragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.debug.DebugRowIcon
import com.lhacenmed.sona.feature.debug.DebugSelectionBar
import com.lhacenmed.sona.feature.debug.DebugViewModel
import com.lhacenmed.sona.feature.debug.DeleteConfirmation
import com.lhacenmed.sona.feature.debug.Loaded
import com.lhacenmed.sona.feature.debug.Message
import com.lhacenmed.sona.feature.debug.rememberLoaded
import kotlinx.coroutines.launch

private fun sectionKeyOf(file: PreferenceFile) = "${file.kind}:${file.name}"

/** A value of a file, as a row of this screen selects and opens it. */
private data class PreferenceValue(val file: PreferenceFile, val key: String, val value: Any)

/**
 * Every key-value file, a section of its own: a value opened can be changed, and values selected - a whole file's, with
 * its section's button - removed, so each goes back to its default. What a folded section holds is out of any selection.
 */
data object PreferencesScreen : Screen {

    @Composable
    override fun Content() {
        val inspector = hiltViewModel<DebugViewModel>().preferences
        val selection = rememberSelectionState()
        val sections = rememberSectionListState()
        val listState = rememberLazyListState()
        var version by remember { mutableIntStateOf(0) }
        var editing by remember { mutableStateOf<PreferenceValue?>(null) }
        var isConfirmingDelete by remember { mutableStateOf(false) }
        val files = rememberLoaded(version) { inspector.files() }
        val values = remember(files) {
            files?.getOrNull().orEmpty().flatMap { file -> file.values.toSortedMap().map { (key, value) -> PreferenceValue(file, key, value) } }
        }
        val shownValues = remember(values, sections.collapsedKeys) { values.filterNot { sections.isCollapsed(sectionKeyOf(it.file)) } }
        val dragSelection = rememberDragSelection(selection, listState, shownValues)

        Column(Modifier.fillMaxSize()) {
            DebugSelectionBar(
                title = "Preferences",
                selection = selection,
                selectableKeys = shownValues,
                onDelete = { isConfirmingDelete = true },
            )
            Loaded(files) { loaded ->
                CompositionLocalProvider(LocalDragSelection provides dragSelection) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().dragSelection(dragSelection),
                        contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                    ) {
                        loaded.forEachIndexed { index, file ->
                            val fileValues = values.filter { it.file == file }
                            section(
                                key = sectionKeyOf(file),
                                title = "${file.kind.label}: ${file.name} (${file.values.size})",
                                state = sections,
                                actions = listOf(TopBarAction(label = "Select this file", icon = Icons.Filled.SelectAll) { selection.selectAll(fileValues) }),
                                hasDividerAbove = index > 0,
                            ) {
                                items(fileValues, key = { "${sectionKeyOf(file)}:${it.key}" }) { item ->
                                    SonaListRow(
                                        title = item.key,
                                        subtitle = "${item.value::class.simpleName}: ${preferenceText(item.value)}",
                                        selection = selection,
                                        selectionKey = item,
                                        onClick = { editing = item },
                                        onOpenOptions = null,
                                        cover = { isSelected -> DebugRowIcon(Icons.Filled.Tune, isSelected) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        editing?.let { item ->
            ValueDialog(
                item = item,
                onSave = { text -> inspector.set(item.file, item.key, item.value, text) },
                onDismiss = { editing = null },
                onSaved = {
                    editing = null
                    version++
                },
            )
        }
        if (isConfirmingDelete) {
            DeleteConfirmation(
                what = "${selection.count} values",
                onDismiss = { isConfirmingDelete = false },
                delete = {
                    selection.selectedKeys.filterIsInstance<PreferenceValue>().forEach { inspector.remove(it.file, it.key) }
                    selection.clear()
                    version++
                },
            )
        }
    }

    @Composable
    private fun ValueDialog(item: PreferenceValue, onSave: suspend (String) -> Unit, onDismiss: () -> Unit, onSaved: () -> Unit) {
        val scope = rememberCoroutineScope()
        var text by remember(item) { mutableStateOf(preferenceText(item.value)) }
        var failure by remember(item) { mutableStateOf<String?>(null) }

        SonaDialog(
            onDismissRequest = onDismiss,
            title = item.key,
            buttons = {
                actionButton(label = "Cancel", onClick = onDismiss)
                actionButton(
                    label = "Save",
                    onClick = { scope.launch { runCatching { onSave(text) }.onSuccess { onSaved() }.onFailure { failure = it.toString() } } },
                )
            },
        ) {
            failure?.let { Message(it) }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(item.value::class.simpleName.orEmpty()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
