package com.missa.tv360.data.source

import java.io.Serializable

/**
 * Lien de lecture résolu pour un contenu.
 *
 * Une source peut en renvoyer plusieurs (un par qualité) : le registre choisit
 * le meilleur et transmet les en-têtes HTTP au lecteur. C'est le rôle que joue
 * `ExtractorLink` dans un agrégateur multi-sources : séparer « où est le
 * contenu » (la fiche) de « comment le lire » (le flux).
 */
data class ExtractorLink(
    val url: String,
    /** Hauteur en pixels ; 0 quand la source ne l'annonce pas. */
    val quality: Int = 0,
    /** mp4 | hls | mp3 */
    val format: String = "mp4",
    /** En-têtes exigés par le flux (Referer, User-Agent, Origin…). */
    val headers: Map<String, String> = emptyMap(),
    val audioOnly: Boolean = false,
) : Serializable {

    /** Libellé court affichable dans un sélecteur de qualité. */
    val label: String
        get() = when {
            quality >= 2160 -> "4K"
            quality >= 1080 -> "1080p"
            quality >= 720 -> "720p"
            quality >= 480 -> "480p"
            quality > 0 -> "${quality}p"
            format == "hls" -> "Auto"
            else -> "SD"
        }

    companion object {
        /**
         * Meilleur lien : la qualité la plus haute, puis le MP4 direct (moins
         * de dépendances côté lecteur) plutôt que le HLS.
         */
        fun best(links: List<ExtractorLink>): ExtractorLink? =
            links.filter { it.url.isNotBlank() }
                .maxWithOrNull(
                    compareBy<ExtractorLink> { it.quality }
                        .thenBy { if (it.format == "mp4") 1 else 0 },
                )

        /**
         * Filtre les en-têtes issus d'une source : clé sans espace ni « : »,
         * valeur sans retour à la ligne. Empêche l'injection d'en-têtes HTTP
         * par un flux distant.
         */
        fun sanitizeHeaders(raw: Map<String, String>): Map<String, String> {
            val safe = LinkedHashMap<String, String>()
            raw.forEach { (key, value) ->
                val name = key.trim()
                val content = value.trim()
                if (name.isEmpty() || content.isEmpty()) return@forEach
                if (name.length > 64 || content.length > 512) return@forEach
                if (name.any { it <= ' ' || it == ':' }) return@forEach
                if (content.any { it == '\r' || it == '\n' || it == '\u0000' }) return@forEach
                safe[name] = content
            }
            return safe
        }
    }
}
