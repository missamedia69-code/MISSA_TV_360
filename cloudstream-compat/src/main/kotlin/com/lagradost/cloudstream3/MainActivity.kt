package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

private const val CLOUDSTREAM_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

private val jsonResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T =
        mapper.readValue(text, kClass.java)

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? = try {
        parse(text, kClass)
    } catch (_: Exception) {
        null
    }

    override fun writeValueAsString(obj: Any): String = mapper.writeValueAsString(obj)
}

/** Client NiceHttp réel attendu par le DEX ; les requêtes ne sont pas simulées. */
val app: Requests = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to CLOUDSTREAM_USER_AGENT)
}

/** Seul l'événement statique référencé par les réglages du prototype est requis. */
class MainActivity {
    companion object {
        val afterPluginsLoadedEvent: Event<Any?> = Event()
    }
}
