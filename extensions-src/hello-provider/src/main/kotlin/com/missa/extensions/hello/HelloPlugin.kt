package com.missa.extensions.hello

import com.missa.tv360.api.Item
import com.missa.tv360.api.Kind
import com.missa.tv360.api.MissaPlugin
import com.missa.tv360.api.Provider
import com.missa.tv360.api.Row
import com.missa.tv360.api.Stream

/**
 * Extension d'exemple.
 *
 * Elle ne fait aucun appel réseau : elle publie trois films libres déjà
 * utilisés par l'application, pour vérifier que la chaîne complète fonctionne
 * (dépôt → téléchargement → vérification SHA-256 → chargement DEX →
 * enregistrement du provider → rangée affichée sur l'accueil).
 *
 * Pour une extension réelle : effectuer l'acquisition dans [HelloProvider.rows]
 * (`suspend`, donc appel réseau autorisé), et **ne jamais publier** un contenu
 * dont la source ou la licence n'est pas documentée.
 */
class HelloPlugin : MissaPlugin() {

    override fun load() {
        // C'est ici — et seulement ici — que les contenus deviennent visibles
        // pour l'application.
        registerProvider(HelloProvider())
    }
}

class HelloProvider : Provider {

    override val id: String = "hello-provider"

    override val name: String = "Démo — films libres"

    override suspend fun rows(): List<Row> = listOf(
        Row(
            id = "blender",
            title = "Films Blender",
            items = listOf(
                Item(
                    id = "bbb",
                    title = "Big Buck Bunny",
                    kind = Kind.MOVIE,
                    stream = Stream(url = "$BUCKET/BigBuckBunny.mp4", format = "mp4"),
                    description = "Court métrage de la Blender Foundation (2008).",
                    source = "Blender Foundation (miroir Google)",
                    license = "CC BY 3.0",
                ),
                Item(
                    id = "sintel",
                    title = "Sintel",
                    kind = Kind.MOVIE,
                    stream = Stream(url = "$BUCKET/Sintel.mp4", format = "mp4"),
                    description = "Court métrage de la Blender Foundation (2010).",
                    source = "Blender Foundation (miroir Google)",
                    license = "CC BY 3.0",
                ),
                Item(
                    id = "tears-of-steel",
                    title = "Tears of Steel",
                    kind = Kind.MOVIE,
                    stream = Stream(url = "$BUCKET/TearsOfSteel.mp4", format = "mp4"),
                    description = "Court métrage de la Blender Foundation (2012).",
                    source = "Blender Foundation (miroir Google)",
                    license = "CC BY 3.0",
                ),
            ),
        )
    )

    private companion object {
        const val BUCKET = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample"
    }
}
