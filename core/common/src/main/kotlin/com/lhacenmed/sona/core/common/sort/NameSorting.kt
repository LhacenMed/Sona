/*
 * Natural name sorting utilities.
 *
 * This is a precision port of the "intelligent"/"simple" name-sorting algorithm from the
 * Auxio music player (musikr module: tag/interpret/Naming.kt and tag/Name.kt), adapted to a
 * much simpler public API: a single [Comparator] over raw [String] names, with no separate
 * "sort" hint string, placeholder handling, or cached token lists per item.
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
fun nameComparator(intelligentSortingEnabled: Boolean): Comparator<String> =
    if (intelligentSortingEnabled) {
        Comparator { a, b -> compareTokenLists(intelligentTokens(a), intelligentTokens(b)) }
    } else {
        Comparator { a, b -> compareTokenLists(simpleTokens(a), simpleTokens(b)) }
    }

// region Shared collation

private val collator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
private val punctRegex by lazy { Regex("[\\p{Punct}+]") }
private val tokenRegex by lazy { Regex("(\\d+)|(\\D+)") }

/** Mirrors `org.oxycblt.musikr.tag.Token.Type`: numeric tokens always sort before lexicographic. */
private enum class TokenType {
    NUMERIC,
    LEXICOGRAPHIC,
}

/** Mirrors `org.oxycblt.musikr.tag.Token`. */
private class Token(val collationKey: CollationKey, val type: TokenType) : Comparable<Token> {
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
private fun compareTokenLists(a: List<Token>, b: List<Token>): Int {
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
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            Transliterator.getAvailableIDs().toList().contains("Any-Latin")
    ) {
        stripped = Transliterator.getInstance("Any-Latin;").transliterate(stripped)
    }

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
