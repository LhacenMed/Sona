package com.lhacenmed.sona.feature.library.sort

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.theme.connectedLeadingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.connectedTrailingButtonPressShapes
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortScope
import com.lhacenmed.sona.core.model.sort.SortableList

/**
 * Choosing how a list is sorted: what by, then which way.
 *
 * Nothing changes until OK, so trying options on the way to the one wanted never reorders the list
 * behind the sheet, and OK only enables once the choice differs from the order the sheet opened on. A
 * criterion without a direction leaves the direction buttons where they are but disabled, so picking it
 * never moves the rest of the sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SortSheet(
    sort: SortControl,
    onDismiss: () -> Unit,
) {
    val initialOrder = remember { sort.currentOrder() }
    var criterion by remember { mutableStateOf(initialOrder.criterion) }
    var direction by remember { mutableStateOf(initialOrder.direction) }
    // A sort reaches the list it was chosen in and no further unless it is asked to, so one playlist
    // sorted by hand leaves every other playlist as it was.
    var scope by remember { mutableStateOf(SortScope.THIS_LIST) }
    // A criterion without a direction is always stored ascending, so switching away from one and
    // back cannot register as a change.
    val chosenOrder = SortOrder(
        criterion = criterion,
        direction = if (criterion.hasDirection) direction else SortDirection.ASCENDING,
    )

    SonaBottomSheet(title = "Sort by", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.selectableGroup()) {
            sort.criteria.forEach { option ->
                CriterionRow(
                    label = option.label(),
                    selected = option == criterion,
                    onClick = { criterion = option },
                )
            }
        }

        Text(
            text = "Direction",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            SortDirection.entries.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option == direction,
                    onCheckedChange = { direction = option },
                    enabled = criterion.hasDirection,
                    shapes = if (index == 0) {
                        connectedLeadingButtonPressShapes()
                    } else {
                        connectedTrailingButtonPressShapes()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                ) {
                    Text(option.label())
                }
            }
        }

        // Only a list there are many of has anywhere else to reach; the library's own lists are the
        // only list of their kind, so they never ask the question.
        sort.target.list.scopeLabels()?.let { scopeLabels ->
            Text(
                text = "Apply to",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                SortScope.entries.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = option == scope,
                        onCheckedChange = { scope = option },
                        shapes = if (index == 0) {
                            connectedLeadingButtonPressShapes()
                        } else {
                            connectedTrailingButtonPressShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                    ) {
                        Text(scopeLabels.of(option))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            SonaActionButtonGroup {
                actionButton(label = "Cancel", onClick = { dismiss() })
                actionButton(
                    label = "OK",
                    onClick = {
                        sort.onApply(chosenOrder, scope)
                        dismiss()
                    },
                    // Sending the order this list already has out to every list of its kind is a change
                    // worth making, even though the order itself is not changing.
                    enabled = chosenOrder != initialOrder || scope == SortScope.ALL_LISTS,
                )
            }
        }
    }
}

@Composable
private fun CriterionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 16.dp))
    }
}

private fun SortCriterion.label(): String = when (this) {
    SortCriterion.CUSTOM -> "Custom"
    SortCriterion.NAME -> "Name"
    SortCriterion.ARTIST -> "Artist"
    SortCriterion.ALBUM -> "Album"
    SortCriterion.YEAR -> "Year"
    SortCriterion.DURATION -> "Duration"
    SortCriterion.TRACK_NUMBER -> "Track number"
    SortCriterion.TRACK_COUNT -> "Track count"
    SortCriterion.ALBUM_COUNT -> "Album count"
    SortCriterion.DATE -> "Date"
    SortCriterion.DATE_ADDED -> "Date added"
}

private fun SortDirection.label(): String = when (this) {
    SortDirection.ASCENDING -> "Ascending"
    SortDirection.DESCENDING -> "Descending"
}

/** What "this one" and "all of them" are called, or null for a list that is the only one of its kind. */
private fun SortableList.scopeLabels(): ScopeLabels? = when (this) {
    SortableList.ALBUM_TRACKS -> ScopeLabels("This album", "All albums")
    SortableList.ARTIST_TRACKS -> ScopeLabels("This artist", "All artists")
    SortableList.GENRE_TRACKS -> ScopeLabels("This genre", "All genres")
    SortableList.FOLDER_TRACKS -> ScopeLabels("This folder", "All folders")
    SortableList.PLAYLIST_TRACKS -> ScopeLabels("This playlist", "All playlists")
    SortableList.TRACKS,
    SortableList.ALBUMS,
    SortableList.ARTISTS,
    SortableList.GENRES,
    SortableList.FOLDERS,
    SortableList.PLAYLISTS,
    -> null
}

private class ScopeLabels(private val thisList: String, private val allLists: String) {
    fun of(scope: SortScope): String = when (scope) {
        SortScope.THIS_LIST -> thisList
        SortScope.ALL_LISTS -> allLists
    }
}
