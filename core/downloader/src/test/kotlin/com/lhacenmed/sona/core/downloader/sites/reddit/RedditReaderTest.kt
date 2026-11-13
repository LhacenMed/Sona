package com.lhacenmed.sona.core.downloader.sites.reddit

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val PostId = "1abcdef"
private const val VideoId = "synthvid0001"
private const val Permalink = "/r/synthetic/comments/$PostId/synthetic_video_post/"

/**
 * A post page shaped like Reddit's logged-out one: attribute names, player and packaged-media JSON as served, every value
 * synthetic. Another post's feed item comes first, so only the post asked for may count.
 */
private fun postPage(type: String = "video", id: String = PostId, content: String = "https://v.redd.it/$VideoId",
    player: Boolean = type == "video", extra: String = ""): String {
    val feed = """<shreddit-post permalink="/r/other/comments/9zzzzzz/other/" content-href="https://v.redd.it/othervid0001"
        id="t3_9zzzzzz" post-title="Other" post-type="video"><shreddit-player post-id="t3_9zzzzzz"
        src="https://v.redd.it/othervid0001/HLSPlaylist.m3u8?a=1"></shreddit-player></shreddit-post>"""
    val media = """{&quot;playbackMp4s&quot;:{&quot;duration&quot;:44,&quot;permutations&quot;:[]}}"""
    val playerTag = if (!player) "" else """<shreddit-player src="https://v.redd.it/$VideoId/HLSPlaylist.m3u8?f=sd%2CsubsAll&amp;v=1"
        packaged-media-json="$media" post-id="t3_$id" post-type="video"
        poster="https://external-preview.redd.it/synthetic-v0-abc.jpeg?width=640&amp;crop=smart&amp;s=00"></shreddit-player>"""
    return """<!DOCTYPE html><html lang="en"><body><shreddit-app>$feed
        <shreddit-post permalink="$Permalink" content-href="$content" id="t3_$id" post-title="A synthetic &amp; quoted &quot;video&quot;"
          post-type="$type" author="synthetic_author" $extra subreddit-name="synthetic">
          <div slot="post-media-container">$playerTag</div>
        </shreddit-post></shreddit-app></body></html>"""
}

private const val Manifest = """<?xml version="1.0" encoding="UTF-8"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="PT44.0S" type="static">
  <Period>
    <AdaptationSet contentType="video" segmentAlignment="true">
      <Representation bandwidth="1200000" codecs="avc1.4d401f" frameRate="30" height="720" id="5" mimeType="video/mp4" width="1280">
        <BaseURL>DASH_720.mp4</BaseURL><SegmentBase indexRange="900-1000"><Initialization range="0-899"/></SegmentBase>
      </Representation>
      <Representation bandwidth="400000" codecs="avc1.4d401e" frameRate="30" height="360" id="3" mimeType="video/mp4" width="640">
        <BaseURL>DASH_360.mp4</BaseURL>
      </Representation>
    </AdaptationSet>
    <AdaptationSet contentType="audio" mimeType="audio/mp4">
      <Representation audioSamplingRate="48000" bandwidth="128000" codecs="mp4a.40.2" id="7">
        <BaseURL>DASH_AUDIO_128.mp4</BaseURL>
      </Representation>
    </AdaptationSet>
  </Period>
</MPD>"""

class RedditReaderTest {
    private fun read(raw: String) = RedditPostIdentity.parse(raw)?.let { Triple(it.fetchUrl, it.postId, it.videoId) }

    @Test
    fun postLinksReadOneWwwPageWithoutTheirQuery() {
        val page = "https://www.reddit.com$Permalink"
        for (raw in listOf(page, "https://reddit.com$Permalink?utm_source=share", "https://old.reddit.com$Permalink",
            "https://m.reddit.com${Permalink.trimEnd('/')}", "https://www.reddit.com${Permalink}c0mment1/"))
            assertEquals(raw, Triple(page, PostId, null), read(raw))
        for (raw in listOf("https://www.reddit.com/r/synthetic/comments/$PostId/", "https://redd.it/$PostId", "https://www.reddit.com/gallery/$PostId"))
            assertEquals(raw, Triple("https://www.reddit.com/comments/$PostId/", PostId, null), read(raw))
        assertEquals(Triple("https://www.reddit.com/r/synthetic/s/AbC123xyz/", null, null), read("https://www.reddit.com/r/synthetic/s/AbC123xyz"))
        for (raw in listOf("https://v.redd.it/$VideoId", "https://www.reddit.com/video/$VideoId/"))
            assertEquals(raw, Triple("https://www.reddit.com/video/$VideoId/", null, VideoId), read(raw))
        for (raw in listOf("http://www.reddit.com$Permalink", "https://reddit.com.evil.test$Permalink", "https://www.reddit.com/r/synthetic/",
            "https://www.reddit.com/login/", "https://i.redd.it/abc.jpg", "https://www.reddit.com/r/synthetic/comments/UPPER/"))
            assertNull(raw, read(raw))
        assertEquals(true, RedditPostIdentity.isLoginWall(checkNotNull(HttpsUrl.parse("https://www.reddit.com/login/"))))
    }

    @Test
    fun theCheckIsAnsweredWithTheDoubledSeedInFormOrder() {
        val check = checkNotNull(javaClass.getResource("/sites/reddit/challenge.html")).readText()
        val page = checkNotNull(HttpsUrl.parse("https://www.reddit.com$Permalink"))
        assertEquals("https://www.reddit.com$Permalink?solution=0123456789abcdef0123456789abcdef&js_challenge=1&jsc_token=${"ab".repeat(32)}&jsc_orig_r=",
            RedditJsChallenge.answer(check, page).toString())
        assertNull(RedditJsChallenge.answer(check.replace("action=\"$Permalink\"", "action=\"https://evil.test$Permalink\""), page))
        assertNull(RedditJsChallenge.answer(check.replace("e+e", "e+e+e"), page))
        assertNull(RedditJsChallenge.answer(check.replace("method=\"GET\"", "method=\"POST\""), page))
        assertNull(RedditJsChallenge.answer(check + " ".repeat(32 * 1024), page))
        assertNull(RedditJsChallenge.answer(postPage(), page))
    }

    @Test
    fun aVideoPostNamesItsVideoTitleAndPicture() {
        val video = checkNotNull(RedditReader.parsePage(postPage(), PostId, null))
        assertEquals(VideoId, video.videoId)
        assertEquals("A synthetic & quoted \"video\"", video.title)
        assertEquals("synthetic_author", video.author)
        assertEquals(44L, video.durationSeconds)
        assertEquals("external-preview.redd.it", HttpsUrl.parse(checkNotNull(video.thumbnail))?.host)
        assertNotNull(RedditReader.parsePage(postPage(), PostId, VideoId))
        assertNull(RedditReader.parsePage(postPage(), PostId, "othervid0001"))
    }

    @Test
    fun anythingButAWatchableVideoOfThePostIsNotRead() {
        for (page in listOf(postPage("text"), postPage("image"), postPage("gallery"), postPage("link", content = "https://example.com/"),
            postPage(extra = "nsfw"), postPage(player = false), postPage(content = "https://v.redd.it/../x"), postPage(id = "2bbbbbb")))
            assertNull(RedditReader.parsePage(page, PostId, null))
    }

    @Test
    fun theManifestsFilesBecomeFormats() {
        val video = checkNotNull(RedditReader.parsePage(postPage(), PostId, null))
        val info = JSONObject(checkNotNull(RedditReader.infoJson(video, Manifest, "https://www.reddit.com$Permalink")))
        val formats = info.getJSONArray("formats")
        val byId = (0 until formats.length()).associate { formats.getJSONObject(it).getString("format_id") to formats.getJSONObject(it) }
        assertEquals(setOf("dash-720", "dash-360", "dash-audio-128"), byId.keys)
        assertEquals("https://v.redd.it/$VideoId/DASH_720.mp4", byId.getValue("dash-720").getString("url"))
        assertEquals(720, byId.getValue("dash-720").getInt("height"))
        assertEquals("none", byId.getValue("dash-720").getString("acodec"))
        assertEquals("m4a", byId.getValue("dash-audio-128").getString("ext"))
        assertEquals("none", byId.getValue("dash-audio-128").getString("vcodec"))
        assertEquals(44.0, info.getDouble("duration"), 0.0)
        assertEquals("Reddit", info.getString("extractor_key"))
        assertNull(RedditReader.infoJson(video, Manifest.replace("DASH_720.mp4", "https://evil.test/a.mp4"), "https://www.reddit.com$Permalink"))
        assertEquals(0, info.getJSONObject("subtitles").length() + info.getJSONObject("automatic_captions").length())
    }

    @Test
    fun aTextRepresentationIsTheVideosCaptionsAndElseTheSpokenOnesAreLookedFor() {
        val video = checkNotNull(RedditReader.parsePage(postPage(), PostId, null))
        val captioned = Manifest.replace("</Period>", "<AdaptationSet contentType=\"text\" lang=\"en\" mimeType=\"text/vtt\"><Representation id=\"9\"><BaseURL>captions.vtt</BaseURL></Representation></AdaptationSet></Period>")
        val listed = JSONObject(checkNotNull(RedditReader.infoJson(video, captioned, "https://www.reddit.com$Permalink") { error("looked for") }))
        assertEquals(3, listed.getJSONArray("formats").length())
        assertEquals("https://v.redd.it/$VideoId/captions.vtt", listed.getJSONObject("subtitles").getJSONArray("en").getJSONObject(0).getString("url"))

        val spoken = "https://v.redd.it/$VideoId/wh_ben_en.vtt"
        val found = JSONObject(checkNotNull(RedditReader.infoJson(video, Manifest, "https://www.reddit.com$Permalink") { spoken }))
        assertEquals(spoken, found.getJSONObject("automatic_captions").getJSONArray("en").getJSONObject(0).getString("url"))
    }
}
