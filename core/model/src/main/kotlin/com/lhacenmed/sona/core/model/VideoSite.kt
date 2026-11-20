package com.lhacenmed.sona.core.model

import java.net.URI

/**
 * The name of the site a video's [link] leads to, as the site calls itself - "YouTube", "Facebook" - known by its address, so
 * a video downloaded by any downloader, before or since, is named alike. A site not listed is named by its address, "vimeo.com";
 * null for no link at all.
 */
fun siteNameOf(link: String): String? {
    val host = runCatching { URI(link.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    return SiteNames.entries.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.value ?: host
}

/** Each site by its address - its other addresses beside it - as it names itself. */
private val SiteNames = linkedMapOf(
    "youtube.com" to "YouTube",
    "youtu.be" to "YouTube",
    "facebook.com" to "Facebook",
    "fb.watch" to "Facebook",
    "instagram.com" to "Instagram",
    "tiktok.com" to "TikTok",
    "x.com" to "X",
    "twitter.com" to "X",
    "threads.net" to "Threads",
    "threads.com" to "Threads",
    "reddit.com" to "Reddit",
    "redd.it" to "Reddit",
    "bsky.app" to "Bluesky",
    "pinterest.com" to "Pinterest",
    "pin.it" to "Pinterest",
    "linkedin.com" to "LinkedIn",
    "snapchat.com" to "Snapchat",
    "t.me" to "Telegram",
    "vimeo.com" to "Vimeo",
    "dailymotion.com" to "Dailymotion",
    "twitch.tv" to "Twitch",
    "soundcloud.com" to "SoundCloud",
)
