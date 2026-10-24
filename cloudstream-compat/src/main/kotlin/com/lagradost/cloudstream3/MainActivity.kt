package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event
import com.missa.tv360.api.PluginLog
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
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

class HttpDiagnosticsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val start = System.currentTimeMillis()
        val response = try {
            chain.proceed(request)
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - start
            PluginLog.e("HTTP EXCEPTION $host (${duration}ms) :: ${t::class.java.simpleName}: ${t.message}")
            throw t
        }
        val duration = System.currentTimeMillis() - start
        val code = response.code
        PluginLog.i("HTTP $code $host (${duration}ms)")

        if (code == 403 || code == 503) {
            val bodySnippet = response.peekBody(2048).string()
            if (bodySnippet.contains("cloudflare", ignoreCase = true) || bodySnippet.contains("Just a moment", ignoreCase = true)) {
                PluginLog.e("Cloudflare détecté sur $host")
            }
        }
        return response
    }
}

private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(HttpDiagnosticsInterceptor())
    .build()

/** Client NiceHttp réel attendu par le DEX ; les requêtes ne sont pas simulées. */
val app: Requests = Requests(responseParser = jsonResponseParser).apply {
    baseClient = okHttpClient
    defaultHeaders = mapOf("user-agent" to CLOUDSTREAM_USER_AGENT)
}

/** Seul l'événement statique référencé par les réglages du prototype est requis. */
class MainActivity {
    companion object {
        val afterPluginsLoadedEvent: Event<Any?> = Event()
    }
}
