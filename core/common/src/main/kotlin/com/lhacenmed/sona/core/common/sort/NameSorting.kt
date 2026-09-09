/*
 * Natural name sorting utilities.
 *
 * This is a precision port of the "intelligent"/"simple" name-sorting algorithm from the
 * Auxio music player (musikr module: tag/interpret/Naming.kt and tag/Name.kt), adapted to a
 * much simpler public API: a [SortKey] per name, with no separate "sort" hint string and no
 * placeholder handling. Auxio's per-item token caching *is* kept - see [SortKey]/[sortedByName].
 */
package com.lhacenmed.sona.core.common.sort

import android.icu.text.Transliterator
import android.os.Build
import java.text.CollationKey
import java.text.Collator

/**
 * Returns a [Comparator] that compares names the way Auxio's music library sorting does.
 *
 * @param intelligentSortingEnabled when `true`, uses the "intelligent" algorithm: punctuation is
 *   turned into token boundaries, a single leading English article is dropped, non-Latin scripts
 *   are transliterated to Latin where possible (API 29+), and numeric runs are compared by
 *   magnitude rather than lexicographically (so "Track 9" sorts before "Track 10"). When `false`,
 *   uses the "simple" algorithm: punctuation is stripped and the whole string is compared as one
 *   locale-aware, case/accent-insensitive token.
 */
fun nameComparator(intelligentSortingEnabled: Boolean): Comparator<String> {
    val keyOf = sortKeyFactory(intelligentSortingEnabled)
    return Comparator { a, b -> keyOf(a).compareTo(keyOf(b)) }
}

/**
 * A name reduced to its comparable form once, up front.
 *
 * Tokenizing is by far the expensive half of this algorithm - collation keys, and on API 29+ a
 * full ICU transliteration pass. A plain [Comparator] does that work *inside every comparison*, so
 * sorting n names tokenizes O(n log n) times: a 5,000-track library pays ~120,000 transliterations
 * to produce 5,000 distinct keys. Auxio (which this file is ported from) sidesteps that by holding
 * one token list per music object and comparing the cached lists. [SortKey] is that same idea, and
 * [sortedByName] is how library code should apply it.
 */
class SortKey internal constructor(private val tokens: List<Token>) : Comparable<SortKey> {
    override fun compareTo(other: SortKey): Int = compareTokenLists(tokens, other.tokens)
}

/** Returns the function that reduces a raw name to its [SortKey] under the given sorting mode. */
fun sortKeyFactory(intelligentSortingEnabled: Boolean): (String) -> SortKey =
    if (intelligentSortingEnabled) {
        { name -> SortKey(intelligentTokens(name)) }
    } else {
        { name -> SortKey(simpleTokens(name)) }
    }

/**
 * Sorts by name, tokenizing each element exactly once (a Schwartzian transform).
 *
 * This is the entry point library code should use; [nameComparator] is kept for one-off comparisons
 * and re-tokenizes on every call.
 */
fun <T> List<T>.sortedByName(
    intelligentSortingEnabled: Boolean,
    selector: (T) -> String,
): List<T> {
    if (size < 2) return this
    val keyOf = sortKeyFactory(intelligentSortingEnabled)
    return map { it to keyOf(selector(it)) }
        .sortedBy { it.second }
        .map { it.first }
}

// region Shared collation

// java.text.Collator is explicitly documented as not thread-safe, and sorting now runs off the main
// thread on a shared dispatcher - so every worker thread gets its own instance instead of racing on
// one shared object.
private val collators = object : ThreadLocal<Collator>() {
    override fun initialValue(): Collator =
        Collator.getInstance().apply { strength = Collator.PRIMARY }
}

private val collator: Collator get() = collators.get()!!

// Resolved once per process: getAvailableIDs() enumerates every ICU transform installed on the
// device and getInstance() compiles a rule set - both were being paid once per name tokenized.
private val latinTransliterator: Transliterator? by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        null
    } else {
        runCatching {
            if (Transliterator.getAvailableIDs().asSequence().contains("Any-Latin")) {
                Transliterator.getInstance("Any-Latin;")
            } else {
                null
            }
        }.getOrNull()
    }
}
private val punctRegex by lazy { Regex("[\\p{Punct}+]") }
private val tokenRegex by lazy { Regex("(\\d+)|(\\D+)") }

/** Mirrors `org.oxycblt.musikr.tag.Token.Type`: numeric tokens always sort before lexicographic. */
internal enum class TokenType {
    NUMERIC,
    LEXICOGRAPHIC,
}

/** Mirrors `org.oxycblt.musikr.tag.Token`. */
internal class Token(val collationKey: CollationKey, val type: TokenType) : Comparable<Token> {
    override fun compareTo(other: Token): Int {
        // Numeric tokens should always be lower than lexicographic tokens.
        val modeComp = type.compareTo(other.type)
        if (modeComp != 0) {
            return modeComp
        }

        // Numeric strings must be ordered by magnitude, thus immediately short-circuit
        // the comparison if the lengths do not match.
        if (
            type == TokenType.NUMERIC &&
                collationKey.sourceString.length != other.collationKey.sourceString.length
        ) {
            return collationKey.sourceString.length - other.collationKey.sourceString.length
        }

        return collationKey.compareTo(other.collationKey)
    }
}

/** Mirrors `Name.Known.compareTo`: compare shared tokens in order, then shorter list first. */
internal fun compareTokenLists(a: List<Token>, b: List<Token>): Int {
    val result =
        a.zip(b).fold(0) { acc, (token, otherToken) ->
            acc.takeIf { it != 0 } ?: token.compareTo(otherToken)
        }
    return if (result != 0) result else a.size.compareTo(b.size)
}

// endregion

// region Simple sorting (mirrors SimpleKnownName.parseToken)

private fun simpleTokens(name: String): List<Token> {
    // Remove excess punctuation from the string, as those usually aren't considered in sorting.
    val stripped = name.replace(punctRegex, "").trim().ifEmpty { name }
    val collationKey = collator.getCollationKey(stripped)
    // Always use lexicographic mode since we aren't parsing any numeric components.
    return listOf(Token(collationKey, TokenType.LEXICOGRAPHIC))
}

// endregion

// region Intelligent sorting (mirrors IntelligentKnownName.parseTokens)

private fun intelligentTokens(name: String): List<Token> {
    var stripped =
        name
            // Replace punctuation with spaces to create token boundaries, improving
            // sorting of names like "15-9" vs "15-10".
            .replace(punctRegex, " ")
            .let { if (it.isBlank()) name else it }
            .run {
                // Strip any english articles like "the" or "an" from the start, as music
                // sorting should ignore such when possible.
                when {
                    length > 4 && startsWith("the ", ignoreCase = true) -> substring(4)
                    length > 3 && startsWith("an ", ignoreCase = true) -> substring(3)
                    length > 2 && startsWith("a ", ignoreCase = true) -> substring(2)
                    else -> this
                }
            }

    // Transliterate to latin if available.
    latinTransliterator?.let { stripped = it.transliterate(stripped) }

    // To properly compare numeric components in names, we have to split them up into
    // individual lexicographic and numeric tokens and then individually compare them
    // with special logic.
    return tokenRegex.findAll(stripped).mapTo(mutableListOf()) { match ->
        // Remove excess whitespace where possible.
        val token = match.value.trim().ifEmpty { match.value }
        val collationKey: CollationKey
        val type: TokenType
        // Separate each token into their numeric and lexicographic counterparts.
        if (token.first().isDigit()) {
            // The digit string comparison breaks with preceding zero digits, remove those.
            val digits = token.trimStart { Character.getNumericValue(it) == 0 }.ifEmpty { token }
            // Other languages have other types of digit strings, still use collation keys.
            collationKey = collator.getCollationKey(digits)
            type = TokenType.NUMERIC
        } else {
            collationKey = collator.getCollationKey(token)
            type = TokenType.LEXICOGRAPHIC
        }
        Token(collationKey, type)
    }
}

// endregion
