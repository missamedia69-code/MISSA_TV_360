package com.missa.tv360.ui

import androidx.annotation.DrawableRes
import com.missa.tv360.R
import java.util.Locale

/**
 * Choisit le fond MISSA TV 360 correspondant au type du contenu.
 *
 * ⚠️ Ces fonds font 1920×1080 : ils servent de PLEIN ÉCRAN (fond de l'écran
 * d'accueil, de la fiche détails). Pour une TUILE, utiliser
 * [afficheParDefaut] : les affiches 320×180 évitent l'étirement d'une image
 * panoramique dans un format 16:9 ou 2:3.
 */
@DrawableRes
fun fondParDefaut(type: String?): Int {
    val t = type.orEmpty().lowercase(Locale.ROOT).trim()
        .replace("é", "e")
        .replace("è", "e")
        .replace("ê", "e")
    return when {
        t.contains("sport") -> R.drawable.fond_sport_direct
        t.contains("court") || t.contains("short") -> R.drawable.fond_films_courts
        t.contains("film") || t.contains("movie") -> R.drawable.fond_films
        t.contains("serie") -> R.drawable.fond_series
        t.contains("musique") || t.contains("music") -> R.drawable.fond_musique
        t.contains("radio") -> R.drawable.fond_radio
        t.contains("podcast") -> R.drawable.fond_podcasts
        t.contains("jeunesse") || t.contains("enfant") || t.contains("kids") ->
            R.drawable.fond_jeunesse
        t.contains("jeu") || t.contains("game") || t.contains("gaming") ->
            R.drawable.fond_jeux_video
        else -> R.drawable.fond_tv_direct
    }
}

/**
 * Affiche de repli pour une TUILE, au format 320×180 (identique aux
 * illustrations intégrées), afin que la tuile ne soit jamais vide ni étirée.
 *
 * L'ordre de préférence reprend celui de [com.missa.tv360.data.ContentKind] :
 * films et séries partagent le visuel « To Be Determined » du domaine public.
 */
@DrawableRes
fun afficheParDefaut(kind: com.missa.tv360.data.ContentKind): Int = when (kind) {
    com.missa.tv360.data.ContentKind.LIVE_TV,
    com.missa.tv360.data.ContentKind.LIVE_SPORT -> R.drawable.card_nasa
    com.missa.tv360.data.ContentKind.MOVIE -> R.drawable.card_tbd
    com.missa.tv360.data.ContentKind.SERIES -> R.drawable.card_series
    com.missa.tv360.data.ContentKind.SHORT -> R.drawable.card_bunny
    com.missa.tv360.data.ContentKind.RADIO -> R.drawable.card_radio
    com.missa.tv360.data.ContentKind.PODCAST -> R.drawable.card_podcast
    com.missa.tv360.data.ContentKind.MUSIC -> R.drawable.card_music
    com.missa.tv360.data.ContentKind.KIDS -> R.drawable.card_bunny
    com.missa.tv360.data.ContentKind.GAME -> R.drawable.card_game
}

