package com.lhacenmed.sona.core.data.sort

import com.lhacenmed.sona.core.common.sort.SortField
import com.lhacenmed.sona.core.common.sort.namesSections
import com.lhacenmed.sona.core.common.sort.sectionOf
import com.lhacenmed.sona.core.common.sort.sortedByFields
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortableList

/**
 * Everything [list] can be sorted by, and how each choice orders it.
 *
 * The criteria the list offers are exactly the keys of [orderings], led by Custom where the list has an
 * [arrangement] - there is no second list of options to drift out of step with what can actually be
 * sorted - and their declaration order is the order the sort sheet lists them in.
 */
internal class SortSpec<T>(
    val list: SortableList,
    private val default: SortOrder,
    private val orderings: Map<SortCriterion, List<SortField<T>>>,
    private val arrangement: Arrangement<T>? = null,
) {
    /** What the sort sheet offers. */
    val criteria: List<SortCriterion> =
        listOfNotNull(SortCriterion.CUSTOM.takeIf { arrangement != null }) + orderings.keys

    /** [stored] if this list still offers it, otherwise the list's default. */
    fun resolve(stored: SortOrder?): SortOrder = stored?.takeIf { it.criterion in criteria } ?: default

    /**
     * The section [item] sits in when sorted by an [order] this spec [resolve]d - see [sectionOf]. None in an
     * order of the user's own.
     */
    fun section(item: T, order: SortOrder, intelligentSorting: Boolean): String? =
        orderings[order.criterion]?.sectionOf(item, intelligentSorting)

    /** Whether [order] puts items into sections - see [namesSections]. Never an order of the user's own. */
    fun namesSections(order: SortOrder): Boolean = orderings[order.criterion]?.namesSections == true

    /** Sorts [items] by an [order] this spec [resolve]d. */
    fun sort(items: List<T>, order: SortOrder, intelligentSorting: Boolean): List<T> =
        if (order.criterion == SortCriterion.CUSTOM) {
            checkNotNull(arrangement).sort(items, order.direction)
        } else {
            items.sortedByFields(orderings.getValue(order.criterion), order.direction, intelligentSorting)
        }
}

/**
 * How a list arranged by hand reads each item's [position] in that arrangement - null for an item that
 * joined the list since - and when it joined the list, [addedAt].
 */
internal class Arrangement<T>(
    private val position: (T) -> Int?,
    private val addedAt: (T) -> Long,
) {
    /**
     * The arranged items where they were put, and the items added since by when they were added: after
     * them, oldest first, for [SortDirection.ASCENDING]; before them, newest first, for
     * [SortDirection.DESCENDING]. Items added together keep the order they were added in.
     */
    fun sort(items: List<T>, newItems: SortDirection): List<T> {
        val (arranged, added) = items.partition { position(it) != null }
        val inArrangedOrder = arranged.sortedBy { position(it) }
        val oldestAddedFirst = added.sortedBy(addedAt)
        return when (newItems) {
            SortDirection.ASCENDING -> inArrangedOrder + oldestAddedFirst
            SortDirection.DESCENDING -> oldestAddedFirst.asReversed() + inArrangedOrder
        }
    }
}
