package com.lhacenmed.sona.feature.library.sort

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortScope
import com.lhacenmed.sona.core.model.sort.SortTarget
import com.lhacenmed.sona.core.model.sort.SortableList

/** One list's sort as its screen drives it: what it can be sorted by, its order now, and how to change it. */
class SortControl internal constructor(
    /** Which list this sorts - and, where there are many of its kind, which one of them. */
    val target: SortTarget,
    val criteria: List<SortCriterion>,
    val currentOrder: () -> SortOrder,
    val onApply: (SortOrder, SortScope) -> Unit,
) {
    /**
     * Puts the list in the order the user has just arranged by hand.
     *
     * That order is never one of [criteria]: it is not somewhere to switch to, it is where dragging
     * leaves the list - so this is how a list enters it, and picking any criterion is how it leaves.
     * It reaches this one list only, an arrangement being this list's and no other's.
     */
    fun applyArrangedOrder() = onApply(
        SortOrder(SortCriterion.CUSTOM, SortDirection.ASCENDING),
        SortScope.THIS_LIST,
    )
}

internal fun LibrarySortOrders.control(list: SortableList, instanceId: String? = null): SortControl {
    val target = SortTarget(list, instanceId)
    return SortControl(
        target = target,
        criteria = criteria(list),
        currentOrder = { currentOrder(target) },
        onApply = { order, scope -> setOrder(target, order, scope) },
    )
}

/** The button that opens a list's [SortSheet]. Every sortable screen uses this one. */
internal fun sortAction(onClick: () -> Unit) = TopBarAction(
    label = "Sort",
    icon = Icons.AutoMirrored.Filled.Sort,
    onClick = onClick,
)
