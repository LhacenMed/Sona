package com.lhacenmed.sona.feature.library.sort

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortScope
import com.lhacenmed.sona.core.model.sort.SortTarget
import com.lhacenmed.sona.core.model.sort.SortableList
import kotlinx.coroutines.flow.Flow

/** One list's sort as its screen drives it: what it can be sorted by, its order now, and how to change it. */
class SortControl internal constructor(
    private val sortOrders: LibrarySortOrders,
    /** Which list this sorts - and, where there are many of its kind, which one of them. */
    val target: SortTarget,
) {
    val criteria: List<SortCriterion> = sortOrders.criteria(target.list)

    /** Whether the list can be dragged into an order of its own - every collection's tracks can. */
    val canArrange: Boolean = SortCriterion.CUSTOM in criteria

    fun currentOrder(): SortOrder = sortOrders.currentOrder(target)

    /** The list's order, now and whenever that changes. */
    val order: Flow<SortOrder> = sortOrders.order(target)

    /** The custom order picking Custom from [order] lands on - see [LibrarySortOrders.customOrderFrom]. */
    fun customOrderFrom(order: SortOrder): SortOrder = sortOrders.customOrderFrom(order)

    /** How far a sort chosen here reaches until the user says otherwise. */
    val defaultScope: SortScope get() = sortOrders.defaultScope

    fun apply(order: SortOrder, scope: SortScope) = sortOrders.setOrder(target, order, scope)

    /** Keeps [trackIds] as this list's own order and puts it in it - what a drag's drop does. */
    fun arrange(trackIds: List<Long>) = sortOrders.arrange(target, trackIds)
}

internal fun LibrarySortOrders.control(list: SortableList, instanceId: String? = null): SortControl =
    SortControl(this, SortTarget(list, instanceId))

/** The button that opens a list's [SortSheet]. Every sortable screen uses this one. */
internal fun sortAction(onClick: () -> Unit) = TopBarAction(
    label = "Sort",
    icon = Icons.AutoMirrored.Filled.Sort,
    onClick = onClick,
)
