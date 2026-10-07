package com.lhacenmed.sona.core.model.sort

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

/**
 * How one list is sorted: by what, and which way.
 *
 * In the [SortCriterion.CUSTOM] order the arranged tracks keep the place they were dragged to, so the
 * direction says where the tracks added since go - the way their dates run: [SortDirection.ASCENDING]
 * after the arranged ones, newest last, and [SortDirection.DESCENDING] before them, newest first.
 */
data class SortOrder(
    val criterion: SortCriterion,
    val direction: SortDirection,
)
