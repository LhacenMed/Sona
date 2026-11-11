package com.lhacenmed.sona.feature.library.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSelection
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.navigation.NavigateAwayEffect
import com.lhacenmed.sona.feature.library.options.OptionsAction
import com.lhacenmed.sona.feature.library.options.OptionsFollowUps
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.options.rememberOptionsActions

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
 * What every selectable list shares beyond its rows and its bar: the options sheet its more options
 * button opens, and dropping the selection the moment the screen navigates elsewhere - Auxio's
 * `onExploreNavigate`. [content] is handed what opens the sheet, and [barActions] as the bar's own buttons.
 *
 * The sheet holds the tracks the selection stands for, in the order their rows were selected, and
 * any of its actions done ends the selection, as every one of Auxio's selection actions does - one that
 * asks first, once confirmed. [barActions] are the sheet's own, brought up to the bar - asked and done the same
 * way - and so left out of the sheet: each is offered once. Dismissing the sheet, or what it asks, leaves the
 * selection as it was.
 */
@Composable
internal fun SelectionOptionsHost(
    selection: SelectionState,
    barActions: List<OptionsAction> = emptyList(),
    content: @Composable (openOptions: () -> Unit, barActions: List<TopBarAction>) -> Unit,
) {
    val actions = rememberOptionsActions()
    var target by remember { mutableStateOf<OptionsTarget.ForSelection?>(null) }

    NavigateAwayEffect { selection.clear() }

    fun resolved(onResolved: (OptionsTarget.ForSelection) -> Unit) =
        actions.viewModel.resolveSelection(selection.selectedKeys.filterIsInstance<SelectionKey>(), onResolved)

    content(
        { resolved { target = it } },
        barActions.map { action ->
            TopBarAction(label = action.label, icon = action.icon) { resolved { actions.perform(it, action) } }
        },
    )

    target?.let {
        OptionsSheet(
            target = it,
            onDismissRequest = { target = null },
            onActionDone = { selection.clear() },
            hiddenActions = barActions.toSet(),
        )
    }
    OptionsFollowUps(actions = actions, onActionDone = { selection.clear() })
}
