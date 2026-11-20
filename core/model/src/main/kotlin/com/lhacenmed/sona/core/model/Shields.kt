package com.lhacenmed.sona.core.model

/** How hard the browser's Shields block trackers and ads - Brave's three: first-party ads too, third-party ones, or none. */
enum class AdBlockMode { AGGRESSIVE, STANDARD, OFF }

/** Whether a site asked for over HTTP is asked for over HTTPS - warning where it has none ([STRICT]), or quietly falling back. */
enum class HttpsUpgradeMode { STRICT, STANDARD, OFF }

/** How a page is kept from telling this browser apart - Brave's farbling: [STANDARD] noise, [STRICT] more, at sites' risk. */
enum class FingerprintMode { STRICT, STANDARD, OFF }

/** Which cookies the browser refuses: other sites' in a page, every one, or none. */
enum class CookieBlockMode { CROSS_SITE, ALL, OFF }

/**
 * The browser's Shields and privacy settings - Brave's, with its defaults. [downSites] are the sites, by eTLD+1, the user took
 * the Shields down on; [listsOn] and [listsOff] the filter lists they turned on or off against the catalog's own defaults.
 */
data class ShieldsPreferences(
    val adBlock: AdBlockMode = AdBlockMode.STANDARD,
    val httpsUpgrade: HttpsUpgradeMode = HttpsUpgradeMode.STANDARD,
    val blockScripts: Boolean = false,
    val fingerprinting: FingerprintMode = FingerprintMode.STANDARD,
    val cookies: CookieBlockMode = CookieBlockMode.CROSS_SITE,
    val forgetOnClose: Boolean = false,
    val reduceLanguage: Boolean = true,
    val deAmp: Boolean = true,
    val debounce: Boolean = true,
    val downSites: Set<String> = emptySet(),
    val listsOn: Set<String> = emptySet(),
    val listsOff: Set<String> = emptySet(),
)
