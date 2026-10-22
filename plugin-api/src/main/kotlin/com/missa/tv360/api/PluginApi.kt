package com.missa.tv360.api

/**
 * Version du contrat plugin ↔ application.
 *
 * Une extension déclare cette valeur dans son `manifest.json` (`apiVersion`).
 * L'application refuse toute extension dont `apiVersion` diffère : c'est le
 * garde-fou qui évite de charger du code compilé contre une API disparue.
 */
const val PLUGIN_API_VERSION: Int = 1

/** Entrée ZIP contenant les métadonnées du plugin (lue par l'application). */
const val PLUGIN_MANIFEST_ENTRY: String = "manifest.json"

/** Extension de fichier des archives de plugin produites par le dépôt. */
const val PLUGIN_FILE_EXTENSION: String = "msa"

/**
 * Types de contenus qu'une extension peut publier.
 * Les noms correspondent volontairement à `ContentKind` côté application :
 * le mapping se fait par nom et reste stable même si l'app ajoute des types.
 */
enum class Kind {
    LIVE_TV,
    LIVE_SPORT,
    MOVIE,
    SERIES,
    SHORT,
    RADIO,
    PODCAST,
    MUSIC,
    KIDS,
    GAME,
}

/**
 * Flux lisible par le lecteur Media3 de l'application.
 *
 * [format] : `hls`, `mp4` ou `mp3` (sélection du MediaSource côté app).
 * [live] : true pour un flux continu (pas de reprise ni de fin).
 */
data class Stream(
    val url: String,
    val format: String = "mp4",
    val live: Boolean = false,
    val audioOnly: Boolean = false,
)

/**
 * Un contenu publié par une extension.
 *
 * Les champs de provenance ([source], [license]) sont OBLIGATOIRES dans le
 * dépôt officiel : une extension qui ne peut pas les documenter ne doit pas
 * publier l'item (cf. la règle « pas d'étiquette de licence inventée »).
 */
data class Item(
    val id: String,
    val title: String,
    val kind: Kind,
    val stream: Stream,
    val description: String = "",
    val thumbnailUrl: String? = null,
    val seriesTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val durationSec: Int? = null,
    val source: String? = null,
    val license: String? = null,
)

/** Une rangée (catégorie) publiée par une extension. */
data class Row(
    val id: String,
    val title: String,
    val items: List<Item>,
)

/**
 * Point d'entrée d'une extension : une ou plusieurs rangées de contenus.
 *
 * [rows] est `suspend` : elle peut appeler le réseau. L'application lui impose
 * un timeout et isole les exceptions : une extension qui échoue ne doit jamais
 * casser l'écran d'accueil.
 */
interface Provider {
    /** Identifiant stable, unique dans le dépôt (ex. `bipbop`). */
    val id: String

    /** Nom affiché dans l'interface. */
    val name: String

    /** Rangées publiées ; une liste vide signifie « rien à afficher ». */
    suspend fun rows(): List<Row>
}

/**
 * Classe de base que DOIT étendre la classe désignée par `pluginClassName`
 * dans le `manifest.json` de l'extension.
 *
 * Une extension typique :
 * ```
 * class BipBopPlugin : MissaPlugin() {
 *     override fun load() {
 *         registerProvider(BipBopProvider())
 *     }
 * }
 * ```
 */
abstract class MissaPlugin {

    /** Chemin absolu du fichier `.msa` chargé (renseigné par l'application). */
    var filename: String? = null

    /** Appelée une fois après l'instanciation : enregistrer les providers ici. */
    open fun load() {}

    /** Appelée avant le déchargement (fermer des ressources, annuler des jobs). */
    open fun beforeUnload() {}

    /**
     * Enregistre un provider auprès de l'application.
     * Sans cet appel, l'extension est chargée mais ne publie aucun contenu.
     */
    protected fun registerProvider(provider: Provider) {
        PluginRegistry.register(this, provider)
    }
}
