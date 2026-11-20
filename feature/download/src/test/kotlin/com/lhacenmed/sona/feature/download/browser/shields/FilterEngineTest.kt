package com.lhacenmed.sona.feature.download.browser.shields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    private val engine = FilterEngine.of(
        """
        ! A comment
        [Adblock Plus 2.0]
        ||ads.example.com^
        ||tracker.net^${'$'}third-party
        /banner/ads/*${'$'}image
        @@||ads.example.com/allowed^
        ||cdn.site.org/pixel.gif${'$'}important
        @@||cdn.site.org^
        ||badly.blocked.com^
        ||badly.blocked.com^${'$'}badfilter
        /^https?:\/\/[a-z]+\.evil\.com\/track/${'$'}script
        ||news.example^${'$'}domain=reader.com|~safe.reader.com
        ##.ad-banner
        ###sponsored > div
        ##div[data-ad]
        example.com##.site-ad
        ~quiet.com##.noisy
        example.com#@#.ad-banner
        ##.card:has(.promo)
        ##+js(nowebrtc)
        example.com#?#.x:has-text(Sponsored)
        @@||plain.org^${'$'}generichide
        youtube.com##+js(json-prune, playerResponse.adPlacements playerResponse.playerAds)
        quiet.org##+js(set-constant, ads, false)
        quiet.org#@#+js()
        fb.com##div[role="feed"] > div:has-text(Sponsored):upward(2)
        fb.com##.promo:remove()
        ||popads.net^${'$'}popup
        ||imasdk.googleapis.com/js/sdkloader/ima3.js${'$'}script,redirect=google-ima.js
        """.trimIndent().lineSequence(),
    )

    private fun request(url: String, page: String, type: RequestType? = null) =
        FilterRequest(url, url.substringAfter("://").substringBefore('/'), page, type)

    @Test
    fun blocksByHost() {
        assertEquals(Block.MATCHED, engine.blocking(request("https://ads.example.com/a.js", "news.com")))
        assertEquals(Block.MATCHED, engine.blocking(request("https://x.ads.example.com/a.js", "news.com")))
        assertEquals(Block.NONE, engine.blocking(request("https://notads.example.com/a.js", "news.com")))
    }

    @Test
    fun exceptionsLetThrough() {
        val allowed = request("https://ads.example.com/allowed/x.js", "news.com")
        assertEquals(Block.MATCHED, engine.blocking(allowed))
        assertTrue(engine.excepts(allowed))
    }

    @Test
    fun importantBeatsException() {
        val pixel = request("https://cdn.site.org/pixel.gif", "news.com")
        assertEquals(Block.IMPORTANT, engine.blocking(pixel))
        assertTrue(engine.excepts(pixel))
    }

    @Test
    fun thirdPartyOnly() {
        assertEquals(Block.MATCHED, engine.blocking(request("https://tracker.net/t", "news.com")))
        assertEquals(Block.NONE, engine.blocking(request("https://tracker.net/t", "www.tracker.net")))
    }

    @Test
    fun typesAndUnknownTypes() {
        assertEquals(Block.MATCHED, engine.blocking(request("https://a.com/banner/ads/1.png", "b.com", RequestType.IMAGE)))
        assertEquals(Block.NONE, engine.blocking(request("https://a.com/banner/ads/1.png", "b.com", RequestType.SCRIPT)))
        // What the web view does not tell the type of is taken for a script or a fetch - never an image.
        assertEquals(Block.NONE, engine.blocking(request("https://a.com/banner/ads/1", "b.com", null)))
        assertEquals(Block.MATCHED, engine.blocking(request("https://www.evil.com/track/1", "b.com", null)))
    }

    @Test
    fun badFilterTakesOut() {
        assertEquals(Block.NONE, engine.blocking(request("https://badly.blocked.com/x", "b.com")))
    }

    @Test
    fun regexFilters() {
        assertEquals(Block.MATCHED, engine.blocking(request("https://www.evil.com/track/1", "b.com", RequestType.SCRIPT)))
        assertEquals(Block.NONE, engine.blocking(request("https://www.evil.com/other", "b.com", RequestType.SCRIPT)))
    }

    @Test
    fun domainOption() {
        assertEquals(Block.MATCHED, engine.blocking(request("https://news.example/x", "reader.com")))
        assertEquals(Block.NONE, engine.blocking(request("https://news.example/x", "safe.reader.com")))
        assertEquals(Block.NONE, engine.blocking(request("https://news.example/x", "other.com")))
    }

    @Test
    fun hidingBySiteAndGeneric() {
        val page = engine.pageHiding("https://www.example.com/", "www.example.com", aggressive = false)
        assertTrue(".site-ad" in page.selectors)
        assertTrue("div[data-ad]" in page.selectors)
        assertTrue(".noisy" in page.selectors)
        assertTrue(page.byClassAndId)
        // A `:has` selector led by a class is looked up by it - and hides only where blocking is aggressive.
        assertFalse(".card:has(.promo)" in engine.classIdSelectors("news.com", listOf("card"), emptyList(), aggressive = false))
        assertTrue(".card:has(.promo)" in engine.classIdSelectors("news.com", listOf("card"), emptyList(), aggressive = true))
        assertFalse(".noisy" in engine.pageHiding("https://quiet.com/", "quiet.com", aggressive = false).selectors)
    }

    @Test
    fun classAndIdSelectorsWithExceptions() {
        assertEquals(listOf(".ad-banner"), engine.classIdSelectors("news.com", listOf("ad-banner", "other"), emptyList(), aggressive = false))
        assertEquals(listOf("#sponsored > div"), engine.classIdSelectors("news.com", emptyList(), listOf("sponsored"), aggressive = false))
        assertEquals(emptyList<String>(), engine.classIdSelectors("www.example.com", listOf("ad-banner"), emptyList(), aggressive = false))
    }

    @Test
    fun genericHideLifted() {
        val page = engine.pageHiding("https://plain.org/", "plain.org", aggressive = false)
        assertFalse("div[data-ad]" in page.selectors)
        assertFalse(page.byClassAndId)
    }

    @Test
    fun entitiesAndSites() {
        assertEquals("example.co.uk", siteOf("www.example.co.uk"))
        assertTrue(hostMatches("news.google.co.uk", "google.*"))
        assertFalse(hostMatches("google.evil.com", "google.*"))
        assertEquals(".ad", classOrIdOf(".ad > img"))
        assertEquals(null, classOrIdOf(".a, .b"))
    }

    @Test
    fun scriptlets() {
        assertEquals(
            // The site's own, then the generic `##+js(nowebrtc)` every site runs.
            listOf(listOf("json-prune", "playerResponse.adPlacements playerResponse.playerAds"), listOf("nowebrtc")),
            engine.scriptletsFor("m.youtube.com"),
        )
        // `#@#+js()` lifts every scriptlet of the site.
        assertEquals(emptyList<List<String>>(), engine.scriptletsFor("quiet.org"))
    }

    @Test
    fun proceduralFilters() {
        val aggressive = engine.proceduralFor("www.fb.com", aggressive = true)
        assertEquals(2, aggressive.size)
        assertTrue(aggressive.any { "has-text" in it && "upward" in it })
        // Standard keeps only what acts on a plain selector.
        val standard = engine.proceduralFor("www.fb.com", aggressive = false)
        assertEquals(1, standard.size)
        assertTrue(".promo" in standard[0] && "\"remove\"" in standard[0] && "has-text" !in standard[0])
    }

    @Test
    fun popupsAndRedirects() {
        assertEquals(Block.MATCHED, engine.blocking(request("https://popads.net/x", "site.com", RequestType.POPUP)))
        assertEquals(Block.NONE, engine.blocking(request("https://popads.net/x.js", "site.com", RequestType.SCRIPT)))
        val ima = request("https://imasdk.googleapis.com/js/sdkloader/ima3.js", "video.com", RequestType.SCRIPT)
        assertEquals(Block.MATCHED, engine.blocking(ima))
        assertEquals("google-ima.js", engine.redirectFor(ima))
    }
}
