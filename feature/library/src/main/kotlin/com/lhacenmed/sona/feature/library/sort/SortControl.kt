package com.lhacenmed.sona.feature.library.sort

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortableList
import kotlinx.coroutines.flow.StateFlow

/** One list's sort as its screen drives it: what it can be sorted by, its order now, and how to change it. */
class SortControl internal constructor(
    val criteria: List<SortCriterion>,
    val order: StateFlow<SortOrder>,
    val onApply: (SortOrder) -> Unit,
)

internal fun LibrarySortOrders.control(list: SortableList) = SortControl(
    criteria = criteria(list),
    order = order(list),
    onApply = { order -> setOrder(list, order) },
)

/** The button that opens a list's [SortSheet]. Every sortable screen uses this one. */
internal fun sortAction(onClick: () -> Unit) = TopBarAction(
    label = "Sort",
    icon = Icons.AutoMirrored.Filled.Sort,
    onClick = onClick,
)
