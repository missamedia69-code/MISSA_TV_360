package com.lagradost.cloudstream3

import kotlin.jvm.functions.Function1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainApiExtensionContractTest {
    private val api = object : MainAPI() {
        override var name: String = "TestApi"
        override var mainUrl: String = "https://example.com"
    }

    @Test
    fun `helper newMovieSearchResponse conserve la signature statique attendue par le DEX`() {
        val helper = Class.forName("com.lagradost.cloudstream3.MainAPIKt").getMethod(
            "newMovieSearchResponse",
            MainAPI::class.java,
            String::class.java,
            String::class.java,
            TvType::class.java,
            Boolean::class.javaPrimitiveType,
            Function1::class.java,
        )

        assertEquals(MovieSearchResponse::class.java, helper.returnType)

        val movie = api.newMovieSearchResponse("Test Movie", "/movie/1") {
            year = 2024
            posterUrl = "https://example.com/poster.jpg"
        }

        assertEquals("Test Movie", movie.name)
        assertEquals("https://example.com/movie/1", movie.url)
        assertEquals(2024, movie.year)
        assertEquals("https://example.com/poster.jpg", movie.posterUrl)
    }

    @Test
    fun `addDubStatus 4 parametres conserve la signature attendue par AllWish et AniKoto`() {
        val mainApiKtClass = Class.forName("com.lagradost.cloudstream3.MainAPIKt")

        val addDubStatusMethod = mainApiKtClass.methods.firstOrNull {
            it.name == "addDubStatus" && it.parameterCount == 5
        }

        assertNotNull("La méthode addDubStatus(AnimeSearchResponse, boolean, boolean, Integer, Integer) doit exister", addDubStatusMethod)

        val anime = api.newAnimeSearchResponse("Test Anime", "/anime/1")
        anime.addDubStatus(dubExist = true, subExist = true, dubEpisodes = 12, subEpisodes = 24)

        assertTrue(anime.dubStatus?.contains(DubStatus.Dubbed) == true)
        assertTrue(anime.dubStatus?.contains(DubStatus.Subbed) == true)
        assertEquals(12, anime.episodes[DubStatus.Dubbed])
        assertEquals(24, anime.episodes[DubStatus.Subbed])
    }
}
