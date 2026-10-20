package com.missa.tv360.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.missa.tv360.R
import java.io.Serializable

/**
 * Types de contenu supportés par MISSA TV 360.
 * Chaque type a un libellé court pour les cartes et les fiches détails.
 */
private val CARD_WHITESPACE = Regex("\\s+")

enum class ContentKind(val label: String) {
    LIVE_TV("TV Direct"),
    LIVE_SPORT("Sport Direct"),
    MOVIE("Film"),
    SERIES("Série"),
    SHORT("Film court"),
    RADIO("Radio"),
    PODCAST("Podcast"),
    MUSIC("Musique"),
    KIDS("Jeunesse"),
    GAME("Jeu vidéo");

    val isLive: Boolean get() = this == LIVE_TV || this == LIVE_SPORT
    val isAudioOnly: Boolean get() = this == RADIO || this == PODCAST

    companion object {
        /** Mapping depuis la valeur textuelle du BFF (tolère les inconnus). */
        fun fromApi(value: String?): ContentKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: when (value?.lowercase()) {
                    "live", "tv" -> LIVE_TV
                    "sport" -> LIVE_SPORT
                    "episode" -> SERIES
                    "short", "court" -> SHORT
                    else -> MOVIE
                }
    }
}

/**
 * Modèle d'un contenu vidéo/audio. Objet "clean-room" original.
 */
data class Video(
    val id: String,
    val title: String,
    val description: String,
    @DrawableRes val cardRes: Int,
    val streamUrl: String,
    val kind: ContentKind = ContentKind.MOVIE,
    /** Série : titre de la série + saison/épisode (null sinon). */
    val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Durée estimée en secondes (VOD uniquement). */
    val durationSec: Int? = null,
    val format: String = "mp4", // mp4 | hls | mp3
    /** true si cette carte ouvre l'écran des jeux téléchargeables. */
    val isGamesLauncher: Boolean = false,
    /** Vignette distante (catalogue du dépôt). Si présente, remplace cardRes. */
    val cardUrl: String? = null,
    /** Source brute à résoudre au clic (ex. recherche Internet Archive). */
    val sourceId: String? = null,
    /** Métadonnées normalisées utilisées par le moteur pour trouver des contenus similaires. */
    val tags: List<String> = emptyList(),
    val creator: String? = null,
    val releaseYear: String? = null,
    /** Origine du contenu affichée sur la fiche (« Internet Archive », « NASA »…). */
    val source: String? = null,
    /** Licence du contenu (« Domaine public », « CC BY »…) — null si inconnue. */
    val license: String? = null,
) : Serializable {

    val isLive: Boolean get() = kind.isLive
    val isAudioOnly: Boolean get() = kind.isAudioOnly
    val isSeries: Boolean get() = seriesTitle != null

    /** Extrait court de description affiché directement sous le titre de la carte. */
    private fun cardExcerpt(text: String, maxChars: Int = 68): String {
        val normalized = text.replace(CARD_WHITESPACE, " ").trim()
        return if (normalized.length <= maxChars) normalized
        else normalized.take(maxChars - 1).trimEnd() + "…"
    }

    /** Description courte sur les cartes ; la fiche détails garde le texte complet. */
    fun subtitle(): String {
        val excerpt = cardExcerpt(description)
        val text = when {
            isSeries -> listOf("S${season ?: 1} · E${episode ?: 1}", excerpt)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            isLive -> listOf("● DIRECT", excerpt)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            isAudioOnly -> "♪ ${excerpt.ifBlank { kind.label }}"
            excerpt.isNotBlank() -> excerpt
            else -> kind.label
        }
        return cardExcerpt(text, maxChars = 82)
    }
}

data class VideoRow(
    @StringRes val headerRes: Int = 0,
    val videos: List<Video>,
    /** Titre de ligne fourni par le catalogue distant (prioritaire sur headerRes). */
    val headerTitle: String? = null,
    /** Identifiant de ligne du dépôt (tv_direct, films, …) — pour l'ordre canonique. */
    val rowId: String? = null,
)
