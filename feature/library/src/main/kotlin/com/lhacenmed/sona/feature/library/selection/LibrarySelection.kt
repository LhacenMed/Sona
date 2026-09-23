package com.lhacenmed.sona.feature.library.selection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSelection
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.navigation.NavigateAwayEffect
import com.lhacenmed.sona.feature.library.options.OptionsActionsViewModel
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

/**
 * The bar every library list shows while it has rows selected - Auxio's selection toolbar.
 *
 * [selectAllAction] over [listKeys], this list's own rows, comes first. The list's own [actions]
 * follow, then the more options button, which [onMoreOptions] answers with the selection's options sheet.
 */
internal fun SelectionState.toLibraryTopBarSelection(
    listKeys: () -> List<SelectionKey>,
    actions: List<TopBarAction>,
    onMoreOptions: () -> Unit,
): TopBarSelection? {
    if (!isActive) return null
    return toTopBarSelection(actions = listOf(selectAllAction(listKeys())) + actions, onMoreOptions = onMoreOptions)
}

/**
 * Select all over [listKeys], a list's own rows: it adds them after whatever else is selected, so a
 * selection gathered elsewhere survives it, and once every one of them is selected it deselects them
 * instead.
 */
internal fun SelectionState.selectAllAction(listKeys: List<SelectionKey>): TopBarAction =
    if (listKeys.isNotEmpty() && selectedKeys.containsAll(listKeys)) {
        TopBarAction(label = "Deselect all", icon = Icons.Filled.Deselect) { deselectAll(listKeys) }
    } else {
        TopBarAction(label = "Select all", icon = Icons.Filled.SelectAll) { selectAll(listKeys) }
    }

/**
 * What every selectable list shares beyond its rows and its bar: the options sheet its more options
 * button opens, and dropping the selection the moment the screen navigates elsewhere - Auxio's
 * `onExploreNavigate`. [content] is handed what opens the sheet.
 *
 * The sheet holds the tracks the selection stands for, in the order their rows were selected, and
 * choosing any of its actions ends the selection, as every one of Auxio's selection actions does.
 * Only dismissing the sheet leaves the selection as it was.
 */
@Composable
internal fun SelectionOptionsHost(
    selection: SelectionState,
    content: @Composable (openOptions: () -> Unit) -> Unit,
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    var target by remember { mutableStateOf<OptionsTarget.ForSelection?>(null) }

    NavigateAwayEffect { selection.clear() }

    content {
        actionsViewModel.resolveSelection(selection.selectedKeys.filterIsInstance<SelectionKey>()) { target = it }
    }

    target?.let {
        OptionsSheet(
            target = it,
            onDismissRequest = { target = null },
            onActionChosen = { selection.clear() },
        )
    }
}
