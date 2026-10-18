package com.missa.tv360.ui

import androidx.annotation.DrawableRes
import com.missa.tv360.R
import java.util.Locale

/** Choisit le fond MISSA TV 360 correspondant au type du contenu. */
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
