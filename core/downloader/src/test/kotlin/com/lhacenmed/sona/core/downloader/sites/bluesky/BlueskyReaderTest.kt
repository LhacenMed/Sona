package com.lhacenmed.sona.core.downloader.sites.bluesky

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val Did = "did:plc:abcdefghijklmnopqrstuvwx"
private const val AtUri = "at://$Did/app.bsky.feed.post/3synthetic001"
private const val Cid = "bafkreiabcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopq"

/** A thread shaped like the public API's for a video post; [embed] and [record] are its view and its record's embed. */
private fun thread(
    embed: String = """{"${'$'}type":"app.bsky.embed.video#view","cid":"$Cid","playlist":"https://video.bsky.app/watch/x/playlist.m3u8",
        "thumbnail":"https://video.bsky.app/watch/x/thumbnail.jpg","aspectRatio":{"width":1080,"height":1920}}""",
    record: String = """{"${'$'}type":"app.bsky.embed.video","video":{"${'$'}type":"blob","ref":{"${'$'}link":"$Cid"},"mimeType":"video/mp4","size":123456}}""",
    uri: String = AtUri,
) = """{"thread":{"${'$'}type":"app.bsky.feed.defs#threadViewPost","post":{"uri":"$uri",
    "author":{"did":"$Did","handle":"synthetic.test","displayName":"Synthetic author"},
    "record":{"${'$'}type":"app.bsky.feed.post","text":"Synthetic caption","embed":$record},"embed":$embed}}}"""

class BlueskyReaderTest {
    @Test
    fun postLinksKeepTheActorAndRkey() {
        for (url in listOf("https://bsky.app/profile/synthetic.test/post/3synthetic001/likes", "https://bsky.app/profile/$Did/post/3synthetic001/quotes/",
            "https://bsky.app/profile/did:web:synthetic.example.org/post/3synthetic001"))
            assertEquals(url, "3synthetic001", BlueskyPostIdentity.parse(url)?.rkey)
        assertEquals(Did, BlueskyPostIdentity.parse("https://bsky.app/profile/$Did/post/3synthetic001")?.did)
        assertNull(BlueskyPostIdentity.parse("https://bsky.app/profile/synthetic.test/post/3synthetic001")?.did)
        for (url in listOf("https://bsky.app/profile/synthetic.test", "https://bsky.app/profile/synthetic.test/post/3synthetic001/replies",
            "https://bsky.app/profile/did:web:localhost/post/3synthetic001", "https://bsky.app/profile/did:key:z6Mk/post/3synthetic001",
            "https://bsky.app/profile/localhost/post/3synthetic001", "https://bsky.app.evil.test/profile/synthetic.test/post/3synthetic001"))
            assertNull(url, BlueskyPostIdentity.parse(url))
    }

    @Test
    fun aVideoPostsBlobIsItsSource() {
        val video = checkNotNull(BlueskyReader.parseThread(thread(), AtUri))
        assertEquals(Cid, video.cid)
        val server = checkNotNull(BlueskyReader.serverOf("""{"id":"$Did","service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer",
            "serviceEndpoint":"https://morel.us-east.host.bsky.network"}]}"""))
        val info = JSONObject(checkNotNull(BlueskyReader.infoJson(video, server, Did, "3synthetic001", "https://bsky.app/profile/$Did/post/3synthetic001")))
        val format = info.getJSONArray("formats").getJSONObject(0)
        assertTrue(format.getString("url").startsWith("https://morel.us-east.host.bsky.network/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3A"))
        assertTrue(format.getString("url").endsWith("&cid=$Cid"))
        assertEquals(1920, format.getInt("height"))
        assertEquals(123456L, format.getLong("filesize"))
        assertEquals("Synthetic caption", info.getString("title"))
        assertEquals("Synthetic author", info.getString("uploader"))
    }

    @Test
    fun aQuotedPostsVideoOfItsOwnIsRead() {
        val embed = """{"${'$'}type":"app.bsky.embed.recordWithMedia#view","media":{"${'$'}type":"app.bsky.embed.video#view","aspectRatio":{"width":1,"height":2}}}"""
        val record = """{"${'$'}type":"app.bsky.embed.recordWithMedia","media":{"${'$'}type":"app.bsky.embed.video","video":{"${'$'}type":"blob",
            "ref":{"${'$'}link":"$Cid"},"mimeType":"video/mp4"}}}"""
        assertEquals(Cid, BlueskyReader.parseThread(thread(embed, record), AtUri)?.cid)
    }

    @Test
    fun photosAnotherPostAndForeignServersAreNotRead() {
        assertNull(BlueskyReader.parseThread(thread(embed = """{"${'$'}type":"app.bsky.embed.images#view","images":[]}"""), AtUri))
        assertNull(BlueskyReader.parseThread(thread(uri = "at://$Did/app.bsky.feed.post/other"), AtUri))
        assertNull(BlueskyReader.parseThread("""{"thread":{"${'$'}type":"app.bsky.feed.defs#notFoundPost"}}""", AtUri))
        for (endpoint in listOf("https://pds.example.org", "https://evil.host.bsky.network.example", "http://a.host.bsky.network"))
            assertNull(endpoint, BlueskyReader.serverOf("""{"service":[{"id":"#atproto_pds","serviceEndpoint":"$endpoint"}]}"""))
    }
}
