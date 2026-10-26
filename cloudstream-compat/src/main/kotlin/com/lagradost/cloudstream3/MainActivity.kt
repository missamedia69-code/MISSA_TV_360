package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event
import com.missa.tv360.api.PluginLog
import com.missa.tv360.cloudstream.compat.extract.HtmlDiagnostics
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

private val htmlDumpCounter = java.util.concurrent.atomic.AtomicInteger(0)

/** Hôtes dont on copie TOUTES les pages : fichier cache/html-debug/hosts.txt (un fragment d'hôte par ligne). */
private fun shouldDumpHost(host: String): Boolean {
    val ctx = CloudStreamApp.context ?: return false
    val marker = java.io.File(java.io.File(ctx.cacheDir, "html-debug"), "hosts.txt")
    if (!marker.exists()) return false
    return runCatching {
        marker.readLines().map { it.trim() }.filter { it.isNotEmpty() }.any { host.contains(it) }
    }.getOrDefault(false)
}

/**
 * Copie (debug) d'une page HTML dans cache/html-debug/page-N.html (6 fichiers tournants) : pages sans titre,
 * ou pages d'un hôte listé dans hosts.txt.
 * Le contenu est la page publique du site (aucun en-tête ni cookie n'est écrit).
 */
private fun dumpHtmlForDebug(host: String, path: String, body: String) {
    val ctx = CloudStreamApp.context ?: return
    runCatching {
        val dir = java.io.File(ctx.cacheDir, "html-debug").apply { mkdirs() }
        val name = "page-${htmlDumpCounter.getAndIncrement() % 6}.html"
        java.io.File(dir, name).writeText(body)
        PluginLog.i("HTML $host$path : copie écrite cache/html-debug/$name (${body.length} caractères)")
    }
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
        val finalHost = response.request.url.host
        if (finalHost != host) PluginLog.i("HTTP redirection $host -> $finalHost")

        val contentType = response.header("Content-Type") ?: ""
        if (contentType.contains("text/html", ignoreCase = true)) {
            val bodySnippet = response.peekBody(2048).string()
            val head = response.peekBody(65536).string()
            val pageTitle = HtmlDiagnostics.title(head)
            if (pageTitle == null || shouldDumpHost(finalHost)) {
                dumpHtmlForDebug(finalHost, response.request.url.encodedPath, response.peekBody(524288).string())
            }
            if (pageTitle == null) {
                PluginLog.i(
                    "HTML $finalHost sans titre : content-length=${response.header("Content-Length") ?: "?"} " +
                        "début=\"${HtmlDiagnostics.preview(head)}\""
                )
            }
            PluginLog.i(
                "HTML $finalHost titre=\"${pageTitle ?: "(aucun)"}\"" +
                    if (HtmlDiagnostics.looksLikeInterstitial(pageTitle)) " (page suspecte : vérification, erreur ou domaine changé ?)" else ""
            )
            if (bodySnippet.contains("just a moment", ignoreCase = true) ||
                bodySnippet.contains("cf-chl", ignoreCase = true) ||
                bodySnippet.contains("cloudflare", ignoreCase = true) ||
                bodySnippet.contains("captcha", ignoreCase = true) ||
                bodySnippet.contains("attention required", ignoreCase = true) ||
                bodySnippet.contains("checking your browser", ignoreCase = true) ||
                bodySnippet.contains("ddos", ignoreCase = true)
            ) {
                PluginLog.e("Page de protection détectée sur $host (code $code)")
            } else if (request.url.encodedPath.contains("api", ignoreCase = true) ||
                request.url.encodedPath.contains("json", ignoreCase = true) ||
                request.header("Accept")?.contains("json", ignoreCase = true) == true
            ) {
                PluginLog.e("HTML reçu au lieu de JSON sur $host")
            }
        } else if (code == 403 || code == 503) {
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
