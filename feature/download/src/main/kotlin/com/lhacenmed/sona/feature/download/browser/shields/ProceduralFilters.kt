/*
 * uBlock Origin's procedural cosmetic filters read into the operators Brave's cosmetic filtering runs - as adblock-rust's
 * parser does for brave-core's components/cosmetic_filters, Copyright The Brave Authors, licensed under the Mozilla Public
 * License, Version 2.0: https://mozilla.org/MPL/2.0/ - written again in Kotlin.
 */
package com.lhacenmed.sona.feature.download.browser.shields

import org.json.JSONArray
import org.json.JSONObject

/** uBO's and AdGuard's operator names, as Brave's procedural filters name them - null for those it runs none of. */
private val Operators = mapOf(
    "has-text" to "has-text", "-abp-contains" to "has-text", "contains" to "has-text",
    "has" to "has", "-abp-has" to "has", "if" to "has",
    "not" to "not", "if-not" to "not",
    "matches-attr" to "matches-attr", "matches-css" to "matches-css", "matches-css-before" to "matches-css-before",
    "matches-css-after" to "matches-css-after", "-abp-properties" to "matches-css", "matches-media" to "matches-media",
    "matches-path" to "matches-path", "matches-prop" to "matches-property", "min-text-length" to "min-text-length",
    "upward" to "upward", "nth-ancestor" to "upward", "xpath" to "xpath",
)

/** What a filter does to what it finds, in place of hiding it. */
private val Actions = setOf("remove", "style", "remove-attr", "remove-class")

/** What no stylesheet or Brave's script can do - a filter using one is left out. */
private val Unsupported = setOf("watch-attr", "others", "shadow", "spath", "matches-prop-value")

/** A procedural filter as Brave's script takes it - `{selector: [operators], action}` - and whether it does no more than act on a plain selector. */
internal class Procedural(val json: String, val isActionOnly: Boolean)

/** [selector] as a procedural filter - null where it is a plain one a stylesheet hides, or one using what is not run. */
internal fun proceduralOf(selector: String): Procedural? {
    var text = selector
    var action: JSONObject? = null
    // An action is the filter's last part: `:remove()`, `:style(...)`, `:remove-attr(...)`, `:remove-class(...)`.
    for (name in Actions) {
        val at = text.lastIndexOf(":$name(")
        if (at >= 0 && text.endsWith(")") && closingOf(text, at + name.length + 1) == text.length - 1) {
            action = JSONObject().put("type", name).put("arg", text.substring(at + name.length + 2, text.length - 1))
            text = text.substring(0, at)
            break
        }
    }
    val operators = operatorsOf(text) ?: return null
    val plain = operators.length() == 1 && operators.getJSONObject(0).getString("type") == "css-selector"
    if (plain && action == null) return null
    val filter = JSONObject().put("selector", operators)
    action?.let { filter.put("action", it) }
    return Procedural(filter.toString(), isActionOnly = plain)
}

/** [text]'s operators, in order: its plain parts as `css-selector`, each procedural one by Brave's name - null where one is not run. */
private fun operatorsOf(text: String): JSONArray? {
    val operators = JSONArray()
    val css = StringBuilder()
    var index = 0
    var depth = 0
    var quote: Char? = null
    while (index < text.length) {
        val char = text[index]
        when {
            char == '\\' -> { css.append(char); if (index + 1 < text.length) css.append(text[index + 1]); index += 2; continue }
            quote != null -> if (char == quote) quote = null
            char == '"' || char == '\'' -> quote = char
            char == '[' || char == '(' -> depth++
            char == ']' || char == ')' -> depth--
            char == ':' && depth == 0 -> {
                val name = text.substring(index + 1).takeWhile { it.isLetterOrDigit() || it == '-' }
                val open = index + 1 + name.length
                if (text.getOrNull(open) == '(') {
                    if (name in Unsupported || name in Actions) return null
                    val operator = Operators[name]
                    val close = closingOf(text, open)
                    if (operator != null && close > 0) {
                        val arg = text.substring(open + 1, close)
                        // `:has` and `:not` of a plain selector are a stylesheet's own.
                        val nested = if (operator == "has" || operator == "not") operatorsOf(arg) else null
                        val isPlainNested = nested != null && nested.length() == 1 && nested.getJSONObject(0).getString("type") == "css-selector"
                        if ((name == "has" || name == "not") && isPlainNested) {
                            css.append(text, index, close + 1)
                            index = close + 1
                            continue
                        }
                        if ((operator == "has" || operator == "not") && nested == null) return null
                        if (css.isNotBlank()) operators.put(JSONObject().put("type", "css-selector").put("arg", css.toString()))
                        css.clear()
                        operators.put(JSONObject().put("type", operator).put("arg", if (isPlainNested) arg else nested ?: arg))
                        index = close + 1
                        continue
                    }
                }
            }
        }
        css.append(char)
        index++
    }
    if (css.isNotBlank()) operators.put(JSONObject().put("type", "css-selector").put("arg", css.toString()))
    return operators.takeIf { it.length() > 0 }
}

/** Where the parenthesis opened at [open] in [text] closes - -1 where it never does. */
private fun closingOf(text: String, open: Int): Int {
    var depth = 0
    var index = open
    while (index < text.length) {
        when (text[index]) {
            '\\' -> index++
            '(' -> depth++
            ')' -> if (--depth == 0) return index
        }
        index++
    }
    return -1
}

/** A `+js(name, arg, ...)` filter's name and arguments, as uBO reads them: commas part them but an escaped one, quotes around one dropped. */
internal fun scriptletArgsOf(selector: String): List<String>? {
    if (!selector.startsWith("+js(") || !selector.endsWith(")")) return null
    val inner = selector.substring(4, selector.length - 1)
    if (inner.isBlank()) return emptyList()
    val args = ArrayList<String>()
    val current = StringBuilder()
    var index = 0
    while (index < inner.length) {
        val char = inner[index]
        if (char == '\\' && inner.getOrNull(index + 1) == ',') {
            current.append(',')
            index += 2
            continue
        }
        if (char == ',') {
            args += current.toString()
            current.clear()
        } else {
            current.append(char)
        }
        index++
    }
    args += current.toString()
    return args.map { arg ->
        val trimmed = arg.trim()
        val first = trimmed.firstOrNull()
        if (trimmed.length >= 2 && (first == '"' || first == '\'' || first == '`') && trimmed.last() == first) trimmed.substring(1, trimmed.length - 1) else trimmed
    }.toMutableList().also { it[0] = it[0].removeSuffix(".js") }
}
