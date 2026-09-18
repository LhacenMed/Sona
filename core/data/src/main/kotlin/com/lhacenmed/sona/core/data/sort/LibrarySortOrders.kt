package com.lhacenmed.sona.core.data.sort

import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.datastore.SortSettings
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortScope
import com.lhacenmed.sona.core.model.sort.SortTarget
import com.lhacenmed.sona.core.model.sort.SortableList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The order every sortable list is in, and the one way to change it.
 *
 * Each order is resolved against its list's [SortSpec] before anyone reads it, so the repository, a
 * screen and the sort sheet all see the same valid order - a stored choice the list no longer offers
 * simply reads as the list's default. Each is read from what is already in memory, so a list's first
 * sort is the one the user picked rather than a default corrected a moment later.
 */
@Singleton
class LibrarySortOrders @Inject constructor(
    private val sortSettings: SortSettings,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    /** How [target] is sorted, now and whenever that changes. */
    fun order(target: SortTarget): Flow<SortOrder> {
        val spec = LibrarySortSpecs.of(target.list)
        return sortSettings.order(target).flow.map(spec::resolve).distinctUntilChanged()
    }

    /** How [target] is sorted at this moment, for a sheet that opens on it. */
    fun currentOrder(target: SortTarget): SortOrder =
        LibrarySortSpecs.of(target.list).resolve(sortSettings.order(target).value)

    /** What [list] can be sorted by, in the order the sort sheet offers it. */
    fun criteria(list: SortableList): List<SortCriterion> = LibrarySortSpecs.of(list).criteria

    /**
     * Saves [order] for [target], as far as [scope] reaches.
     *
     * Written from the application scope rather than the caller's: a sort is usually chosen right
     * before leaving the screen it was chosen on, and closing that screen must not cancel the write.
     */
    fun setOrder(target: SortTarget, order: SortOrder, scope: SortScope) {
        applicationScope.launch { sortSettings.setOrder(target, order, scope) }
    }
}
