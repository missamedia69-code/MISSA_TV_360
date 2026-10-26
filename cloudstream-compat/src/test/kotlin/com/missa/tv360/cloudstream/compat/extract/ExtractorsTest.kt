package com.missa.tv360.cloudstream.compat.extract

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.missa.tv360.cloudstream.compat.CloudstreamCompatRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aucun test ne contacte un vrai site : les pages sont fabriquées à la main ou lues dans src/test/resources. */
class ExtractorsTest {

    private class FakeFetcher(private val pages: Map<String, PageResponse>) : PageFetcher {
        val requested = mutableListOf<String>()
        override suspend fun get(url: String, referer: String?): PageResponse? {
            requested += url
            return pages[url]
        }
        override suspend fun post(url: String, referer: String?, data: Map<String, String>): PageResponse? {
            requested += "POST $url"
            return pages[url]
        }
    }

    private fun page(url: String, text: String, code: Int = 200) = url to PageResponse(text, url, code)

    private inline fun <T> withFetcher(fetcher: PageFetcher, block: () -> T): T {
        val previous = ExtractorHttp.fetcher
        ExtractorHttp.fetcher = fetcher
        try {
            return block()
        } finally {
            ExtractorHttp.fetcher = previous
        }
    }

    private fun resolve(url: String, referer: String? = null): Pair<Boolean, List<ExtractorLink>> = runBlocking {
        val links = mutableListOf<ExtractorLink>()
        val ok = loadExtractor(url, referer ?: "https://site.example/", { _: SubtitleFile -> }, { links += it })
        ok to links
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("extractors/$name")) { "ressource absente : $name" }
            .bufferedReader().readText()

    // ---- parsing pur ------------------------------------------------------------------------------

    @Test
    fun toBaseMatchesThePackerDigits() {
        assertEquals("a", ExtractorParsing.toBase(10, 62))
        assertEquals("A", ExtractorParsing.toBase(36, 62))
        assertEquals("10", ExtractorParsing.toBase(62, 62))
        assertEquals("z", ExtractorParsing.toBase(35, 36))
        assertEquals("1a", ExtractorParsing.toBase(46, 36))
    }

    @Test
    fun unpackJsRestoresPackedSource() {
        val unpacked = ExtractorParsing.unpackJs(resource("packed_sources.js"))
        assertNotNull(unpacked)
        assertTrue(unpacked!!.contains("jwplayer(\"vplayer\")"))
        assertEquals(listOf("https://cdn.example.test/v/abc123.mp4"), ExtractorParsing.parseSourcesPage(resource("packed_sources.js")))
    }

    @Test
    fun unpackJsReturnsNullWithoutPackedBlock() {
        assertNull(ExtractorParsing.unpackJs("<html>rien</html>"))
    }

    @Test
    fun findMediaUrlsHandlesEscapesDuplicatesAndQueries() {
        val text = """{"a":"https:\/\/cdn.test\/v\/a.m3u8?t=1&e=2","b":"https://cdn.test/v/a.m3u8?t=1&e=2","c":"https://x.test/b.MP4"} <img src="https://x.test/p.png">"""
        assertEquals(
            listOf("https://cdn.test/v/a.m3u8?t=1&e=2", "https://x.test/b.MP4"),
            ExtractorParsing.findMediaUrls(text),
        )
    }

    @Test
    fun parseSourcesPageReadsSourcesArray() {
        val html = """<script>jwplayer("v").setup({sources: ["https://cdn.test/v/a.mp4"], image:"x.jpg"});</script>"""
        assertEquals(listOf("https://cdn.test/v/a.mp4"), ExtractorParsing.parseSourcesPage(html))
        assertTrue(ExtractorParsing.parseSourcesPage("<html>vide</html>").isEmpty())
    }

    @Test
    fun parseVidozaReadsSrcProperty() {
        val html = """<script>sourcesCode: [{ src: "https://s1.test/v/xyz.mp4", type: "video/mp4", label:"SD"}]</script>"""
        assertEquals(listOf("https://s1.test/v/xyz.mp4"), ExtractorParsing.parseVidoza(html))
        val tag = """<video><source src="https://s1.test/v/t.mp4" type="video/mp4"></video>"""
        assertEquals(listOf("https://s1.test/v/t.mp4"), ExtractorParsing.parseVidoza(tag))
    }

    @Test
    fun parseVoeReadsHlsAndBase64() {
        val hls = """var sources = {"hls": "https:\/\/edge.test\/hls\/master.m3u8", "video_height": 720};"""
        assertEquals(listOf("https://edge.test/hls/master.m3u8"), ExtractorParsing.parseVoe(hls))
        // "https://edge.test/v/a.mp4" en base64
        val encoded = "aHR0cHM6Ly9lZGdlLnRlc3Qvdi9hLm1wNA=="
        assertEquals("https://edge.test/v/a.mp4", ExtractorParsing.decodeBase64(encoded))
        assertEquals(listOf("https://edge.test/v/a.mp4"), ExtractorParsing.parseVoe("""{"source": "$encoded"}"""))
        assertNull(ExtractorParsing.decodeBase64("pas du base64 !!"))
    }

    @Test
    fun findJsRedirectReadsWindowLocation() {
        assertEquals("/e/abc", ExtractorParsing.findJsRedirect("<script>window.location.href = '/e/abc';</script>"))
        assertNull(ExtractorParsing.findJsRedirect("<html></html>"))
    }

    @Test
    fun parseDoodAndBuildUrl() {
        val html = """<script>$.get('/pass_md5/11111-22222-abcdef/zzzz', function(data){ return data + makePlay() + "?token=tok123abc"; });</script>"""
        val parsed = ExtractorParsing.parseDood(html)
        assertEquals("/pass_md5/11111-22222-abcdef/zzzz" to "tok123abc", parsed)
        assertNull(ExtractorParsing.parseDood("<html>protection</html>"))
        assertEquals(
            "https://cdn.test/v/base1234567890?token=tok&expiry=42",
            ExtractorParsing.buildDoodUrl("https://cdn.test/v/base", "tok", "1234567890", 42L),
        )
        assertNull(ExtractorParsing.buildDoodUrl("RELOAD", "tok", "1234567890", 42L))
        assertEquals(10, ExtractorParsing.randomAlnum(10).length)
        assertTrue(ExtractorParsing.randomAlnum(10).all { it.isLetterOrDigit() })
    }

    @Test
    fun parseFembedJson() {
        val json = """{"success":true,"data":[{"file":"https:\/\/f.test\/a.mp4","label":"720p","type":"mp4"},{"file":"https://f.test/b.mp4","label":"480p"}]}"""
        assertEquals(
            listOf("https://f.test/a.mp4" to 720, "https://f.test/b.mp4" to 480),
            ExtractorParsing.parseFembedJson(json),
        )
        assertTrue(ExtractorParsing.parseFembedJson("""{"success":false,"data":"Video not found"}""").isEmpty())
        assertTrue(ExtractorParsing.parseFembedJson("<html>").isEmpty())
    }

    // ---- extracteurs complets via loadExtractor (réseau simulé) ----------------------------------

    @Test
    fun uqloadCoIsResolvedByTheBuiltInExtractor() {
        val embed = "https://uqload.co/embed-abc.html"
        val fetcher = FakeFetcher(
            mapOf(page(embed, """<script>player.setup({sources: ["https://m1.uqload.test/v/abc.mp4"]});</script>""")),
        )
        val (ok, links) = withFetcher(fetcher) { resolve(embed) }
        assertTrue(ok)
        assertEquals(1, links.size)
        assertEquals("https://m1.uqload.test/v/abc.mp4", links[0].url)
        assertEquals("https://uqload.co/", links[0].referer)
        assertFalse(links[0].isM3u8)
        assertEquals("Uqload", links[0].name)
    }

    @Test
    fun doodUsesTheRequestedHostAndBuildsTheFinalLink() {
        val embed = "https://dood.so/e/imykqcdk70od"
        val fetcher = FakeFetcher(
            mapOf(
                page(embed, """<script>fetch('/pass_md5/aaa-bbb/ccc'); var u = "?token=tok999";</script>"""),
                page("https://dood.so/pass_md5/aaa-bbb/ccc", "https://cdn.dood.test/stream/base"),
            ),
        )
        val (ok, links) = withFetcher(fetcher) { resolve(embed) }
        assertTrue(ok)
        assertEquals(1, links.size)
        val url = links[0].url
        assertTrue(url, Regex("""https://cdn\.dood\.test/stream/base[A-Za-z0-9]{10}\?token=tok999&expiry=\d+""").matches(url))
        assertEquals("https://dood.so/", links[0].referer)
        assertTrue(fetcher.requested.contains("https://dood.so/pass_md5/aaa-bbb/ccc"))
    }

    @Test
    fun doodWithUnexpectedPageEmitsNothing() {
        val embed = "https://dood.so/e/gone"
        val fetcher = FakeFetcher(mapOf(page(embed, "<html>Video not found</html>")))
        val (ok, links) = withFetcher(fetcher) { resolve(embed) }
        assertFalse(ok)
        assertTrue(links.isEmpty())
    }

    @Test
    fun pluginExtractorThatEmitsNothingFallsBackToTheBuiltInOne() {
        // Comme DoodStream/Fembed des extensions : sous-classe sans extraction propre, mainUrl différent.
        class PluginDood : DoodLaExtractor() {
            override val name = "DoodStream"
            override val mainUrl = "https://do7go.com"
        }
        // Un extracteur de plugin qui ne produit rien et se déclare sur l'hôte canonique.
        class EmptyUqload : ExtractorApi() {
            override val name = "EmptyUqload"
            override val mainUrl = "https://uqload.co"
            override val requiresReferer = false
        }
        val embed = "https://uqload.co/embed-zzz.html"
        val fetcher = FakeFetcher(mapOf(page(embed, """sources: ["https://m.test/v/z.mp4"]""")))
        val plugin = EmptyUqload().also { it.sourcePlugin = "test-plugin.cs3" }
        val dood = PluginDood().also { it.sourcePlugin = "test-plugin.cs3" }
        CloudstreamCompatRegistry.register("test-plugin.cs3", plugin)
        CloudstreamCompatRegistry.register("test-plugin.cs3", dood)
        try {
            val (ok, links) = withFetcher(fetcher) { resolve(embed) }
            assertTrue(ok)
            assertEquals("https://m.test/v/z.mp4", links.single().url)
            assertEquals("Uqload", links.single().name)
        } finally {
            CloudstreamCompatRegistry.unload("test-plugin.cs3")
        }
    }

    @Test
    fun voeFollowsOneJavascriptRedirect() {
        val first = "https://voe.sx/e/abc"
        val second = "https://voe.sx/real/abc"
        val fetcher = FakeFetcher(
            mapOf(
                page(first, "<script>window.location.href = '/real/abc';</script>"),
                page(second, """{"hls": "https://edge.test/h/master.m3u8"}"""),
            ),
        )
        val (ok, links) = withFetcher(fetcher) { resolve(first) }
        assertTrue(ok)
        assertEquals("https://edge.test/h/master.m3u8", links.single().url)
        assertTrue(links.single().isM3u8)
    }

    @Test
    fun unknownHostReturnsFalseWithoutNetwork() {
        val fetcher = FakeFetcher(emptyMap())
        val (ok, links) = withFetcher(fetcher) { resolve("https://inconnu.example/e/1") }
        assertFalse(ok)
        assertTrue(links.isEmpty())
        assertTrue(fetcher.requested.isEmpty())
    }
}

class HtmlDiagnosticsTest {
    @Test
    fun titleIsExtractedCleanedAndTruncated() {
        assertEquals("Anime-Sama - Catalogue", HtmlDiagnostics.title("<html><head><TITLE>\n  Anime-Sama -\n Catalogue </TITLE></head>"))
        assertNull(HtmlDiagnostics.title("<html><body>pas de titre</body></html>"))
        assertNull(HtmlDiagnostics.title("<title>   </title>"))
        assertEquals(81, HtmlDiagnostics.title("<title>" + "a".repeat(200) + "</title>")!!.length)
    }

    @Test
    fun interstitialTitlesAreFlagged() {
        assertTrue(HtmlDiagnostics.looksLikeInterstitial("Just a moment..."))
        assertTrue(HtmlDiagnostics.looksLikeInterstitial("This domain is for sale"))
        assertFalse(HtmlDiagnostics.looksLikeInterstitial("Copycat - Anime-Sama"))
        assertFalse(HtmlDiagnostics.looksLikeInterstitial(null))
    }
}

class HtmlPreviewTest {
    @Test
    fun previewFlattensAndTruncates() {
        assertEquals("(corps vide)", HtmlDiagnostics.preview("  \n\t "))
        assertEquals("<div> a b </div>", HtmlDiagnostics.preview("<div>\n a\n\n b </div>"))
        assertEquals(201, HtmlDiagnostics.preview("x".repeat(500)).length)
    }
}
