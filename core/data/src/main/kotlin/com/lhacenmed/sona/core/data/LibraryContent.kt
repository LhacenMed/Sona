package com.lhacenmed.sona.core.data

/**
 * A library list plus the one thing a bare `List` cannot express: whether it has been read yet.
 *
 * "Empty" and "not loaded" look identical to a list but mean opposite things to a person. Conflating
 * them is what made Sona flash "No tracks found" (or nothing at all) over a library that was in fact
 * already on disk. [Loading] is the honest answer for the window before the first row arrives, and
 * screens render a placeholder for it rather than an empty-library message.
 */
sealed interface LibraryContent<out T> {
    data object Loading : LibraryContent<Nothing>

    data class Ready<T>(val items: List<T>) : LibraryContent<T>
}

/** The rows, or `null` while still loading. */
val <T> LibraryContent<T>.itemsOrNull: List<T>?
    get() = (this as? LibraryContent.Ready)?.items

/** The rows, treating "still loading" as empty - only for callers that cannot show a placeholder. */
val <T> LibraryContent<T>.itemsOrEmpty: List<T>
    get() = itemsOrNull.orEmpty()

val LibraryContent<*>.isLoading: Boolean
    get() = this is LibraryContent.Loading
