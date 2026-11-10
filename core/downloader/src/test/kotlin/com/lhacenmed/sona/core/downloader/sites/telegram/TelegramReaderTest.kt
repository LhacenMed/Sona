package com.lhacenmed.sona.core.downloader.sites.telegram

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val Key = "synthetic_channel/123"
private const val Src = "https://cdn4.cdn-telegram.org/file/SyntheticFile_-1.mp4?token=abc"

private fun player(src: String = Src) = """<a class="tgme_widget_message_video_player" href="https://t.me/$Key">
    <i class="tgme_widget_message_video_thumb" style="background-image:url('https://cdn4.cdn-telegram.org/file/SyntheticThumb.jpg')"></i>
    <div class="tgme_widget_message_video_wrap" style="width:720px;padding-top:56.25%">
    <video class="tgme_widget_message_video js-message_video" src="$src" width="100%" height="100%"></video></div>
    <time class="message_video_duration js-message_video_duration">1:05</time></a>"""

private fun page(media: String, key: String = Key) = """<!doctype html><html><body><div class="tgme_widget_message" data-post="$key">
    <div class="tgme_widget_message_owner_name">Synthetic Channel</div>$media
    <div class="tgme_widget_message_text">Synthetic caption<br/>second line</div></div></body></html>"""

class TelegramReaderTest {
    @Test
    fun previewAndForumLinksNameTheMessage() {
        val cases = mapOf("https://t.me/synthetic_channel/123" to Key, "https://t.me/s/synthetic_channel/123" to Key,
            "https://telegram.me/s/synthetic_channel/123/?before=1" to Key, "https://t.me/synthetic_forum/5/456" to "synthetic_forum/456")
        for ((url, key) in cases) assertEquals(url, "https://t.me/$key?embed=1&mode=tme", TelegramPostIdentity.parse(url)?.fetchUrl)
        for (url in listOf("https://t.me/c/123/456", "https://t.me/+invite", "https://t.me/s/synthetic_channel", "https://t.me/share/123",
            "https://t.me/synthetic_channel/123/extra", "https://t.me/synthetic_forum/0/456", "https://t.me.evil.test/s/synthetic_channel/123"))
            assertNull(url, TelegramPostIdentity.parse(url))
    }

    @Test
    fun aPostsOneVideoIsItsFile() {
        val info = JSONObject(checkNotNull(TelegramReader.parse(page(player()), Key, "https://t.me/$Key")))
        val format = info.getJSONArray("formats").getJSONObject(0)
        assertEquals(Src, format.getString("url"))
        assertEquals(720, format.getInt("width"))
        assertEquals(405, format.getInt("height"))
        assertEquals(65.0, info.getDouble("duration"), 0.0)
        assertEquals("Synthetic caption", info.getString("title"))
        assertEquals("Synthetic Channel", info.getString("uploader"))
        assertEquals("https://cdn4.cdn-telegram.org/file/SyntheticThumb.jpg", info.getString("thumbnail"))
    }

    @Test
    fun noVideoSeveralAnotherPostOrAForeignFileIsNotRead() {
        assertNull(TelegramReader.parse(page(""), Key, "https://t.me/$Key"))
        assertNull(TelegramReader.parse(page(player() + player()), Key, "https://t.me/$Key"))
        assertNull(TelegramReader.parse(page(player(), "other_channel/1"), Key, "https://t.me/$Key"))
        assertNull(TelegramReader.parse(page(player("https://evil.example/file/a.mp4")), Key, "https://t.me/$Key"))
        assertNull(TelegramReader.parse(page("<div class=\"tgme_widget_message_link_preview\">${player()}</div>"), Key, "https://t.me/$Key"))
        assertNull(TelegramReader.parse(page(player()).replace("<body>", "<body><div class=\"tgme_widget_message_error\"></div>"), Key, "https://t.me/$Key"))
    }
}
