/*
 * Brave's debouncing and query filtering - brave-core's components/debounce and components/query_filter, Copyright The Brave
 * Authors, licensed under the Mozilla Public License, Version 2.0: https://mozilla.org/MPL/2.0/ - written again in Kotlin,
 * reading the same rules Brave's lists give it: brave/adblock-lists' debounce.json and query-filter.json.
 */
package com.lhacenmed.sona.feature.download.browser.shields

import android.net.Uri
import android.util.Base64
import java.net.URLDecoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** A Chrome extension's match pattern - a scheme, a host and a path, each `*` for any part - as debounce.json's include and exclude are written. */
internal class MatchPattern private constructor(private val anyHost: Boolean, private val host: String, private val subdomains: Boolean, private val path: Regex) {
    fun matches(url: Uri): Boolean {
        if (url.scheme != "http" && url.scheme != "https") return false
        val urlHost = url.host?.lowercase() ?: return false
        if (!anyHost && !(urlHost == host || subdomains && urlHost.endsWith(".$host"))) return false
        // The path a request asks for: its path, and its query after a `?` where it has one.
        val pathAndQuery = (url.encodedPath ?: "/") + (url.encodedQuery?.let { "?$it" } ?: "")
        return path.matches(pathAndQuery)
    }

    /** The site of the host this pattern names - none for any host's. */
    val site: String? get() = if (anyHost) null else siteOf(host)

    companion object {
        fun of(pattern: String): MatchPattern? {
            val scheme = pattern.substringBefore("://", "")
            if (scheme != "*" && scheme != "http" && scheme != "https") return null
            val rest = pattern.substringAfter("://")
            val slash = rest.indexOf('/').takeIf { it >= 0 } ?: return null
            val hostPart = rest.substring(0, slash).substringBefore(':').lowercase()
            val path = Regex(rest.substring(slash).split('*').joinToString(".*") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL)
            return when {
                hostPart == "*" -> MatchPattern(true, "", false, path)
                hostPart.startsWith("*.") -> MatchPattern(false, hostPart.substring(2), true, path)
                else -> MatchPattern(false, hostPart, false, path)
            }
        }
    }
}

/** What a debounce rule does to find where its link goes. */
private enum class DebounceAction { REDIRECT, BASE64_REDIRECT, REGEX_PATH, REGEX_PATH_TEMPLATE }

/** One of debounce.json's rules: a tracking link it knows, and where in it the page it stands for is. */
internal class DebounceRule private constructor(
    private val include: List<MatchPattern>,
    private val exclude: List<MatchPattern>,
    private val action: DebounceAction,
    private val param: String,
    private val prependScheme: String?,
    /** The setting the rule waits on - de-AMP's, for an AMP cache's links - see [DeAmpPref]. */
    val pref: String?,
    private val redirectTemplate: String,
) {
    val sites: List<String> get() = include.mapNotNull(MatchPattern::site)

    /** Where [url] goes, past this rule's tracker - null where the rule does not apply, or finds no other site. */
    fun apply(url: Uri): Uri? {
        if (exclude.any { it.matches(url) } || include.none { it.matches(url) }) return null
        var value: String = when (action) {
            DebounceAction.REGEX_PATH, DebounceAction.REGEX_PATH_TEMPLATE -> {
                if (param.length > MaxRegexLength) return null
                val regex = runCatching { Regex(param) }.getOrNull() ?: return null
                val groups = regex.find(url.encodedPath ?: return null)?.groupValues?.drop(1)?.takeIf { it.isNotEmpty() } ?: return null
                val joined = if (action == DebounceAction.REGEX_PATH) {
                    groups.joinToString("")
                } else {
                    if (groups.size > 9) return null
                    val placeholders = Regex("""\$([1-9])""").findAll(redirectTemplate).map { it.groupValues[1].toInt() }.toSet()
                    if (placeholders != (1..groups.size).toSet()) return null
                    groups.foldIndexed(redirectTemplate) { index, template, group -> template.replace("$${index + 1}", group) }
                }
                runCatching { URLDecoder.decode(joined, Charsets.UTF_8.name()) }.getOrNull() ?: return null
            }
            DebounceAction.REDIRECT, DebounceAction.BASE64_REDIRECT -> {
                val raw = queryValue(url, param) ?: return null
                if (action == DebounceAction.REDIRECT) raw else runCatching {
                    String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrNull() ?: return null
            }
        }
        val parsed = value.toHttpUrlOrNull()
        // A rule prepending a scheme to what already has one is a rule written wrong.
        if (prependScheme != null && parsed != null) return null
        if (prependScheme != null) value = "$prependScheme://$value"
        val target = value.toHttpUrlOrNull() ?: return null
        val original = url.host?.lowercase() ?: return null
        // Never to the same site - and only where a simple reading of the address finds the same host the parser does.
        if (siteOf(target.host) == siteOf(original)) return null
        if (value.substringAfter("://").split(':', '/', '?').firstOrNull { it.isNotEmpty() }?.lowercase() != target.host) return null
        if ('.' !in siteOf(target.host)) return null
        return Uri.parse(target.toString())
    }

    companion object {
        private const val MaxRegexLength = 200

        fun parse(json: String): List<DebounceRule> {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::of) }
        }

        private fun of(json: JSONObject): DebounceRule? {
            val action = when (json.optString("action")) {
                "redirect" -> DebounceAction.REDIRECT
                "base64,redirect" -> DebounceAction.BASE64_REDIRECT
                "regex-path" -> DebounceAction.REGEX_PATH
                "regex-path-template" -> DebounceAction.REGEX_PATH_TEMPLATE
                // An action Brave's code of the day does not know is a rule for a later one.
                else -> return null
            }
            val prepend = json.optString("prepend_scheme").takeIf { it.isNotEmpty() }
            if (prepend != null && prepend != "http" && prepend != "https") return null
            return DebounceRule(
                include = json.optJSONArray("include").strings().mapNotNull(MatchPattern::of),
                exclude = json.optJSONArray("exclude").strings().mapNotNull(MatchPattern::of),
                action = action,
                param = json.optString("param"),
                prependScheme = prepend,
                pref = json.optString("pref").takeIf { it.isNotEmpty() },
                redirectTemplate = json.optString("redirect_url_template"),
            )
        }
    }
}

/** debounce.json's name for the de-AMP setting - its rules for an AMP cache's links wait on it. */
internal const val DeAmpPref = "brave.de_amp.enabled"

/** Where [url] goes past every tracker [rules] know - followed through each, as a chain of them is - or null where none apply. */
internal fun debounce(url: Uri, rules: List<DebounceRule>, sites: Set<String>, deAmp: Boolean): Uri? {
    var current = url
    var changed = false
    repeat(MaxDebounces) {
        val host = current.host?.lowercase() ?: return if (changed) current else null
        if (siteOf(host) !in sites) return if (changed) current else null
        val next = rules.firstNotNullOfOrNull { rule -> rule.takeIf { it.pref == null || it.pref == DeAmpPref && deAmp }?.apply(current) }
            ?: return if (changed) current else null
        current = next
        changed = true
    }
    return current
}

private const val MaxDebounces = 5

/** One of query-filter.json's rules: [params] taken off an address [include] matches and [exclude] does not. */
internal class QueryRule(private val include: List<Regex>, private val exclude: List<Regex>, val params: List<String>) {
    fun appliesTo(spec: String): Boolean = exclude.none { it.containsMatchIn(spec) } && include.any { it.containsMatchIn(spec) }

    companion object {
        fun parse(json: String): List<QueryRule> {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val rule = array.optJSONObject(index) ?: return@mapNotNull null
                QueryRule(
                    include = rule.optJSONArray("include").strings().mapNotNull(::globRegex),
                    exclude = rule.optJSONArray("exclude").strings().mapNotNull(::globRegex),
                    params = rule.optJSONArray("params").strings(),
                )
            }
        }

        /** A subresource filter's pattern as a regex: `*` anything, the rest as it is, anywhere in an address. */
        private fun globRegex(pattern: String): Regex? = pattern.trim().takeIf(String::isNotEmpty)?.let {
            Regex(it.split('*').joinToString(".*") { part -> Regex.escape(part) }, RegexOption.IGNORE_CASE)
        }
    }
}

/** Parameters tracking only in some places - where the address does not match, they are taken off too. Brave's own, in its code. */
private val ConditionalTrackers = mapOf(
    "ck_subscriber_id" to Regex("/unsubscribe"),
    "h_sid" to Regex("/email/"),
    "h_slt" to Regex("/email/"),
    "mkt_tok" to Regex("([uU]nsubscribe|emailWebview)"),
)

/** Hosts whose links are left as they are: a mail filter's link wrapper, broken by what is taken off it. */
private val QueryFilterExempted = setOf("urldefense.com")

/**
 * [url] without the tracking parameters [rules] name - in the order the rest were - for a navigation from another site
 * ([fromHost]'s); null where nothing is taken off.
 */
internal fun filterQuery(url: Uri, fromHost: String?, rules: List<QueryRule>): Uri? {
    val query = url.encodedQuery ?: return null
    val host = url.host?.lowercase() ?: return null
    if (host in QueryFilterExempted) return null
    if (fromHost != null && siteOf(fromHost) == siteOf(host)) return null
    val spec = url.toString()
    val blocked = rules.filter { it.appliesTo(spec) }.flatMapTo(HashSet()) { it.params }
    var stripped = false
    val kept = query.split('&').filter { token ->
        val param = token.substringBefore('=', "")
        val value = token.substringAfter('=', "")
        if (param.isEmpty() || value.isEmpty()) return@filter true
        val tracking = param in blocked || ConditionalTrackers[param]?.containsMatchIn(spec) == false
        if (tracking) stripped = true
        !tracking
    }
    if (!stripped) return null
    return url.buildUpon().encodedQuery(kept.joinToString("&").takeIf(String::isNotEmpty)).build()
}

/** The first value of [name] in [url]'s query, unescaped as a browser's reading of it is. */
private fun queryValue(url: Uri, name: String): String? {
    val token = url.encodedQuery?.split('&')?.firstOrNull { it.substringBefore('=') == name } ?: return null
    return runCatching { URLDecoder.decode(token.substringAfter('=', ""), Charsets.UTF_8.name()) }.getOrNull()
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotEmpty) }
