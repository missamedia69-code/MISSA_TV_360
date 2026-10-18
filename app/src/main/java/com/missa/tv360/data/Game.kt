package com.missa.tv360.data

/**
 * Un jeu telechargeable publie par le depot GitHub (engine « jeux »).
 *
 * Contrairement aux 9 autres categories (contenu a streamer), la categorie
 * « Jeux video » fournit des fichiers binaires a telecharger sur l'appareil.
 * Le JSON distant (data/games.json) decrit chaque jeu ; les binaires sont
 * servi par les Releases du depot ou un bucket lie.
 */
data class Game(
    val id: String,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String? = null,
    /** URL directe du fichier telechargeable (APK, ZIP, asset). */
    val downloadUrl: String,
    /** Taille en octets (affichee a l'utilisateur, sert au suivi). */
    val sizeBytes: Long = 0L,
    /** Somme de controle SHA-256 du fichier (verification avant installation). */
    val sha256: String? = null,
    /** Type de paquet : apk | asset | web. Determinera la strategy d'installation. */
    val packageType: String = "apk",
    val versionName: String? = null,
    val versionCode: Int? = null,
    val genre: String? = null,
) {
    /** Taille lisible (Ko / Mo / Go). */
    fun humanSize(): String {
        if (sizeBytes <= 0L) return ""
        val units = listOf("o", "Ko", "Mo", "Go")
        var value = sizeBytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format("%.1f %s", value, units[unit])
    }
}

/** Une ligne de la grille de jeux (groupe affiche dans l'UI). */
data class GamesRow(
    val title: String,
    val games: List<Game>,
)
