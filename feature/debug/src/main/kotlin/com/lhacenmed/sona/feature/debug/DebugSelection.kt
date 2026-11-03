package com.lhacenmed.sona.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.navigation.LocalNavigator

/**
 * The bar of a screen whose rows can be selected and deleted, as the library's are: [title] and [actions] as usual, and
 * while [selection] holds anything, how many, Select all over [selectableKeys], and [onDelete].
 */
@Composable
internal fun DebugSelectionBar(
    title: String,
    selection: SelectionState,
    selectableKeys: List<Any>,
    onDelete: () -> Unit,
    actions: List<TopBarAction> = emptyList(),
) {
    val navigator = LocalNavigator.current
    SonaTopAppBar(
        title = title,
        onNavigateBack = navigator::back,
        actions = actions,
        selection = selection.toTopBarSelection(
            actions = listOf(
                selection.selectAllAction(selectableKeys),
                TopBarAction(label = "Delete", icon = Icons.Filled.Delete, onClick = onDelete),
            ),
            onMoreOptions = null,
        ),
    )
}

/** Asks before [delete] takes [what] for good - the one way anything is deleted here, with its way out. */
@Composable
internal fun DeleteConfirmation(what: String, onDismiss: () -> Unit, delete: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    SonaConfirmationDialog(
        title = "Delete $what?",
        message = "This cannot be undone.",
        confirmLabel = "Delete",
        successMessage = "Deleted $what",
        failureMessage = "Could not delete $what",
        onDismiss = onDismiss,
        operation = { onFinished -> scope.launchOperation(onFinished) { delete() } },
    )
}

/** What stands for a row that has no picture: [icon] in a circle, a check while [isSelected]. */
@Composable
internal fun DebugRowIcon(icon: ImageVector, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.Check else icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
