package com.lhacenmed.sona.core.downloader.sites

/**
 * A site read by the app itself - Nezzel's native readers: one public page or JSON of the post, parsed for its video, so the
 * link needs no Python, and often no yt-dlp at all, to be shown and downloaded.
 *
 * [read] answers yt-dlp's own info JSON for the post's video - `_type` "video", `id`, `title`, `uploader`, `thumbnail`,
 * `duration` where known, `webpage_url`, `extractor_key`, and `formats`: each with `format_id`, `url`, `protocol` "https",
 * `ext`, `vcodec`/`acodec` ("none" for a missing track), `width`/`height`, `tbr`, `filesize` where known, and `http_headers`
 * the URL needs - or null for a post it does not read in full, which yt-dlp then reads. It blocks its thread.
 */
internal interface SiteReader {
    /** Whether [url] is one of this site's posts - decided from the link alone, with no request. */
    fun accepts(url: String): Boolean

    fun read(url: String, http: SiteHttp): String?
}
