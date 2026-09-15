package com.lhacenmed.sona.core.data.sort

import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortableList

/**
 * Everything [list] can be sorted by, and how each choice orders it.
 *
 * The criteria the list offers are exactly the keys of [orderings] - there is no second list of options
 * to drift out of step with what can actually be sorted - and their declaration order is the order the
 * sort sheet lists them in.
 */
internal class SortSpec<T>(
    val list: SortableList,
    private val default: SortOrder,
    private val orderings: Map<SortCriterion, List<SortField<T>>>,
) {
    val criteria: List<SortCriterion> = orderings.keys.toList()

    /** [stored] if this list still offers it, otherwise the list's default. */
    fun resolve(stored: SortOrder?): SortOrder = stored?.takeIf { it.criterion in orderings } ?: default

    /** Sorts [items] by an [order] this spec [resolve]d. */
    fun sort(items: List<T>, order: SortOrder, intelligentSorting: Boolean): List<T> =
        items.sortedByFields(orderings.getValue(order.criterion), order.direction, intelligentSorting)
}
