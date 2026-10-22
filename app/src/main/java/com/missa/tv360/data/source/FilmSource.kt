package com.missa.tv360.data.source

import com.missa.tv360.data.Video
import kotlinx.coroutines.CancellationException

/**
 * Résultat d'une résolution : la fiche enrichie et **tous** les liens connus.
 *
 * La fiche peut porter un titre ou une description affinés par la source ; les
 * liens, eux, ne sont pas encore choisis — c'est [bestLink] qui tranche.
 */
data class ResolvedSource(
    val video: Video,
    val links: List<ExtractorLink>,
) {
    /** Meilleur lien disponible, ou null si la source n'a rien trouvé. */
    val bestLink: ExtractorLink? get() = ExtractorLink.best(links)
}

/**
 * Adaptateur d'une source brute consultée par le moteur Films natif de l'app.
 *
 * Contrat d'agrégateur, en trois temps :
 *  1. **découvrir** — [trending], [search], [related] renvoient des fiches ;
 *  2. **résoudre** — [load] transforme une fiche en liens de lecture ;
 *  3. **lire** — le registre applique les en-têtes et le lecteur joue le flux.
 *
 * Chaque adaptateur reste du code natif de l'application : le manifeste distant
 * ne transporte que des paramètres, jamais d'URL ni de code exécutable.
 */
interface FilmSource {
    /** Identifiant de l'adaptateur natif, déclaré comme `adapter` dans le manifeste. */
    val adapterId: String

    suspend fun trending(config: FilmSourceConfig): List<Video>

    suspend fun search(query: String, config: FilmSourceConfig): List<Video>

    suspend fun related(video: Video, config: FilmSourceConfig): List<Video>

    /**
     * Résout une fiche en liens de lecture (un par qualité disponible).
     * Retourne null si le contenu n'est pas résoluble par cette source.
     */
    suspend fun load(result: Video, config: FilmSourceConfig): ResolvedSource?
}

/**
 * Registre du moteur Films : agrège les sources activées par le paquet GitHub.
 *
 * Les fichiers distants ne sont que des paramètres validés, jamais du code
 * exécutable. Chaque appel est chronométré et compte dans [SourceHealthTracker].
 */
object FilmSourceRegistry {
    private val adapters: List<FilmSource> = listOf(
        InternetArchiveFilmSource,
        DailymotionFilmSource,
    )

    /** Adaptateurs réellement compilés dans l'APK. */
    fun adapterIds(): List<String> = adapters.map { it.adapterId }

    /** État de santé observé pour chaque source depuis le démarrage. */
    fun health(): List<SourceHealth> = SourceHealthTracker.snapshot()

    suspend fun trending(engine: FilmEngineConfig): List<Video> =
        collect(engine) { source, config -> source.trending(config) }

    suspend fun search(query: String, engine: FilmEngineConfig): List<Video> =
        collect(engine) { source, config -> source.search(query, config) }

    suspend fun related(video: Video, engine: FilmEngineConfig): List<Video> =
        collect(engine) { source, config -> source.related(video, config) }

    /**
     * Résolution complète : tous les liens de la source, avec suivi de santé.
     * Une source en quarantaine est ignorée sans appel réseau.
     */
    suspend fun load(result: Video, engine: FilmEngineConfig): ResolvedSource? {
        val config = engine.sources.firstOrNull { it.id == result.sourceId } ?: return null
        val source = adapters.firstOrNull { it.adapterId == config.adapter } ?: return null
        if (SourceHealthTracker.isCoolingDown(config.id)) return null

        val started = System.nanoTime()
        return try {
            val resolved = source.load(result, config)
            if (resolved?.bestLink == null) {
                SourceHealthTracker.recordFailure(config.id, null, elapsedMs(started))
                null
            } else {
                SourceHealthTracker.recordSuccess(config.id, elapsedMs(started))
                resolved
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SourceHealthTracker.recordFailure(config.id, error, elapsedMs(started))
            null
        }
    }

    /**
     * Compatibilité : renvoie directement une [Video] jouable, en repliant le
     * meilleur lien (et ses en-têtes) dans le modèle. Les écrans existants
     * passent par ici ; un futur sélecteur de qualité utilisera [load].
     */
    suspend fun resolve(result: Video, engine: FilmEngineConfig): Video? {
        val resolved = load(result, engine) ?: return null
        val best = resolved.bestLink ?: return null
        return resolved.video.copy(
            streamUrl = best.url,
            format = best.format,
            streamHeaders = best.headers,
        )
    }

    private suspend fun collect(
        engine: FilmEngineConfig,
        load: suspend (FilmSource, FilmSourceConfig) -> List<Video>,
    ): List<Video> {
        val results = mutableListOf<Video>()
        var sourceFailure: Exception? = null
        for (config in engine.sources) {
            val source = adapters.firstOrNull { it.adapterId == config.adapter } ?: continue
            if (SourceHealthTracker.isCoolingDown(config.id)) continue
            val started = System.nanoTime()
            try {
                results += load(source, config)
                SourceHealthTracker.recordSuccess(config.id, elapsedMs(started))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SourceHealthTracker.recordFailure(config.id, error, elapsedMs(started))
                if (sourceFailure == null) sourceFailure = error
            }
        }
        if (results.isEmpty()) sourceFailure?.let { throw it }
        return results.distinctBy { video -> "${video.sourceId}:${video.id}" }
    }

    private fun elapsedMs(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000
}
