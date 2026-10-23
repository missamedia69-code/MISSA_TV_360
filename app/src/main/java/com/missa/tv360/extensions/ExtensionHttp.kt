package com.missa.tv360.extensions

import com.missa.tv360.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Catégories stables utilisées par le chargeur, les états persistés et l'UI. */
enum class ExtensionErrorType {
    INCOMPATIBLE,
    MISSING_DEPENDENCY,
    BAD_MANIFEST,
    LOAD_FAILED,
}

/** Message court pour l'UI ; la stack et les descripteurs restent dans les détails. */
class ExtensionException(
    message: String,
    cause: Throwable? = null,
    val type: ExtensionErrorType = ExtensionErrorType.LOAD_FAILED,
    val technicalDetails: String? = null,
) : Exception(message, cause)

/**
 * Accès HTTP du moteur d'extensions.
 *
 * Différences assumées par rapport à un simple `HttpURLConnection` :
 *  - **HTTPS obligatoire** (sauf boucle locale en build debug) ;
 *  - redirections suivies MANUELLEMENT pour revalider chaque saut (évite un
 *    downgrade https → http en cours de route) ;
 *  - **taille maximale** imposée : un dépôt compromis ne peut pas saturer la
 *    mémoire de l'appareil ;
 *  - délais courts : ces appels ne doivent jamais bloquer l'interface.
 */
internal object ExtensionHttp {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 3

    private val LOOPBACK_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

    /** Valide une URL avant tout usage réseau (dépôt, liste, artefact). */
    fun checkUrl(rawUrl: String) {
        val url = try {
            URL(rawUrl)
        } catch (e: Exception) {
            throw ExtensionException("URL invalide : $rawUrl", e)
        }
        val scheme = url.protocol?.lowercase()
        val host = url.host?.lowercase().orEmpty()

        if (host.isBlank()) throw ExtensionException("URL sans hôte : $rawUrl")

        when (scheme) {
            "https" -> Unit
            "http" -> {
                // Toléré uniquement pour un serveur local de développement,
                // comme la politique réseau de l'application.
                val allowed = BuildConfig.DEBUG && host in LOOPBACK_HOSTS
                if (!allowed) {
                    throw ExtensionException(
                        "HTTP refusé (HTTPS obligatoire) : $rawUrl"
                    )
                }
            }

            else -> throw ExtensionException("Schéma d'URL refusé : $scheme")
        }
    }

    fun getText(url: String, maxBytes: Int): String =
        String(getBytes(url, maxBytes), Charsets.UTF_8)

    fun getBytes(rawUrl: String, maxBytes: Int): ByteArray {
        var current = rawUrl
        var redirects = 0

        while (true) {
            checkUrl(current)
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, application/octet-stream, */*")
                setRequestProperty("User-Agent", "MISSA-TV-360/${BuildConfig.VERSION_NAME}")
            }

            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw ExtensionException("Redirection sans Location ($code) : $current")
                    redirects++
                    if (redirects > MAX_REDIRECTS) {
                        throw ExtensionException("Trop de redirections : $rawUrl")
                    }
                    current = URL(URL(current), location).toString()
                    continue
                }
                if (code !in 200..299) {
                    throw ExtensionException("HTTP $code pour $current")
                }

                // getContentLengthLong() n'existe qu'à partir de l'API 24 :
                // l'en-tête est lu manuellement pour rester compatible minSdk 21.
                val declared = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                if (declared > maxBytes) {
                    throw ExtensionException(
                        "Réponse trop volumineuse (${declared} o) : $current"
                    )
                }

                return connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream(if (declared > 0) declared.toInt() else 8192)
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) {
                            throw ExtensionException(
                                "Réponse dépassant la limite de $maxBytes o : $current"
                            )
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
