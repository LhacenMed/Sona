package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Which rows a screen currently has selected, in the order they were selected - Auxio's `ListViewModel`
 * selection.
 *
 * Keys are opaque [Any] rather than a type parameter: [SonaTopAppBar] has no interest in *what* is
 * selected, only in how many rows there are and what can be done with them. The screens that select
 * give their keys a shape of their own, one that stays distinct across every kind of row a selection
 * can mix.
 */
@Stable
class SelectionState {

    var selectedKeys by mutableStateOf<Set<Any>>(emptySet())
        private set

    val count: Int get() = selectedKeys.size

    /** True once anything is selected - which is also what puts the top bar into its context mode. */
    val isActive: Boolean get() = selectedKeys.isNotEmpty()

    fun isSelected(key: Any): Boolean = key in selectedKeys

    fun toggle(key: Any) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    /** Adds [keys] after whatever is already selected, leaving every other selected row where it is. */
    fun selectAll(keys: Collection<Any>) {
        selectedKeys = selectedKeys + keys
    }

    /** Removes [keys], leaving every other selected row where it is. */
    fun deselectAll(keys: Collection<Any>) {
        selectedKeys = selectedKeys - keys.toSet()
    }

    fun clear() {
        selectedKeys = emptySet()
    }
}

@Composable
fun rememberSelectionState(): SelectionState = remember { SelectionState() }

/**
 * How this selection looks to [SonaTopAppBar]: null while nothing is selected, which is precisely
 * what leaves the bar in its ordinary mode. Every selectable screen needs this same conversion, so
 * it lives here rather than as a null check repeated at each one.
 */
fun SelectionState.toTopBarSelection(actions: List<TopBarAction>, onMoreOptions: () -> Unit): TopBarSelection? =
    if (!isActive) {
        null
    } else {
        TopBarSelection(count = count, actions = actions, onMoreOptions = onMoreOptions, onDismiss = { clear() })
    }
