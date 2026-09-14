package com.lhacenmed.sona.core.data.sort

import com.lhacenmed.sona.core.common.sort.SortKey
import com.lhacenmed.sona.core.common.sort.sortKeyFactory
import com.lhacenmed.sona.core.model.sort.SortDirection

/**
 * One value a list can be ordered by, read off each item.
 *
 * Names and numbers are kept apart because they compare differently: a name goes through the
 * library's natural name sorting, a number compares as a number. A missing number - a track with no
 * year - is not a zero, and sorts after every present one whichever way the list runs.
 */
internal sealed interface SortField<in T> {
    class Name<in T>(val read: (T) -> String) : SortField<T>

    class Number<in T>(val read: (T) -> Long?) : SortField<T>
}

/**
 * Sorts by [fields]: the first decides the order, in [direction]; each later one only breaks the ties
 * those before it left, and always ascending - reversing "Artist" should list artists Z to A, not play
 * every one of their albums backwards.
 *
 * Every item's keys are computed once, up front, and each distinct name is tokenised once however many
 * items share it: sorting tracks by artist tokenises each artist once, not once per track.
 */
internal fun <T> List<T>.sortedByFields(
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
                is SortField.Name -> field.read(item).let { name -> nameKeys.getOrPut(name) { sortKeyOf(name) } }
                is SortField.Number -> field.read(item)
            }
        }
    }
        .sortedWith { (_, keys), (_, otherKeys) -> compareKeys(keys, otherKeys, direction) }
        .map { it.first }
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
