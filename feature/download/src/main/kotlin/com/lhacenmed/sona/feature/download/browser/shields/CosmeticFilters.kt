/*
 * Brave Shields' element hiding - adblock-rust's cosmetic filter cache, as brave-core runs it (components/brave_shields),
 * Copyright The Brave Authors, licensed under the Mozilla Public License, Version 2.0: https://mozilla.org/MPL/2.0/ -
 * written again in Kotlin.
 */
package com.lhacenmed.sona.feature.download.browser.shields

/** What parts a hiding filter's sites from its selector: `##`, `#@#` lifting it, and the procedural kinds' `#?#`, `#$#`, `#%#`. */
private val HidingMarker = Regex("#@?[?$%]?#")

/**
 * The lists' hiding filters: `##selector` for every page, `example.com##selector` for a site's, and `#@#` lifting either.
 * Generic selectors led by a class or an id are kept by it, and only given to a page holding an element of that class or id -
 * as adblock-rust does - rather than every page being handed the thousands of them. A site's procedural filters, and its
 * scriptlets - `example.com##+js(...)` - are kept by it too.
 */
internal class CosmeticFilters private constructor(
    private val generic: List<String>,
    private val genericExceptFor: List<Pair<String, Array<String>>>,
    private val byClassOrId: Map<String, List<String>>,
    private val bySite: Map<String, List<String>>,
    private val notOnSite: Map<String, Set<String>>,
    private val liftedOnSite: Map<String, Set<String>>,
    private val liftedEverywhere: Set<String>,
    private val proceduralBySite: Map<String, List<Procedural>>,
    private val scriptletsBySite: Map<String, List<List<String>>>,
    private val genericScriptlets: List<List<String>>,
    private val scriptletsLiftedOnSite: Map<String, Set<List<String>>>,
) {
    /** [host]'s procedural filters, as Brave's script takes them - those doing more than act on a plain selector only where [aggressive]. */
    fun proceduralFor(host: String, aggressive: Boolean): List<String> {
        val keys = domainKeysOf(host)
        val lifted = keys.flatMapTo(HashSet(liftedEverywhere)) { liftedOnSite[it].orEmpty() }
        return keys.flatMap { proceduralBySite[it].orEmpty() }
            .filter { (aggressive || it.isActionOnly) && it.json !in lifted }
            .map { it.json }
            .distinct()
    }

    /** The scriptlets [host]'s pages run - each its name, then its arguments - but those an exception lifts, or all where `#@#+js()` does. */
    fun scriptletsFor(host: String): List<List<String>> {
        val keys = domainKeysOf(host)
        val lifted = (keys + "").flatMapTo(HashSet()) { scriptletsLiftedOnSite[it].orEmpty() }
        if (emptyList<String>() in lifted) return emptyList()
        return (keys.flatMap { scriptletsBySite[it].orEmpty() } + genericScriptlets).distinct().filter { it !in lifted }
    }

    fun selectorsFor(host: String, withGeneric: Boolean, aggressive: Boolean): List<String> {
        val keys = domainKeysOf(host)
        val lifted = keys.flatMapTo(HashSet(liftedEverywhere)) { liftedOnSite[it].orEmpty() }
        val result = LinkedHashSet<String>()
        keys.forEach { key ->
            bySite[key]?.forEach { selector -> if (keys.none { notOnSite[it]?.contains(selector) == true }) result += selector }
        }
        if (withGeneric) {
            result += generic
            genericExceptFor.forEach { (selector, except) -> if (except.none { hostMatches(host, it) }) result += selector }
        }
        return result.filter { it !in lifted && (aggressive || ":has(" !in it) }
    }

    fun classIdSelectors(host: String, classes: Collection<String>, ids: Collection<String>, aggressive: Boolean): List<String> {
        val lifted = domainKeysOf(host).flatMapTo(HashSet(liftedEverywhere)) { liftedOnSite[it].orEmpty() }
        val result = LinkedHashSet<String>()
        classes.forEach { byClassOrId[".$it"]?.let(result::addAll) }
        ids.forEach { byClassOrId["#$it"]?.let(result::addAll) }
        return result.filter { it !in lifted && (aggressive || ":has(" !in it) }
    }

    class Builder {
        private val generic = ArrayList<String>()
        private val genericExceptFor = ArrayList<Pair<String, Array<String>>>()
        private val byClassOrId = HashMap<String, MutableList<String>>()
        private val bySite = HashMap<String, MutableList<String>>()
        private val notOnSite = HashMap<String, MutableSet<String>>()
        private val liftedOnSite = HashMap<String, MutableSet<String>>()
        private val liftedEverywhere = HashSet<String>()
        private val proceduralBySite = HashMap<String, MutableList<Procedural>>()
        private val scriptletsBySite = HashMap<String, MutableList<List<String>>>()
        private val genericScriptlets = ArrayList<List<String>>()
        private val scriptletsLiftedOnSite = HashMap<String, MutableSet<List<String>>>()

        /** Takes [line] where it is a hiding filter - true then, used or not, as no network filter is ever one. */
        fun add(line: String): Boolean {
            val marker = HidingMarker.find(line) ?: return false
            // A network filter's own `#` - a page's anchor in its address - is never followed by a second.
            if (marker.range.first > 0 && line[marker.range.first - 1] == '$') return false
            val selector = line.substring(marker.range.last + 1).trim()
            val markerText = marker.value
            // `#?#` is uBO's procedural marker - the same filters, marked; `#$#` and `#%#` are AdGuard's, none of Brave's.
            if (markerText != "##" && markerText != "#@#" && markerText != "#?#" && markerText != "#@?#") return true
            if (selector.isEmpty() || selector.startsWith('^')) return true
            val isException = markerText.startsWith("#@")
            val sites = line.substring(0, marker.range.first).lowercase().split(',').map(String::trim).filter(String::isNotEmpty)
            val onSites = sites.filterNot { it.startsWith('~') }
            val notOn = sites.filter { it.startsWith('~') }.map { it.substring(1) }
            if (selector.startsWith("+js(")) {
                val args = scriptletArgsOf(selector) ?: return true
                when {
                    isException -> (onSites.ifEmpty { listOf("") }).forEach { scriptletsLiftedOnSite.getOrPut(it, ::HashSet) += args }
                    args.isEmpty() -> {}
                    onSites.isNotEmpty() -> onSites.forEach { scriptletsBySite.getOrPut(it, ::ArrayList) += args }
                    notOn.isEmpty() -> genericScriptlets += args
                }
                return true
            }
            val procedural = proceduralOf(selector)
            if (procedural != null) {
                // A procedural filter for every page is none uBO runs either - too costly for every page.
                if (isException) onSites.forEach { liftedOnSite.getOrPut(it, ::HashSet) += procedural.json }
                else onSites.forEach { proceduralBySite.getOrPut(it, ::ArrayList) += procedural }
                return true
            }
            if (markerText.contains('?')) return true
            when {
                isException && onSites.isEmpty() -> liftedEverywhere += selector
                isException -> onSites.forEach { liftedOnSite.getOrPut(it, ::HashSet) += selector }
                onSites.isNotEmpty() -> {
                    onSites.forEach { bySite.getOrPut(it, ::ArrayList) += selector }
                    notOn.forEach { notOnSite.getOrPut(it, ::HashSet) += selector }
                }
                notOn.isNotEmpty() -> genericExceptFor += selector to notOn.toTypedArray()
                else -> classOrIdOf(selector)?.let { byClassOrId.getOrPut(it, ::ArrayList) += selector } ?: run { generic += selector }
            }
            return true
        }

        fun build() = CosmeticFilters(
            generic, genericExceptFor, byClassOrId, bySite, notOnSite, liftedOnSite, liftedEverywhere,
            proceduralBySite, scriptletsBySite, genericScriptlets, scriptletsLiftedOnSite,
        )
    }
}

/** The class or id leading [selector] - `.ad` of `.ad > img` - which an element must have for it to match; null for one led by neither. */
internal fun classOrIdOf(selector: String): String? {
    if (selector.length < 2 || selector[0] != '.' && selector[0] != '#' || ',' in selector) return null
    var end = 1
    while (end < selector.length && (selector[end].isLetterOrDigit() || selector[end] == '-' || selector[end] == '_')) end++
    if (end == 1) return null
    val next = selector.getOrNull(end)
    return if (next == null || next in " .#[:>+~") selector.substring(0, end) else null
}
