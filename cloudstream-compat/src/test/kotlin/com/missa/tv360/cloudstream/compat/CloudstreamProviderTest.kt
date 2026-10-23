package com.missa.tv360.cloudstream.compat

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.missa.tv360.api.Kind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudstreamProviderTest {

    @Test
    fun `main page devient une row sans transformer la page en flux`() = runBlocking {
        val provider = CloudstreamProvider(FakeMainAPI())

        val rows = provider.rows()

        assertEquals(1, rows.size)
        assertEquals("Nouveautés", rows.single().title)
        val item = rows.single().items.single()
        assertEquals("https://source.example/serie/1", item.pageUrl)
        assertTrue(item.stream.url.isEmpty())
        assertEquals(Kind.SERIES, item.kind)
        assertEquals("Fake", item.source)
        assertEquals("https://img.example/poster.jpg", item.thumbnailUrl)
    }

    @Test
    fun `types Cloudstream conserves la distinction film serie direct`() {
        assertEquals(Kind.MOVIE, TvType.Movie.toMissaKind())
        assertEquals(Kind.SERIES, TvType.TvSeries.toMissaKind())
        assertEquals(Kind.LIVE_TV, TvType.Live.toMissaKind())
    }

    @Test
    fun `registre accepte puis retire un provider local`() {
        val api = FakeMainAPI()
        val id = CloudstreamProvider.idFor(api)
        try {
            CloudstreamCompatRegistry.register(null, api)
            assertNotNull(CloudstreamCompatRegistry.providerFor(id))
        } finally {
            CloudstreamCompatRegistry.unload(null)
        }
        assertNull(CloudstreamCompatRegistry.providerFor(id))
    }

    @Test
    fun `constructeur ExtractorLink conserve la signature default du DEX`() {
        val found = ExtractorLink::class.java.declaredConstructors.any { constructor ->
            constructor.isSynthetic &&
                constructor.parameterTypes.size == 10 &&
                constructor.parameterTypes[8] == Int::class.javaPrimitiveType &&
                constructor.parameterTypes[9].name == "kotlin.jvm.internal.DefaultConstructorMarker"
        }
        assertTrue(found)
    }

    @Test
    fun `helper Anime conserve sa signature default`() {
        val found = Class.forName("com.lagradost.cloudstream3.MainAPIKt").declaredMethods.any { method ->
            method.name == "newAnimeSearchResponse\$default" && method.parameterTypes.size == 8
        }
        assertTrue(found)
    }

    private class FakeMainAPI : MainAPI() {
        override var name: String = "Fake"
        override var mainUrl: String = "https://source.example"
        override val hasMainPage: Boolean = true
        override val mainPage: List<MainPageData> = mainPageOf(
            "https://source.example/nouveautes" to "Nouveautés",
        )

        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            return newHomePageResponse(
                request.name,
                listOf(
                    AnimeSearchResponse(
                        name = "Série test",
                        url = "https://source.example/serie/1",
                        apiName = name,
                        posterUrl = "https://img.example/poster.jpg",
                    ),
                ),
            )
        }
    }
}
