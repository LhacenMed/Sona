package com.lhacenmed.sona.core.common.sort

import com.lhacenmed.sona.core.model.sort.SortDirection

/**
 * One value a list can be ordered by, read off each item - the library's lists' and the Private Folder's alike.
 *
 * Names and numbers are kept apart because they compare differently: a name goes through the
 * library's natural name sorting, a number compares as a number. A missing number - a track with no
 * year - is not a zero, and sorts after every present one whichever way the list runs.
 */
sealed interface SortField<in T> {
    /**
     * [isPlaceholder] marks a name the file never gave - "Unknown artist" and the like. Auxio's
     * `Name.Unknown` orders those before every real name, and so after them once the list is
     * reversed, which is what reading it as part of the name's own key gives.
     */
    class Name<in T>(
        val read: (T) -> String,
        val isPlaceholder: (T) -> Boolean = { false },
    ) : SortField<T>

    /**
     * [section] names the section a value falls in when the list is sorted by it - a year, a count - or
     * is null where the value has no section worth naming, like a position in a hand-made order.
     */
    class Number<in T>(
        val section: ((Long) -> String)? = null,
        val read: (T) -> Long?,
    ) : SortField<T>
}

/**
 * The section [item] sits in among items sorted by these fields - what a fast scroller's popup names it by, Auxio's
 * `getPopupData`. Read off the first field, the one the order is decided by, so it always agrees with the sort: a
 * name's initial as that name sorts, or a number's own section. Null where there is none: a missing year, a name the
 * file never gave, a field with no sections.
 */
fun <T> List<SortField<T>>.sectionOf(item: T, intelligentSorting: Boolean): String? =
    when (val field = firstOrNull()) {
        null -> null
        is SortField.Name -> if (field.isPlaceholder(item)) null else sectionInitial(field.read(item), intelligentSorting)
        is SortField.Number -> field.section?.let { section -> field.read(item)?.let(section) }
    }

/**
 * Whether items sorted by these fields fall into sections at all - not by a disc and track number, which name
 * nothing, where a popup would have no name to give any row.
 */
val <T> List<SortField<T>>.namesSections: Boolean
    get() = when (val field = firstOrNull()) {
        null -> false
        is SortField.Name -> true
        is SortField.Number -> field.section != null
    }

/**
 * Sorts by [fields]: the first decides the order, in [direction]; each later one only breaks the ties
 * those before it left, and always ascending - reversing "Artist" should list artists Z to A, not play
 * every one of their albums backwards.
 *
 * Every item's keys are computed once, up front, and each distinct name is tokenised once however many
 * items share it: sorting tracks by artist tokenises each artist once, not once per track.
 */
fun <T> List<T>.sortedByFields(
    fields: List<SortField<T>>,
    direction: SortDirection,
    intelligentSorting: Boolean,
): List<T> {
    if (size < 2) return this
    val sortKeyOf = sortKeyFactory(intelligentSorting)
    val nameKeys = HashMap<String, SortKey>()
    return map { item ->
        item to Array<Comparable<*>?>(fields.size) { index ->
            when (val field = fields[index]) {
                is SortField.Name -> {
                    val name = field.read(item)
                    NameKey(field.isPlaceholder(item), nameKeys.getOrPut(name) { sortKeyOf(name) })
                }
                is SortField.Number -> field.read(item)
            }
        }
    }
        .sortedWith { (_, keys), (_, otherKeys) -> compareKeys(keys, otherKeys, direction) }
        .map { it.first }
}

/** A name as it sorts: a placeholder before any real name, then the name itself. */
private class NameKey(private val isPlaceholder: Boolean, private val key: SortKey) : Comparable<NameKey> {
    override fun compareTo(other: NameKey): Int = when {
        isPlaceholder != other.isPlaceholder -> if (isPlaceholder) -1 else 1
        else -> key.compareTo(other.key)
    }
}

private fun compareKeys(
    keys: Array<Comparable<*>?>,
    otherKeys: Array<Comparable<*>?>,
    direction: SortDirection,
): Int {
    for (index in keys.indices) {
        val reversed = index == 0 && direction == SortDirection.DESCENDING
        val result = compareKey(keys[index], otherKeys[index], reversed)
        if (result != 0) return result
    }
    return 0
}

@Suppress("UNCHECKED_CAST")
private fun compareKey(key: Comparable<*>?, otherKey: Comparable<*>?, reversed: Boolean): Int = when {
    key == null && otherKey == null -> 0
    key == null -> 1
    otherKey == null -> -1
    reversed -> (otherKey as Comparable<Any>).compareTo(key)
    else -> (key as Comparable<Any>).compareTo(otherKey)
}
