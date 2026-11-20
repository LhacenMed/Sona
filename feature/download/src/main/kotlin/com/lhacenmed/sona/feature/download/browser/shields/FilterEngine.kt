/*
 * Brave Shields' ad and tracker blocking - brave-core's components/brave_shields, and the adblock-rust engine it runs,
 * Copyright The Brave Authors, licensed under the Mozilla Public License, Version 2.0: https://mozilla.org/MPL/2.0/ -
 * written again in Kotlin, for what a web view lets be seen of a page's requests.
 */
package com.lhacenmed.sona.feature.download.browser.shields

/** What a request fetches, as a filter's `$script`, `$image`... names it. */
internal enum class RequestType {
    DOCUMENT, SUBDOCUMENT, SCRIPT, IMAGE, STYLESHEET, FONT, MEDIA, XHR, WEBSOCKET, PING, OTHER,

    /** A window a page opens - matched only by a filter naming `$popup`. */
    POPUP;

    val bit: Int get() = 1 shl ordinal
}

/**
 * A request a page makes: [url], to [host], from the page on [pageHost] - of [type], null where the web view does not tell:
 * a script's or a fetch's, as nearly every such request is.
 */
internal class FilterRequest(val url: String, val host: String, val pageHost: String, val type: RequestType?) {
    private val pageSite = siteOf(pageHost)
    val isThirdParty: Boolean = siteOf(host) != pageSite
    val lowerUrl: String = url.lowercase()
    val tokens: Set<String> by lazy(LazyThreadSafetyMode.NONE) { tokensOf(lowerUrl) }

    /** The types a filter's must take in for it to match this request. */
    val typeBits: Int = type?.bit ?: UnknownTypeBits

    /** The page's host without its public suffix - what an entity's `name.*` is matched against. */
    private val pageEntity: String by lazy(LazyThreadSafetyMode.NONE) { pageHost.removeSuffix("." + pageSite.substringAfter('.', "")) }

    /** The page's host as a filter's `$domain=` may name it - see [domainKeysOf]. */
    val pageKeys: List<String> by lazy(LazyThreadSafetyMode.NONE) { domainKeysOf(pageHost) }

    /** Whether the page is on [domain], as a filter's `$domain=` names it - see [hostMatches]. */
    fun pageIsOn(domain: String): Boolean =
        if (domain.endsWith(".*")) hostMatches(pageEntity, domain.dropLast(2)) else hostMatches(pageHost, domain)
}

/** What a request of no known type may be: a script, a fetch, or another. */
private val UnknownTypeBits = RequestType.SCRIPT.bit or RequestType.XHR.bit or RequestType.OTHER.bit

/** What a list's filters make of a request - nothing, a block, or an `$important` block no exception lifts. */
internal enum class Block { NONE, MATCHED, IMPORTANT }

/** One network filter: a request it matches is blocked - or, as an exception, let through. */
internal class NetworkFilter(
    val isException: Boolean,
    val isImportant: Boolean,
    private val pattern: String,
    private val hostAnchored: Boolean,
    private val leftAnchored: Boolean,
    private val rightAnchored: Boolean,
    private val isRegex: Boolean,
    private val matchCase: Boolean,
    private val types: Int,
    private val party: Int,
    val domains: Array<String>?,
    private val notDomains: Array<String>?,
    /** For an exception: which of a page's hiding it lifts - [LiftsAllHiding], [LiftsGenericHiding] - none for a request's. */
    val lifts: Int,
    /** The stand-in served for a request it blocks - one of uBO's resources, by name - see `$redirect`. */
    val redirect: String? = null,
    /** Whether it blocks at all - a `$redirect-rule` only says what to serve for what another filter blocks. */
    val blocks: Boolean = true,
) {
    /** The filter as a pattern - compiled the first time a request could match it, not before: most never are. */
    private val regex: Regex? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        // A pattern not matching case is lowercased, as the address it is matched against is - a regex is matched as written.
        runCatching { if (isRegex) Regex(pattern, if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)) else Regex(patternRegex()) }.getOrNull()
    }

    /** `||host^` alone - most of the lists - is a host and its subdomains, with no pattern to run. */
    private val plainHost: String? =
        pattern.takeIf { hostAnchored && !rightAnchored && it.endsWith('^') }?.dropLast(1)?.takeIf { host -> host.none { it in "*^/|?=&:" } }

    /** The tokens a request's address must hold for it to match - see [tokensOf] - empty where none are sure. */
    fun tokens(): List<String> {
        if (isRegex) return emptyList()
        val found = ArrayList<String>()
        var start = -1
        for (index in 0..pattern.length) {
            val char = pattern.getOrNull(index)
            if (char != null && char.isTokenChar()) {
                if (start < 0) start = index
                continue
            }
            if (start >= 0) {
                val before = pattern.getOrNull(start - 1)
                // A token at the pattern's start or end is whole only where it is anchored there - and never beside a wildcard.
                val wholeAtStart = if (before == null) hostAnchored || leftAnchored else before != '*'
                val wholeAtEnd = if (char == null) rightAnchored else char != '*'
                if (wholeAtStart && wholeAtEnd && index - start >= 2) found += pattern.substring(start, index).lowercase()
                start = -1
            }
        }
        return found
    }

    fun matches(request: FilterRequest): Boolean {
        if (party == FirstPartyOnly && request.isThirdParty || party == ThirdPartyOnly && !request.isThirdParty) return false
        if (types != 0 && types and request.typeBits == 0) return false
        if (request.type == RequestType.POPUP && types == 0) return false
        if (domains != null && domains.none(request::pageIsOn)) return false
        if (notDomains != null && notDomains.any(request::pageIsOn)) return false
        plainHost?.let { return hostMatches(request.host, it) }
        return regex?.containsMatchIn(if (matchCase || isRegex) request.url else request.lowerUrl) == true
    }

    /** The pattern as a regular expression: `*` anything, `^` a separator, `|` an end, `||` the host and its subdomains. */
    private fun patternRegex(): String = buildString {
        when {
            hostAnchored -> append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?")
            leftAnchored -> append('^')
        }
        for (char in pattern) {
            when {
                char == '*' -> append(".*")
                char == '^' -> append("(?:[^\\w.%-]|$)")
                char.isLetterOrDigit() -> append(char)
                else -> append('\\').append(char)
            }
        }
        if (rightAnchored) append('$')
    }
}

/**
 * Filters by a token their requests hold - the rarest of theirs - so a request is checked only against the few that could
 * match; one with none, by the sites it is for, where it names them.
 */
internal class FilterIndex(filters: List<NetworkFilter>) {
    private val byToken = HashMap<String, MutableList<NetworkFilter>>()
    private val byPageDomain = HashMap<String, MutableList<NetworkFilter>>()
    private val untokened = ArrayList<NetworkFilter>()

    init {
        val tokens = filters.map(NetworkFilter::tokens)
        val counts = HashMap<String, Int>()
        tokens.forEach { list -> list.forEach { counts.merge(it, 1, Int::plus) } }
        filters.forEachIndexed { index, filter ->
            val rarest = tokens[index].minByOrNull { counts.getValue(it) }
            when {
                rarest != null -> byToken.getOrPut(rarest, ::ArrayList) += filter
                filter.domains != null -> filter.domains.forEach { byPageDomain.getOrPut(it, ::ArrayList) += filter }
                else -> untokened += filter
            }
        }
    }

    fun firstMatch(request: FilterRequest, where: (NetworkFilter) -> Boolean = { true }): NetworkFilter? {
        for (token in request.tokens) byToken[token]?.firstOrNull { where(it) && it.matches(request) }?.let { return it }
        for (key in request.pageKeys) byPageDomain[key]?.firstOrNull { where(it) && it.matches(request) }?.let { return it }
        return untokened.firstOrNull { where(it) && it.matches(request) }
    }
}

/** What a page hides: [selectors] from its first frame, and whether more are looked up by its elements' classes and ids. */
internal class PageHiding(val selectors: List<String>, val byClassAndId: Boolean)

/** One set of lists' filters - Brave's default engine, or its additional one: what they block, let through, and hide. */
internal class FilterEngine private constructor(
    private val blocks: FilterIndex,
    private val exceptions: FilterIndex,
    private val hidingExceptions: FilterIndex,
    private val cosmetic: CosmeticFilters,
) {
    fun blocking(request: FilterRequest): Block {
        val match = blocks.firstMatch(request) { it.blocks } ?: return Block.NONE
        return if (match.isImportant || blocks.firstMatch(request) { it.blocks && it.isImportant } != null) Block.IMPORTANT else Block.MATCHED
    }

    /** The stand-in a blocked [request] is served in its place, where a filter names one. */
    fun redirectFor(request: FilterRequest): String? = blocks.firstMatch(request) { it.redirect != null }?.redirect

    /** The procedural filters of [host]'s pages - those more than acting on a plain selector only where [aggressive]. */
    fun proceduralFor(host: String, aggressive: Boolean): List<String> = cosmetic.proceduralFor(host, aggressive)

    /** The scriptlets [host]'s pages run - each its name, then its arguments. */
    fun scriptletsFor(host: String): List<List<String>> = cosmetic.scriptletsFor(host)

    fun excepts(request: FilterRequest): Boolean = exceptions.firstMatch(request) != null

    /** What the page at [url], on [host], hides: nothing where an exception lifts it, and a `:has` selector only where [aggressive]. */
    fun pageHiding(url: String, host: String, aggressive: Boolean): PageHiding {
        val lifts = hidingExceptions.firstMatch(FilterRequest(url, host, host, RequestType.DOCUMENT))?.lifts ?: 0
        if (lifts and LiftsAllHiding != 0) return PageHiding(emptyList(), byClassAndId = false)
        val generic = lifts and LiftsGenericHiding == 0
        return PageHiding(cosmetic.selectorsFor(host, generic, aggressive), byClassAndId = generic)
    }

    /** The generic selectors hiding an element on [host] of one of [classes] or [ids]. */
    fun classIdSelectors(host: String, classes: Collection<String>, ids: Collection<String>, aggressive: Boolean): List<String> =
        cosmetic.classIdSelectors(host, classes, ids, aggressive)

    companion object {
        /** The engine of [lines] - a filter list's, or several's, one filter to a line. */
        fun of(lines: Sequence<String>): FilterEngine {
            val network = ArrayList<Pair<String, NetworkFilter>>()
            val badFilters = HashSet<String>()
            val cosmetic = CosmeticFilters.Builder()
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line[0] == '!' || line[0] == '[' || cosmetic.add(line)) return@forEach
                val badFilter = badFilterTarget(line)
                if (badFilter != null) badFilters += badFilter else parseNetworkFilter(line)?.let { network += line to it }
            }
            val kept = network.filterNot { it.first in badFilters }.map { it.second }
            return FilterEngine(
                blocks = FilterIndex(kept.filter { !it.isException }),
                exceptions = FilterIndex(kept.filter { it.isException && it.lifts == 0 }),
                hidingExceptions = FilterIndex(kept.filter { it.isException && it.lifts != 0 }),
                cosmetic = cosmetic.build(),
            )
        }
    }
}

internal const val LiftsAllHiding = 1
internal const val LiftsGenericHiding = 2
private const val FirstPartyOnly = 1
private const val ThirdPartyOnly = 2

/** Options that add nothing a web view can tell apart from blocking. */
private val BlockingOptions = setOf("all")

/** The filter a `$badfilter` line takes out - the same line without it - or null for a line that is none. */
private fun badFilterTarget(line: String): String? {
    val dollar = line.lastIndexOf('$').takeIf { it >= 0 } ?: return null
    val options = line.substring(dollar + 1).split(',')
    if ("badfilter" !in options) return null
    val rest = options.filterNot { it == "badfilter" }
    return if (rest.isEmpty()) line.substring(0, dollar) else line.substring(0, dollar + 1) + rest.joinToString(",")
}

/**
 * [line] as a network filter - null for a line that is none, or one that asks for what a web view cannot: rewriting a
 * request, a page's own headers, or its popups.
 */
internal fun parseNetworkFilter(line: String): NetworkFilter? {
    var text = line
    val isException = text.startsWith("@@")
    if (isException) text = text.substring(2)
    // Options follow the last `$`, as adblock-rust reads them - a regex's own `$` before its closing slash stays its.
    var options = ""
    val optionsAt = text.lastIndexOf('$')
    if (optionsAt >= 0 && !text.endsWith('/')) {
        options = text.substring(optionsAt + 1)
        text = text.substring(0, optionsAt)
    }
    val isRegex = text.length > 2 && text.startsWith('/') && text.endsWith('/')
    var types = 0
    var notTypes = 0
    var party = 0
    var important = false
    var matchCase = false
    var lifts = 0
    var domains: Array<String>? = null
    var notDomains: Array<String>? = null
    var redirect: String? = null
    var blocks = true
    for (option in options.split(',')) {
        if (option.isEmpty()) continue
        val negated = option.startsWith('~')
        val name = option.removePrefix("~").substringBefore('=')
        val value = option.substringAfter('=', "")
        when (name) {
            "third-party", "3p", "strict3p" -> party = if (negated) FirstPartyOnly else ThirdPartyOnly
            "first-party", "1p", "strict1p" -> party = if (negated) ThirdPartyOnly else FirstPartyOnly
            "important" -> important = true
            "match-case" -> matchCase = true
            "domain", "from" -> {
                val parts = value.lowercase().split('|').filter(String::isNotEmpty)
                domains = parts.filterNot { it.startsWith('~') }.toTypedArray().takeIf { it.isNotEmpty() }
                notDomains = parts.filter { it.startsWith('~') }.map { it.substring(1) }.toTypedArray().takeIf { it.isNotEmpty() }
            }
            "elemhide", "ehide" -> lifts = lifts or LiftsAllHiding
            "generichide", "ghide" -> lifts = lifts or LiftsGenericHiding
            // An exception for a whole page lifts its hiding; what it lifts of its requests, a web view cannot tell apart.
            "document", "doc" -> if (isException) lifts = lifts or LiftsAllHiding else types = types or RequestType.DOCUMENT.bit
            in BlockingOptions -> {}
            // uBO's stand-ins - a priority after `:` is for choosing among several, none of which is chosen between here.
            "redirect" -> redirect = value.substringBefore(':')
            "redirect-rule" -> { redirect = value.substringBefore(':'); blocks = false }
            "empty" -> redirect = "empty"
            "mp4" -> redirect = "noop-1s.mp4"
            else -> {
                val type = typeNamed(name) ?: return null
                if (negated) notTypes = notTypes or type.bit else types = types or type.bit
            }
        }
    }
    if (notTypes != 0) types = (if (types == 0) RequestType.entries.filter { it != RequestType.POPUP }.fold(0) { all, it -> all or it.bit } else types) and notTypes.inv()
    // A page itself is never blocked here - only what it asks for.
    if (types == RequestType.DOCUMENT.bit) return null
    var hostAnchored = false
    var leftAnchored = false
    var rightAnchored = false
    var pattern = text
    if (isRegex) {
        pattern = pattern.substring(1, pattern.length - 1)
    } else {
        when {
            pattern.startsWith("||") -> { hostAnchored = true; pattern = pattern.substring(2) }
            pattern.startsWith('|') -> { leftAnchored = true; pattern = pattern.substring(1) }
        }
        if (pattern.endsWith('|')) { rightAnchored = true; pattern = pattern.dropLast(1) }
        // A wildcard at an end left open is no different from none.
        if (!hostAnchored && !leftAnchored) pattern = pattern.trimStart('*')
        if (!rightAnchored) pattern = pattern.trimEnd('*')
        if (!matchCase) pattern = pattern.lowercase()
    }
    // A filter of every request, with nothing to narrow it, would block every page - no list means one.
    if (pattern.isEmpty() && domains == null && types == 0 && lifts == 0) return null
    return NetworkFilter(
        isException, important, pattern, hostAnchored, leftAnchored, rightAnchored, isRegex, matchCase, types, party, domains, notDomains, lifts,
        redirect = redirect?.takeIf { it.isNotEmpty() && it != "none" }, blocks = blocks,
    )
}

private fun typeNamed(name: String): RequestType? = when (name) {
    "script" -> RequestType.SCRIPT
    "image" -> RequestType.IMAGE
    "stylesheet", "css" -> RequestType.STYLESHEET
    "font" -> RequestType.FONT
    "media" -> RequestType.MEDIA
    "xmlhttprequest", "xhr" -> RequestType.XHR
    "subdocument", "frame" -> RequestType.SUBDOCUMENT
    "websocket" -> RequestType.WEBSOCKET
    "ping", "beacon" -> RequestType.PING
    "other", "object", "object-subrequest" -> RequestType.OTHER
    "popup" -> RequestType.POPUP
    else -> null
}

private fun Char.isTokenChar(): Boolean = this in 'a'..'z' || this in '0'..'9' || this == '%' || this in 'A'..'Z'

/** The tokens of an address - its runs of two or more letters, digits and `%` - which a filter's own must be among to match. */
internal fun tokensOf(url: String): Set<String> {
    val tokens = HashSet<String>()
    var start = -1
    for (index in 0..url.length) {
        if (index < url.length && url[index].isTokenChar()) {
            if (start < 0) start = index
        } else if (start >= 0) {
            if (index - start >= 2) tokens += url.substring(start, index)
            start = -1
        }
    }
    return tokens
}

/**
 * Whether [host] is [domain] or one of its subdomains - or, for an entity's `name.*`, the site of that name under any public
 * suffix: `google.*` for `google.co.uk`.
 */
internal fun hostMatches(host: String, domain: String): Boolean {
    if (domain.endsWith(".*")) {
        val suffix = siteOf(host).substringAfter('.', "")
        return hostMatches(host.removeSuffix(".$suffix"), domain.dropLast(2))
    }
    return host == domain || host.length > domain.length && host.endsWith(domain) && host[host.length - domain.length - 1] == '.'
}
