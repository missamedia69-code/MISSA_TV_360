package com.lagradost.cloudstream3

import kotlin.jvm.functions.Function1
import org.junit.Assert.assertEquals
import org.junit.Test

class TwitchMainApiContractTest {
    private val api = object : MainAPI() {
        override var name: String = "Twitch"
        override var mainUrl: String = "https://twitchtracker.com"
    }

    @Test
    fun `helper Live conserve la signature statique attendue par le DEX`() {
        val helper = Class.forName("com.lagradost.cloudstream3.MainAPIKt").getMethod(
            "newLiveSearchResponse",
            MainAPI::class.java,
            String::class.java,
            String::class.java,
            TvType::class.java,
            Boolean::class.javaPrimitiveType,
            Function1::class.java,
        )

        assertEquals(LiveSearchResponse::class.java, helper.returnType)
    }

    @Test
    fun `helper Live conserve l url relative demandee et le poster reel`() {
        val response = api.newLiveSearchResponse(
            name = "Chaîne",
            url = "chaine",
            type = TvType.Live,
            fix = false,
        ) { posterUrl = "https://img.example/live.jpg" }

        assertEquals("chaine", response.url)
        assertEquals("Twitch", response.apiName)
        assertEquals(TvType.Live, response.type)
        assertEquals("https://img.example/live.jpg", response.posterUrl)
    }

    @Test
    fun `fixUrl reproduit les formes utilisees par Twitch`() {
        assertEquals("https://twitchtracker.com/games", api.fixUrl("/games"))
        assertEquals("https://twitchtracker.com/games", api.fixUrl("games"))
        assertEquals("https://cdn.example/image.jpg", api.fixUrl("//cdn.example/image.jpg"))
        assertEquals("https://twitch.tv/chaine", api.fixUrl("https://twitch.tv/chaine"))
    }
}
