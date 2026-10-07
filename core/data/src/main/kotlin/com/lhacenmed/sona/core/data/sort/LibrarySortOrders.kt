package com.lhacenmed.sona.core.data.sort

import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.database.dao.ArrangementDao
import com.lhacenmed.sona.core.database.dao.PlaylistDao
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
 * The order every sortable list is in, and the one way to change it - by choosing a sort, or by
 * dragging a list into an order of its own.
 *
 * Each order is resolved against its list's [SortSpec] before anyone reads it, so the repository, a
 * screen and the sort sheet all see the same valid order - a stored choice the list no longer offers
 * simply reads as the list's default. Each is read from what is already in memory, so a list's first
 * sort is the one the user picked rather than a default corrected a moment later.
 */
@Singleton
class LibrarySortOrders @Inject constructor(
    private val sortSettings: SortSettings,
    private val arrangementDao: ArrangementDao,
    private val playlistDao: PlaylistDao,
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

    /** How far the sort sheet reaches until told otherwise - the user's choice in settings. */
    val defaultScope: SortScope get() = sortSettings.defaultScope.value

    /**
     * The custom order a list sorted by [order] moves into, which keeps placing new tracks as [order]
     * did where it says how: a list already custom keeps its placement, and one by date added keeps
     * the newest where they were - on top when descending, at the bottom when ascending. From any
     * other order, new tracks go where the user's setting puts them.
     */
    fun customOrderFrom(order: SortOrder): SortOrder = SortOrder(
        criterion = SortCriterion.CUSTOM,
        direction = when (order.criterion) {
            SortCriterion.CUSTOM, SortCriterion.DATE_ADDED -> order.direction
            else -> sortSettings.newTracksDirection.value
        },
    )

    /**
     * Saves [order] for [target], as far as [scope] reaches.
     *
     * Written from the application scope rather than the caller's: a sort is usually chosen right
     * before leaving the screen it was chosen on, and closing that screen must not cancel the write.
     */
    fun setOrder(target: SortTarget, order: SortOrder, scope: SortScope) {
        applicationScope.launch { sortSettings.setOrder(target, order, scope) }
    }

    /**
     * Makes [trackIds] - every track [target] shows, in the order a drag left them - its hand-made
     * order, and puts it in that order: see [customOrderFrom]. The arrangement is stored first, so the
     * switch can only ever land on the order this drag just made. A playlist counts as changed, as a
     * playlist does whenever its tracks move.
     */
    fun arrange(target: SortTarget, trackIds: List<Long>) {
        val instanceId = checkNotNull(target.instanceId) { "Only a collection's tracks can be arranged" }
        applicationScope.launch {
            arrangementDao.replace(target.list, instanceId, trackIds)
            if (target.list == SortableList.PLAYLIST_TRACKS) {
                playlistDao.setModifiedAt(instanceId.toLong(), System.currentTimeMillis())
            }
            sortSettings.setOrder(target, customOrderFrom(currentOrder(target)), SortScope.THIS_LIST)
        }
    }

    /**
     * Gives a list just made from a file the order it was written in: [trackIds] as its arrangement,
     * sorted by the file's [order] - or, where the file names none this list offers, kept in its own.
     */
    internal suspend fun restore(target: SortTarget, trackIds: List<Long>, order: SortOrder?) {
        arrangementDao.replace(target.list, checkNotNull(target.instanceId), trackIds)
        val restored = order?.takeIf { it.criterion in criteria(target.list) }
            ?: SortOrder(SortCriterion.CUSTOM, sortSettings.newTracksDirection.value)
        sortSettings.setOrder(target, restored, SortScope.THIS_LIST)
    }

    /** Drops the order chosen for [target], a list that no longer exists. Its arrangement goes with it. */
    internal suspend fun forget(target: SortTarget) {
        sortSettings.forget(target)
    }

    /** Puts every list back in its default order, and drops every hand-made one. */
    suspend fun reset() {
        sortSettings.resetOrders()
        arrangementDao.deleteAll()
    }
}
