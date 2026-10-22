package com.lhacenmed.sona.core.data.search

import java.text.Normalizer

/**
 * [text] as every search in Sona compares it: lower case, its accents and Arabic vowel marks dropped, an
 * apostrophe closed up and any other punctuation a space. So "Mr. Feel", "mr feel" and "MR-FEEL" read the
 * same, "Beyoncé" is found by "beyonce", "don't" by "dont", and "أعلى" by "اعلى".
 *
 * Decomposing first is what strips the marks: an accented letter, and Arabic's alef or waw carrying a
 * hamza, each come apart into the bare letter and a mark that is then left out.
 */
fun searchFormOf(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
    val form = StringBuilder(decomposed.length)
    var isAfterWord = false
    for (char in decomposed) {
        val letter = char.lowercaseChar().foldedArabic()
        when {
            Character.getType(letter) == Character.NON_SPACING_MARK.toInt() || letter == ARABIC_TATWEEL -> Unit
            letter.isLetterOrDigit() -> {
                form.append(letter)
                isAfterWord = true
            }
            letter in APOSTROPHES -> Unit
            isAfterWord -> {
                form.append(' ')
                isAfterWord = false
            }
        }
    }
    return form.trimEnd().toString()
}

/** The Arabic letters typed one way as often as another, as the one search reads them as. */
private fun Char.foldedArabic(): Char = when (this) {
    'ٱ' -> 'ا'
    'ة' -> 'ه'
    'ى' -> 'ي'
    else -> this
}

private const val ARABIC_TATWEEL = 'ـ'

private val APOSTROPHES = setOf('\'', '’', '`')

/** A text broken into what a search compares - see [searchFormOf]. */
class SearchText(text: String) {
    val words: List<String> = searchFormOf(text).split(' ').filter { it.isNotEmpty() }

    /** Every word run together, so "mrfeel" and "mr feel" find each other. */
    val compact: String = words.joinToString("")

    private val full: String = words.joinToString(" ")

    /** Each word's first letter: "mf" for Mr. Feel. */
    private val initials: String = words.joinToString("") { it.take(1) }

    /**
     * How well [query] finds this text, best first - 0 for not at all. Each way of matching is a tier of
     * its own, so a row found exactly always ranks above one found by a typo:
     * - the whole text, or how it starts;
     * - every word typed starting a word of the text, in any order - "feel mr";
     * - the text with its spaces closed up starting with or holding what was typed - "mrfee", "rfee";
     * - its initials - "mf";
     * - every word typed starting a word of the text but for a typo or two - "mr feal";
     * - the letters typed, in order, close together in the text - "mrfl".
     */
    fun scoreFor(query: SearchText): Int {
        val typed = query.compact
        return when {
            full == query.full -> 100
            full.startsWith(query.full) -> 90
            query.words.all { typedWord -> words.any { it.startsWith(typedWord) } } -> 80
            compact.startsWith(typed) -> 72
            compact.contains(typed) -> 65
            typed.length >= 2 && initials.startsWith(typed) -> 60
            else -> typoScoreFor(query)
        }
    }

    private fun typoScoreFor(query: SearchText): Int {
        val wordTypos = query.words.sumOfOrNull { typedWord -> words.mapNotNull { typoCount(typedWord, it) }.minOrNull() }
        val compactTypos = typoCount(query.compact, compact)
        val typos = listOfNotNull(wordTypos, compactTypos).minOrNull()
        return when {
            typos != null -> 50 - typos * 5
            isCloseSubsequence(query.compact) -> 40
            else -> 0
        }
    }

    /** Whether [typed]'s letters appear in [compact] in order, never far apart. */
    private fun isCloseSubsequence(typed: String): Boolean {
        if (typed.length < 3) return false
        var start = compact.indexOf(typed[0])
        while (start >= 0) {
            var position = start
            val isFound = typed.drop(1).all { letter ->
                position = compact.indexOf(letter, position + 1)
                position >= 0
            }
            if (isFound && position - start < typed.length * 2) return true
            start = compact.indexOf(typed[0], start + 1)
        }
        return false
    }
}

/** How many typos [typed] may hold and still be found: none in a word too short to tell, more in a longer one. */
private fun allowedTypos(typed: String): Int = when {
    typed.length < 3 -> 0
    typed.length < 7 -> 1
    else -> 2
}

/**
 * The fewest typos - a letter missed, added, changed, or two swapped - that turn [typed] into how [word]
 * starts, or null for more than [allowedTypos]. Compared with the word's start, so a word still being typed
 * is found: "fel" finds "feeling" as readily as "feel".
 */
private fun typoCount(typed: String, word: String): Int? {
    if (word.startsWith(typed)) return 0
    val allowed = allowedTypos(typed)
    if (allowed == 0 || word.isEmpty()) return null
    val columns = minOf(word.length, typed.length + allowed)
    // Damerau-Levenshtein, as optimal string alignment: the rows are [typed], the columns [word]'s start.
    var beforePrevious = IntArray(columns + 1)
    var previous = IntArray(columns + 1) { it }
    var current = IntArray(columns + 1)
    for (row in 1..typed.length) {
        current[0] = row
        for (column in 1..columns) {
            val cost = if (typed[row - 1] == word[column - 1]) 0 else 1
            var distance = minOf(previous[column] + 1, current[column - 1] + 1, previous[column - 1] + cost)
            if (row > 1 && column > 1 && typed[row - 1] == word[column - 2] && typed[row - 2] == word[column - 1]) {
                distance = minOf(distance, beforePrevious[column - 2] + 1)
            }
            current[column] = distance
        }
        val recycled = beforePrevious
        beforePrevious = previous
        previous = current
        current = recycled
    }
    // The best of the word's starts no more than the typos allowed shorter than what was typed.
    val fewest = (maxOf(1, typed.length - allowed)..columns).minOfOrNull { previous[it] } ?: return null
    return fewest.takeIf { it <= allowed }
}

private inline fun <T> List<T>.sumOfOrNull(selector: (T) -> Int?): Int? {
    var sum = 0
    for (element in this) sum += selector(element) ?: return null
    return sum
}

/**
 * What a search found: [items], best first - those found by their texts, then those found otherwise, the
 * first [foundByTextCount] being the former.
 */
class SearchResult<T>(val items: List<T>, val foundByTextCount: Int) {
    /** What was found by more than its texts alone - a track by its lyrics. */
    val foundOtherwise: List<T> get() = items.subList(foundByTextCount, items.size)
}

/**
 * [items], searchable by their [texts] - the one naming an item first, which a match on ranks a little
 * higher. The texts are read the first time it is searched rather than when it is built, so a list nobody
 * searches costs nothing, and once per list rather than per keystroke.
 *
 * A search ranks what it finds best first - see [SearchText.scoreFor] - keeping the list's own order among
 * rows found as well as each other, so a list sorted by title stays so among its equally good matches.
 */
class SearchIndex<T>(private val items: List<T>, texts: (T) -> List<String>) {
    private val searchTexts: List<List<SearchText>> by lazy { items.map { item -> texts(item).map(::SearchText) } }

    /**
     * What [query] finds, best first. Anything [isFoundOtherwise] says belongs that the texts do not find
     * comes last, in the list's order. A query of nothing to compare - blank, or punctuation alone - finds
     * every item.
     */
    fun search(query: String, isFoundOtherwise: (T) -> Boolean = { false }): SearchResult<T> {
        val searched = SearchText(query)
        if (searched.compact.isEmpty()) return SearchResult(items, items.size)
        val scored = items.indices.mapNotNull { index ->
            scoreOf(searchTexts[index], searched).takeIf { it > 0 }?.let { index to it }
        }.sortedByDescending { (_, score) -> score }
        val foundByText = scored.mapTo(HashSet(scored.size)) { (index, _) -> index }
        val foundOtherwise = items.filterIndexed { index, item -> index !in foundByText && isFoundOtherwise(item) }
        return SearchResult(scored.map { (index, _) -> items[index] } + foundOtherwise, scored.size)
    }

    private fun scoreOf(texts: List<SearchText>, query: SearchText): Int =
        texts.withIndex().maxOfOrNull { (position, text) ->
            text.scoreFor(query).let { score -> if (score > 0 && position == 0) score + NAMING_TEXT_BONUS else score }
        } ?: 0
}

/** What a match on the text naming an item adds: less than a tier, so it only orders rows within one. */
private const val NAMING_TEXT_BONUS = 2

/** Whether a row showing [texts] belongs on screen while [query] is being searched for - see [SearchText.scoreFor]. */
fun matchesSearch(query: String?, vararg texts: String): Boolean {
    if (query.isNullOrBlank()) return true
    val searched = SearchText(query)
    return searched.compact.isEmpty() || texts.any { SearchText(it).scoreFor(searched) > 0 }
}
