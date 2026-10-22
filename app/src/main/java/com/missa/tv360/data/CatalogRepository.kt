package com.missa.tv360.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.missa.tv360.BuildConfig
import com.missa.tv360.R
import com.missa.tv360.data.remote.BffApi
import com.missa.tv360.data.remote.ItemDto
import com.missa.tv360.data.remote.PagesCatalogDto
import com.missa.tv360.data.remote.PagesClient
import com.missa.tv360.ui.afficheParDefaut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Catalogue de l'application — 10 catégories :
 * TV Direct, Sport Direct, Films, Séries, Films courts, Musique,
 * Radio, Podcasts, Jeunesse, Jeux vidéo.
 *
 * Source 1 : catalog.json servi par GitHub Pages depuis le dépôt
 *            (produit par les moteurs engines/<cat>/engine.py).
 *            C'est le « serveur » de l'app.
 * Source 2 : BFF distant Retrofit (BuildConfig.BFF_BASE_URL) — alternative.
 *            (À CONFIRMER : aucun serveur BFF n'est actuellement déployé ;
 *             ce chemin est conservé pour compatibilité future.)
 * Source 3 : catalogue local de démonstration — uniquement des contenus
 *            libres de droits (domaine public, Creative Commons) +
 *            emplacements réservés à connecter.
 */
enum class CatalogSource { REMOTE, CACHE, BUNDLED, DEMO }

data class CatalogLoadResult(
    val rows: List<VideoRow>,
    val source: CatalogSource,
    val remoteFailed: Boolean = false,
    val networkFailed: Boolean = false,
)

object CatalogRepository {

    /** Ordre canonique des 10 lignes (identifiants de row du dépôt). */
    private val CANONICAL_ORDER = listOf(
        "tv_direct", "sport", "films", "series", "films_courts",
        "radio", "podcasts", "musique", "jeunesse", "jeux",
    )

    private const val CATALOG_PATH = "data/catalog.json"

    /** Catalogue embarqué dans l'APK (source de repli fiable, hors réseau). */
    private const val ASSET_CATALOG_PATH = "catalog.json"

    /** Nom des préférences où le dernier catalogue distant valide est conservé. */
    private const val PREFS_NAME = "catalog_cache"
    private const val PREF_CATALOG = "last_valid_catalog"

    // Contenus libres de droits vérifiés (domaine public / CC BY / webradios publiques)
    private const val NASA_TV = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"
    private const val BIPBOP =
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    private const val GTV = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/"

    private val api: BffApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BFF_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BffApi::class.java)
    }

    /** Premier affichage : cache réel, asset embarqué, puis démonstration en dernier recours. */
    suspend fun initialCatalog(context: Context): CatalogLoadResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        cachedCatalogRows(appContext)?.let {
            return@withContext CatalogLoadResult(it, CatalogSource.CACHE)
        }
        bundledCatalogRows(appContext)?.let {
            return@withContext CatalogLoadResult(it, CatalogSource.BUNDLED)
        }
        CatalogLoadResult(localCatalog(), CatalogSource.DEMO)
    }

    /** Compatibilité avec les appels existants qui n'ont besoin que des rangées. */
    suspend fun loadCatalog(context: Context): List<VideoRow> = loadCatalogWithStatus(context).rows

    /** Charge la source distante sans masquer l'origine du repli affiché à l'utilisateur. */
    suspend fun loadCatalogWithStatus(context: Context): CatalogLoadResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val type = object : TypeToken<PagesCatalogDto>() {}.type
        val result: PagesClient.FetchResult<PagesCatalogDto> =
            PagesClient.fetchJson(BuildConfig.BFF_BASE_URL, CATALOG_PATH, type, 4000)
        val dto: PagesCatalogDto? = when (result) {
            is PagesClient.FetchResult.Success<*> -> result.data as? PagesCatalogDto
            else -> null
        }
        val rows = dto?.toRows().orEmpty()
        if (rows.isNotEmpty()) {
            if (dto != null) cacheCatalog(appContext, dto)
            return@withContext CatalogLoadResult(rows, CatalogSource.REMOTE)
        }

        var networkFailed = result is PagesClient.FetchResult.NetworkError
        try {
            val bffRows = api.rows()
            if (bffRows.isNotEmpty()) {
                return@withContext CatalogLoadResult(
                    bffRows.map { row ->
                        VideoRow(
                            rowId = row.id,
                            headerTitle = row.title,
                            videos = row.items.mapNotNull { it.toVideo() },
                        )
                    },
                    CatalogSource.REMOTE,
                )
            }
        } catch (_: Exception) {
            networkFailed = true
        }

        val fallback = initialCatalog(appContext)
        fallback.copy(remoteFailed = true, networkFailed = networkFailed)
    }

    /**
     * Catalogue livré dans les assets de l'APK.
     *
     * Il est produit par `python engines/build_all.py` puis copié dans
     * `app/src/main/assets/catalog.json`. Contrairement au catalogue local de
     * démonstration, il contient les vignettes et les descriptions réelles :
     * les tuiles ne sont donc jamais vides, même sans réseau.
     */
    fun bundledCatalogRows(context: Context): List<VideoRow>? {
        val appContext = context.applicationContext
        return try {
            val json = appContext.assets.open(ASSET_CATALOG_PATH)
                .bufferedReader().use { it.readText() }
            parseRows(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Catalogue mis en cache lors du dernier chargement réseau réussi.
     * Évite de retomber sur le catalogue de démonstration après une coupure.
     */
    private fun cachedCatalogRows(context: Context): List<VideoRow>? {
        val appContext = context.applicationContext
        val json = try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_CATALOG, null)
        } catch (_: Exception) {
            null
        } ?: return null
        return parseRows(json)
    }

    /** Conserve le catalogue distant valide pour les prochaines openings. */
    private fun cacheCatalog(context: Context, dto: PagesCatalogDto) {
        try {
            val appContext = context.applicationContext
            val json = Gson().toJson(dto)
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_CATALOG, json).apply()
        } catch (_: Exception) {
            // Le cache est un confort : son échec n'est pas bloquant.
        }
    }

    /** Convertit un catalogue JSON (texte) en lignes de contenus. */
    private fun parseRows(json: String): List<VideoRow>? = try {
        val dto = Gson().fromJson(json, PagesCatalogDto::class.java)
        dto.toRows().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    /** Mappe le DTO Pages en lignes ordonnées selon l'ordre canonique. */
    private fun PagesCatalogDto.toRows(): List<VideoRow> =
        rows.mapNotNull { row ->
            val rowId = row.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = row.title?.takeIf { it.isNotBlank() } ?: rowId
            val kindGames = rowId == "games" || rowId == "jeux"
            val videos = if (kindGames) {
                // Ligne « Jeux vidéo » : une seule carte lanceur vers l'écran
                // des jeux téléchargeables (data/games.json du dépôt).
                listOf(
                    Video("game-open", title,
                        "Parcourir et installer les jeux telechargeables publies par le depot.",
                        R.drawable.card_game, "", kind = ContentKind.GAME,
                        isGamesLauncher = true),
                )
            } else {
                row.items.mapNotNull { item ->
                    item.toVideo()?.let { video ->
                        // Fallback : si le catalogue distant ne fournit ni drawable local
                        // ni URL de vignette, on affiche une illustration par défaut
                        // pour éviter des tuiles vides.
                        if (video.cardRes == 0 && video.cardUrl.isNullOrBlank()) {
                            video.copy(cardRes = defaultCardResFor(video.kind))
                        } else {
                            video
                        }
                    }
                }
            }
            if (videos.isEmpty()) return@mapNotNull null
            VideoRow(videos = videos, headerTitle = title, rowId = rowId)
        }.sortedBy { row ->
            val idx = CANONICAL_ORDER.indexOf(row.rowId)
            if (idx >= 0) idx else CANONICAL_ORDER.size
        }

    /** Illustration de secours par kind quand le catalogue distant n'en fournit aucune. */
    private fun defaultCardResFor(kind: ContentKind): Int = afficheParDefaut(kind)

    /** Catalogue local de démonstration (10 lignes). */
    fun localCatalog(): List<VideoRow> = listOf(

        // ── 1. TV DIRECT ─────────────────────────────────────────────
        VideoRow(R.string.row_live_tv, listOf(
            Video("live-nasa", "NASA TV Public",
                "Chaîne publique de la NASA en flux HLS continu (usage autorisé).",
                R.drawable.card_nasa, NASA_TV, kind = ContentKind.LIVE_TV, format = "hls"),
            Video("live-bipbop", "BipBop Advanced",
                "Flux HLS de test d'Apple (fMP4 multi-débit) — valide la lecture adaptative.",
                R.drawable.card_apple, BIPBOP, kind = ContentKind.LIVE_TV, format = "hls"),
            Video("live-nasa-iss", "ISS — Vue de la Terre",
                "Camera de la Station spatiale internationale en continu.",
                R.drawable.card_nasa, NASA_TV, kind = ContentKind.LIVE_TV, format = "hls"),
            Video("live-mux-demo", "Chaine de demonstration HLS",
                "Flux HLS continu de reference.",
                R.drawable.card_apple,
                "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                kind = ContentKind.LIVE_TV, format = "hls"),
        )),

        // ── 2. SPORT ─────────────────────────────────────────────────
        // Extraits de démonstration (non directs) — kind MOVIE, forceLive=false.
        VideoRow(R.string.row_sport, listOf(
            Video("sport-1", "Subaru — Sur route et terre",
                "Essai tout-terrain : démonstration de conduite sportive.",
                R.drawable.card_sport, GTV + "SubaruOutbackOnStreetAndDirt.mp4",
                kind = ContentKind.MOVIE),
            Video("sport-2", "Volkswagen GTI — Essai",
                "Essai routier d'une berline sportive.",
                R.drawable.card_sport, GTV + "VolkswagenGTIReview.mp4",
                kind = ContentKind.MOVIE),
            Video("sport-3", "Grand Prix — Tour de piste",
                "Démonstration de pilotage sur circuit.",
                R.drawable.card_sport, GTV + "WeAreGoingOnBullrun.mp4",
                kind = ContentKind.MOVIE),
            Video("sport-4", "Moteurs — Plein régime",
                "Séquence mécanique et sport automobile.",
                R.drawable.card_sport, GTV + "WhatCarCanYouGetForAGrand.mp4",
                kind = ContentKind.MOVIE),
        )),

        // ── 3. FILMS (domaine public, archive.org) ───────────────────
        VideoRow(R.string.row_films, listOf(
            Video("film-phantom", "Phantom of the Opera",
                "Film muet fantastique de Rupert Julian (1929), domaine public.",
                R.drawable.card_phantom,
                "https://archive.org/download/ThePhantomOfTheOpera1929/" +
                    "thepaantomoftheopera1929_512kb.mp4",
                kind = ContentKind.MOVIE),
            Video("film-scarlet", "Scarlet Street",
                "Film noir de Fritz Lang (1945), restauré, domaine public. Note IMDB 7.8.",
                R.drawable.card_scarlet,
                "https://archive.org/download/scarlet-street-1945-restored-movie-720p-hd/" +
                    "scarlet%20street-1945-restored%20movie-720p-hd.mp4",
                kind = ContentKind.MOVIE),
            Video("film-gaslight", "Gaslight",
                "Mystère psychologique de George Cukor (1940), restauré et colorisé.",
                R.drawable.card_gaslight,
                "https://archive.org/download/gaslight-1940-colorized-movie-576p-sd/" +
                    "gaslight-1940-colorized%20movie-576p-sd.mp4",
                kind = ContentKind.MOVIE),
            Video("film-mrx", "The Amazing Mr. X",
                "Film noir mystérieux de Harold Schuster (1948), restauré.",
                R.drawable.card_mrx,
                "https://archive.org/download/the-amazing-mr.-x-1948-restored-movie-720p-hd/" +
                    "The%20Amazing%20Mr.%20X-1948-restored%20movie-720p-hd.mp4",
                kind = ContentKind.MOVIE),
            Video("film-sanfran", "Old San Francisco",
                "Drame mélodramatique de Alan Crosland (1927), domaine public.",
                R.drawable.card_sanfran,
                "https://archive.org/download/old-san-francisco-1927-dvd-mkv/" +
                    "Old%20San%20Francisco%20(1927)%20%7Btmdb-104433%7D%20%5BDVD%5D" +
                    "%5BAC3%202.0%5D%5BMPEG2%5D.mp4",
                kind = ContentKind.MOVIE),
            Video("film-hometown", "Home Town Story",
                "Drame familial americain de Arthur Lubin (1951), domaine public.",
                R.drawable.card_gaslight,
                "https://archive.org/download/home-town-story-1951-classic-family-drama/" +
                    "Home%20Town%20Story%20(1951)%20%2B%2B%2B%2B%2B%20Classic%20Family%20Drama).mp4",
                kind = ContentKind.MOVIE),
            Video("film-nosferatu", "Nosferatu (1922)",
                "Chef-d'oeuvre expressionniste de F. W. Murnau (1922), domaine public.",
                R.drawable.card_phantom,
                "https://archive.org/download/nosferatu/nosferatu_512kb.mp4",
                kind = ContentKind.MOVIE),
            Video("film-nold", "Night of the Living Dead (1968)",
                "Film fondateur de George A. Romero (1968), domaine public.",
                R.drawable.card_mrx,
                "https://archive.org/download/night_of_the_living_dead/" +
                    "night_of_the_living_dead_512kb.mp4",
                kind = ContentKind.MOVIE),
            Video("film-detour", "Detour (1945)",
                "Road movie noir d'Edgar G. Ulmer (1945), domaine public.",
                R.drawable.card_scarlet,
                "https://archive.org/download/Detour_1945/Detour_1945_512kb.mp4",
                kind = ContentKind.MOVIE),
            Video("film-general", "The General (1926)",
                "Comedie burlesque de Buster Keaton (1926), domaine public.",
                R.drawable.card_sanfran,
                "https://archive.org/download/TheGeneral/The_General_512kb.mp4",
                kind = ContentKind.MOVIE),
            Video("film-hisgirlfriday", "His Girl Friday (1940)",
                "Comedie de Howard Hawks (1940), domaine public.",
                R.drawable.card_gaslight,
                "https://archive.org/download/his_girl_friday/his_girl_friday_512kb.mp4",
                kind = ContentKind.MOVIE),
        )),

        // ── 4. SÉRIES (démonstration : 2 séries, saison 1) ───────────
        VideoRow(R.string.row_series, listOf(
            Video("ser-a-e1", "Les Grandes Escapades — É1",
                "Épisode 1 — les grandes vacances prennent un tour inattendu.",
                R.drawable.card_series, GTV + "ForBiggerBlazes.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Les Grandes Escapades",
                season = 1, episode = 1),
            Video("ser-a-e2", "Les Grandes Escapades — É2",
                "Épisode 2 — direction la montagne.",
                R.drawable.card_series, GTV + "ForBiggerEscapes.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Les Grandes Escapades",
                season = 1, episode = 2),
            Video("ser-a-e3", "Les Grandes Escapades — É3",
                "Épisode 3 — le grand jeu.",
                R.drawable.card_series, GTV + "ForBiggerFun.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Les Grandes Escapades",
                season = 1, episode = 3),
            Video("ser-a-e4", "Les Grandes Escapades — É4",
                "Épisode 4 — l'orage.",
                R.drawable.card_series, GTV + "ForBiggerJoyrides.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Les Grandes Escapades",
                season = 1, episode = 4),
            Video("ser-a-e5", "Les Grandes Escapades — É5",
                "Épisode 5 — le retour.",
                R.drawable.card_series, GTV + "ForBiggerMeltdowns.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Les Grandes Escapades",
                season = 1, episode = 5),
            Video("ser-b-e1", "Mission Boréale — É1",
                "Épisode 1 — au-delà du cercle polaire.",
                R.drawable.card_series, GTV + "ElephantsDream.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Mission Boréale",
                season = 1, episode = 1),
            Video("ser-b-e2", "Mission Boréale — É2",
                "Épisode 2 — la tempête.",
                R.drawable.card_series, GTV + "Sintel.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Mission Boréale",
                season = 1, episode = 2),
            Video("ser-b-e3", "Mission Boréale — É3",
                "Épisode 3 — l'aurore.",
                R.drawable.card_series, GTV + "TearsOfSteel.mp4",
                kind = ContentKind.SERIES, seriesTitle = "Mission Boréale",
                season = 1, episode = 3),
        )),

        // ── 5. FILMS COURTS (Blender Foundation, Creative Commons) ───
        VideoRow(R.string.row_shorts, listOf(
            Video("short-bbb", "Big Buck Bunny",
                "Court-metrage d'animation de la Blender Foundation (2008), CC BY.",
                R.drawable.card_bunny, GTV + "BigBuckBunny.mp4", kind = ContentKind.SHORT),
            Video("short-ed", "Elephants Dream",
                "Premier film entierement libre de la Blender Foundation (2006), CC BY.",
                R.drawable.card_dream, GTV + "ElephantsDream.mp4", kind = ContentKind.SHORT),
            Video("short-sintel", "Sintel",
                "Court-metrage de la Blender Institute (2010), CC BY.",
                R.drawable.card_sintel, GTV + "Sintel.mp4", kind = ContentKind.SHORT),
            Video("short-tos", "Tears of Steel",
                "Court-metrage sci-fi de la Blender Foundation (2012), CC BY.",
                R.drawable.card_steel, GTV + "TearsOfSteel.mp4", kind = ContentKind.SHORT),
            Video("short-blazes", "For Bigger Blazes",
                "Clip court de demonstration (Google), CC BY.",
                R.drawable.card_fun, GTV + "ForBiggerBlazes.mp4", kind = ContentKind.SHORT),
            Video("short-escapes", "For Bigger Escapes",
                "Clip court de demonstration (Google), CC BY.",
                R.drawable.card_fun, GTV + "ForBiggerEscapes.mp4", kind = ContentKind.SHORT),
            Video("short-joyrides", "For Bigger Joyrides",
                "Clip court de demonstration (Google), CC BY.",
                R.drawable.card_fun, GTV + "ForBiggerJoyrides.mp4", kind = ContentKind.SHORT),
            Video("short-meltdowns", "For Bigger Meltdowns",
                "Clip court de demonstration (Google), CC BY.",
                R.drawable.card_fun, GTV + "ForBiggerMeltdowns.mp4", kind = ContentKind.SHORT),
        )),

        // ── 6. MUSIQUE ───────────────────────────────────────────────
        // Données de démonstration uniquement — à remplacer par des sources
        // audio dont les conditions autorisent l'intégration en application tierce.
        VideoRow(R.string.row_music, listOf(
            Video("music-clip-1", "Clip — Plein cadre",
                "Clip musical de demonstration (Google, usage de test).",
                R.drawable.card_music, GTV + "ForBiggerBlazes.mp4",
                kind = ContentKind.MUSIC),
            Video("music-clip-2", "Clip — Nuit electrique",
                "Clip musical de demonstration (Google, usage de test).",
                R.drawable.card_music, GTV + "ForBiggerFun.mp4",
                kind = ContentKind.MUSIC),
            Video("music-clip-3", "Clip — Route infinie",
                "Clip musical de demonstration (Google, usage de test).",
                R.drawable.card_music, GTV + "ForBiggerJoyrides.mp4",
                kind = ContentKind.MUSIC),
        )),

        // ── 7. RADIO ─────────────────────────────────────────────────
        // ATTENTION conformité : les flux SomaFM nécessitent une autorisation
        // écrite préalable avant intégration en application tierce.
        // Ligne conservée vide en attendant une source audio autorisée.
        VideoRow(R.string.row_radio, emptyList()),

        // ── 8. PODCASTS ──────────────────────────────────────────────
        // Données de démonstration uniquement — à remplacer par des podcasts
        // dont les conditions autorisent l'intégration en application tierce.
        VideoRow(R.string.row_podcast, listOf(
            Video("pod-demo-1", "Podcast · Emplacement 1",
                "Emplacement réservé pour un futur podcast autorisé.",
                R.drawable.card_podcast, "",
                kind = ContentKind.PODCAST),
        )),

        // ── 9. JEUNESSE (Blender CC, tous publics) ───────────────────
        VideoRow(R.string.row_kids, listOf(
            Video("kids-bbb", "Big Buck Bunny",
                "Le lapin geant et ses petits amis — animation douce, Blender CC BY.",
                R.drawable.card_bunny, GTV + "BigBuckBunny.mp4", kind = ContentKind.KIDS),
            Video("kids-ed", "Elephants Dream",
                "Aventure onirique en machine — Blender CC BY.",
                R.drawable.card_dream, GTV + "ElephantsDream.mp4", kind = ContentKind.KIDS),
            Video("kids-sintel", "Sintel",
                "Conte anime de la Blender Institute — CC BY.",
                R.drawable.card_sintel, GTV + "Sintel.mp4", kind = ContentKind.KIDS),
            Video("kids-fun", "For Bigger Fun",
                "Clip court ludique — demonstration Google.",
                R.drawable.card_fun, GTV + "ForBiggerFun.mp4", kind = ContentKind.KIDS),
            Video("kids-tos", "Tears of Steel",
                "Court-metrage sci-fi (ados et plus) — Blender CC BY.",
                R.drawable.card_steel, GTV + "TearsOfSteel.mp4", kind = ContentKind.KIDS),
        )),

        // ── 10. JEUX VIDEO (telechargeables via l'engine « jeux » du depot) ──
        VideoRow(R.string.row_games, listOf(
            Video("game-open", "Jeux video",
                "Parcourir et installer les jeux telechargeables publies par le depot.",
                R.drawable.card_game, "", kind = ContentKind.GAME,
                isGamesLauncher = true),
        )),
    )
}
