package com.lhacenmed.sona.core.designsystem.component.sheet

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaChoiceRow
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.theme.connectedLeadingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.connectedTrailingButtonPressShapes
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortScope
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** The button that opens a list's [SonaSortSheet]. Every sortable screen uses this one. */
@Composable
fun sortAction(onClick: () -> Unit) = TopBarAction(
    label = stringResource(R.string.sort_action),
    icon = SonaIcons.Sort,
    onClick = onClick,
)

/** What "this one" and "all of them" are called for a list there are many of - "This album", "All albums". */
class SortScopeLabels(@StringRes private val thisList: Int, @StringRes private val allLists: Int) {
    @Composable
    fun of(scope: SortScope): String = stringResource(
        when (scope) {
            SortScope.THIS_LIST -> thisList
            SortScope.ALL_LISTS -> allLists
        },
    )
}

/**
 * Choosing how a list is sorted: what by - one of [criteria] - then which way. Every sortable list in the app,
 * the library's and the Private Folder's, is sorted from this one sheet.
 *
 * Nothing changes until OK, so trying options on the way to the one wanted never reorders the list
 * behind the sheet, and OK only enables once the choice differs from [initialOrder], the order the sheet
 * opened on. Every row is always there and always enabled, so no pick moves or greys out the rest of the
 * sheet: Custom, where [criteria] offer it, only renames the second row, whose choice there is where tracks
 * added later go - starting from [initialNewTracksDirection].
 *
 * A list there are many of names its [scopeLabels], and asks how far the choice reaches, starting from
 * [defaultScope]; a list that is the only one of its kind has none, and never asks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaSortSheet(
    criteria: List<SortCriterion>,
    initialOrder: SortOrder,
    onApply: (order: SortOrder, scope: SortScope) -> Unit,
    onDismiss: () -> Unit,
    initialNewTracksDirection: SortDirection = SortDirection.ASCENDING,
    scopeLabels: SortScopeLabels? = null,
    defaultScope: SortScope = SortScope.THIS_LIST,
) {
    var criterion by remember { mutableStateOf(initialOrder.criterion) }
    // Custom's placement and the other criteria's direction are separate choices, each kept while the
    // other is tried, so hopping between Custom and a sort never changes what either was set to.
    var direction by remember {
        mutableStateOf(if (initialOrder.criterion == SortCriterion.CUSTOM) SortDirection.ASCENDING else initialOrder.direction)
    }
    var newTracksDirection by remember { mutableStateOf(initialNewTracksDirection) }
    var scope by remember { mutableStateOf(defaultScope) }
    val isCustom = criterion == SortCriterion.CUSTOM
    val chosenOrder = SortOrder(criterion, if (isCustom) newTracksDirection else direction)
    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.confirmation_dialog_cancel)
    val okLabel = stringResource(R.string.dialog_ok)

    SonaBottomSheet(title = stringResource(R.string.sort_by), onDismissRequest = onDismiss) {
        Column(modifier = Modifier.selectableGroup()) {
            criteria.forEach { option ->
                SonaChoiceRow(
                    label = option.label(),
                    selected = option == criterion,
                    onClick = { criterion = option },
                )
            }
        }

        Text(
            text = stringResource(if (isCustom) R.string.sort_new_tracks else R.string.sort_direction),
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
                    checked = option == chosenOrder.direction,
                    onCheckedChange = { if (isCustom) newTracksDirection = option else direction = option },
                    shapes = if (index == 0) {
                        connectedLeadingButtonPressShapes()
                    } else {
                        connectedTrailingButtonPressShapes()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                ) {
                    Text(if (isCustom) option.newTracksLabel() else option.label())
                }
            }
        }

        scopeLabels?.let { labels ->
            Text(
                text = stringResource(R.string.sort_apply_to),
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
                        Text(labels.of(option))
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
                actionButton(label = cancelLabel, onClick = { dismiss() })
                actionButton(
                    label = okLabel,
                    onClick = {
                        onApply(chosenOrder, scope)
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
private fun SortCriterion.label(): String = stringResource(
    when (this) {
        SortCriterion.CUSTOM -> R.string.sort_custom
        SortCriterion.NAME -> R.string.sort_name
        SortCriterion.ARTIST -> R.string.sort_artist
        SortCriterion.ALBUM -> R.string.sort_album
        SortCriterion.YEAR -> R.string.sort_year
        SortCriterion.DURATION -> R.string.sort_duration
        SortCriterion.TRACK_NUMBER -> R.string.sort_track_number
        SortCriterion.TRACK_COUNT -> R.string.sort_track_count
        SortCriterion.ALBUM_COUNT -> R.string.sort_album_count
        SortCriterion.DATE -> R.string.sort_date
        SortCriterion.DATE_ADDED -> R.string.sort_date_added
        SortCriterion.PLAY_COUNT -> R.string.sort_play_count
    },
)

@Composable
private fun SortDirection.label(): String = stringResource(
    when (this) {
        SortDirection.ASCENDING -> R.string.sort_ascending
        SortDirection.DESCENDING -> R.string.sort_descending
    },
)

/** Where a custom order going this way puts the tracks added to it - see [SortOrder]. */
@Composable
private fun SortDirection.newTracksLabel(): String = stringResource(
    when (this) {
        SortDirection.ASCENDING -> R.string.sort_new_tracks_bottom
        SortDirection.DESCENDING -> R.string.sort_new_tracks_top
    },
)
