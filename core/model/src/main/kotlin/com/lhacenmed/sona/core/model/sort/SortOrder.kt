package com.lhacenmed.sona.core.model.sort

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

/** How one list is sorted: by what, and which way. */
data class SortOrder(
    val criterion: SortCriterion,
    val direction: SortDirection,
)
