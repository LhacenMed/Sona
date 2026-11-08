package com.lhacenmed.sona.core.vault

import com.lhacenmed.sona.core.common.sort.SortField
import com.lhacenmed.sona.core.common.sort.sortedByFields
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.vault.data.VaultItem

/**
 * How the Private Folder's lists are sorted: by what a file kept on its own still knows of itself - its name,
 * its length, when it came in, and how often it was listened to through - each tie broken by its name, as the
 * library's lists break theirs. Newest first until the user chooses otherwise.
 */
internal object VaultSort {

    private val title = SortField.Name<VaultItem>({ it.title })

    private val orderings: Map<SortCriterion, List<SortField<VaultItem>>> = mapOf(
        SortCriterion.NAME to listOf(title),
        SortCriterion.DURATION to listOf(SortField.Number<VaultItem> { it.durationMs }, title),
        SortCriterion.DATE_ADDED to listOf(SortField.Number<VaultItem> { it.addedAt }, title),
        SortCriterion.PLAY_COUNT to listOf(SortField.Number<VaultItem> { it.playCount.toLong() }, title),
    )

    /** What the sort sheet offers, in the order it lists them. */
    val criteria: List<SortCriterion> = orderings.keys.toList()

    /** [stored] if it is still one the folder offers, otherwise newest first. */
    fun resolve(stored: SortOrder?): SortOrder =
        stored?.takeIf { it.criterion in orderings } ?: SortOrder(SortCriterion.DATE_ADDED, SortDirection.DESCENDING)

    fun sort(items: List<VaultItem>, order: SortOrder, intelligentSorting: Boolean): List<VaultItem> =
        items.sortedByFields(orderings.getValue(order.criterion), order.direction, intelligentSorting)
}
