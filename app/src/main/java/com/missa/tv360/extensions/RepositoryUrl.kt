package com.missa.tv360.extensions

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Entrée explicitement fournie par l'utilisateur dans la boîte d'ajout de dépôt. */
internal sealed interface RepositoryInput {
    val url: String

    /** URL du manifeste, ou URL de partage contenant directement cette adresse. */
    data class Direct(override val url: String) : RepositoryInput

    /** Code court Cloudstream dont la redirection doit être validée avant lecture. */
    data class ShortLink(override val url: String) : RepositoryInput
}

/**
 * Interprète les formats publics de partage Cloudstream sans préconfigurer de dépôt.
 *
 * Un code nu est un raccourci, et non un nom d'hôte : `cloudstreamrepo` doit
 * donc être résolu par le service de partage avant tout accès au manifeste.
 */
internal fun parseRepositoryInput(raw: String): RepositoryInput {
    var value = raw.trim()
    if (value.contains(" : ")) {
        value = value.substringAfter(" : ").trim()
    }
    if (value.isEmpty()) {
        throw ExtensionException("Saisissez l’adresse ou le code d’un dépôt.")
    }

    fun directWithDefaultScheme(input: String): RepositoryInput.Direct =
        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
        ) {
            RepositoryInput.Direct(input)
        } else {
            RepositoryInput.Direct("https://$input")
        }

    if (value.startsWith("cloudstreamrepo://")) {
        return directWithDefaultScheme(value.removePrefix("cloudstreamrepo://").trim())
    }
    if (value.startsWith("https://cs.repo/")) {
        val shared = value.removePrefix("https://cs.repo/").removePrefix("?").trim()
        return directWithDefaultScheme(shared)
    }
    if (value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
    ) {
        return RepositoryInput.Direct(value)
    }

    if (SHORT_CODE.matches(value)) {
        if (value == "!") throw ExtensionException("Code de dépôt incomplet.")
        val shortUrl = if (value.startsWith("!")) {
            "https://py.md/${value.removePrefix("!")}"
        } else {
            "https://cutt.ly/$value"
        }
        return RepositoryInput.ShortLink(shortUrl)
    }

    // Conserve l'ancienne facilité de saisie pour une URL de domaine sans schéma.
    return RepositoryInput.Direct("https://$value")
}

private val SHORT_CODE = Regex("^[a-zA-Z0-9!_-]+$")

/** Message court pour le bandeau ; la cause et la pile restent dans les détails. */
internal fun repositoryErrorMessage(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    return when {
        causes.any { it is UnknownHostException } ->
            "Dépôt injoignable. Vérifiez le code ou l’adresse, puis votre connexion."
        causes.any { it is SocketTimeoutException } ->
            "Le dépôt ne répond pas. Réessayez dans un moment."
        else -> error.message?.takeIf(String::isNotBlank) ?: "Dépôt illisible."
    }
}
