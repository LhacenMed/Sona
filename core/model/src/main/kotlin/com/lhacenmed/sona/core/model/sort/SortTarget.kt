package com.lhacenmed.sona.core.model.sort

/**
 * The one list a sort belongs to: a kind of list, and - for the kinds there can be many of - which one.
 *
 * The library's own lists are the only list of their kind, so they name no [instanceId]. A playlist, an
 * album, an artist, a genre or a folder is one of many, and names itself, so the order it is sorted by
 * can be its own rather than every other list's of that kind.
 */
data class SortTarget(
    val list: SortableList,
    val instanceId: String? = null,
)

/** How far a chosen sort order reaches. */
enum class SortScope {
    /** Only the list it was chosen in, leaving every other list of that kind as it was. */
    THIS_LIST,

    /** Every list of that kind, including the ones that had been given an order of their own. */
    ALL_LISTS,
}
