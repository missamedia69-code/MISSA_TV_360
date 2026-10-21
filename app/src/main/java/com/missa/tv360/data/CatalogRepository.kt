package com.missa.tv360.data

import com.google.gson.reflect.TypeToken
import com.missa.tv360.BuildConfig
import com.missa.tv360.R
import com.missa.tv360.data.remote.BffApi
import com.missa.tv360.data.remote.ItemDto
import com.missa.tv360.data.remote.PagesCatalogDto
import com.missa.tv360.data.remote.PagesClient
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
 * Source 3 : catalogue local de démonstration — uniquement des contenus
 *            libres de droits (domaine public, Creative Commons, webradios
 *            publiques) + emplacements réservés à connecter.
 */
object CatalogRepository {

    /** Ordre canonique des 10 lignes (identifiants de row du dépôt). */
    private val CANONICAL_ORDER = listOf(
        "tv_direct", "sport", "films", "series", "films_courts",
        "radio", "podcasts", "musique", "jeunesse", "jeux",
    )

    private const val CATALOG_PATH = "data/catalog.json"

    // Contenus libres de droits vérifiés
    private const val NASA_TV = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"
    private const val BIPBOP =
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    private const val GTV = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/"
    private const val SOMAFM = "https://ice1.somafm.com/"

    private val api: BffApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BFF_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BffApi::class.java)
    }

    suspend fun loadCatalog(): List<VideoRow> = withContext(Dispatchers.IO) {
        // Source 1 : le dépôt GitHub (GitHub Pages) — le « serveur » de l'app.
        try {
            val type = object : TypeToken<PagesCatalogDto>() {}.type
            val dto: PagesCatalogDto? =
                PagesClient.fetchJson(BuildConfig.BFF_BASE_URL, CATALOG_PATH, type, 4000)
            val rows = dto?.toRows().orEmpty()
            if (rows.isNotEmpty()) return@withContext rows
        } catch (_: Exception) {
            // on passe à la source suivante
        }
        // Source 2 : BFF Retrofit (alternative, même contrat).
        try {
            val rows = api.rows()
            if (rows.isNotEmpty()) {
                return@withContext rows.map { row ->
                    VideoRow(
                        headerRes = R.string.app_name,
                        videos = row.items.mapNotNull { it.toVideo() },
                    )
                }
            }
        } catch (_: Exception) {
            // on passe à la source suivante
        }
        // Source 3 : catalogue local embarqué.
        localCatalog()
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
                row.items.mapNotNull { it.toVideo() }
            }
            if (videos.isEmpty()) return@mapNotNull null
            VideoRow(videos = videos, headerTitle = title, rowId = rowId)
        }.sortedBy { row ->
            val idx = CANONICAL_ORDER.indexOf(row.rowId)
            if (idx >= 0) idx else CANONICAL_ORDER.size
        }

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

        // ── 2. SPORT DIRECT ──────────────────────────────────────────
        VideoRow(R.string.row_sport, listOf(
            Video("sport-1", "Subaru — Sur route et terre",
                "Essai tout-terrain : démonstration de conduite sportive.",
                R.drawable.card_sport, GTV + "SubaruOutbackOnStreetAndDirt.mp4",
                kind = ContentKind.LIVE_SPORT),
            Video("sport-2", "Volkswagen GTI — Essai",
                "Essai routier d'une berline sportive.",
                R.drawable.card_sport, GTV + "VolkswagenGTIReview.mp4",
                kind = ContentKind.LIVE_SPORT),
            Video("sport-3", "Grand Prix — Tour de piste",
                "Démonstration de pilotage sur circuit.",
                R.drawable.card_sport, GTV + "WeAreGoingOnBullrun.mp4",
                kind = ContentKind.LIVE_SPORT),
            Video("sport-4", "Moteurs — Plein régime",
                "Séquence mécanique et sport automobile.",
                R.drawable.card_sport, GTV + "WhatCarCanYouGetForAGrand.mp4",
                kind = ContentKind.LIVE_SPORT),
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
        VideoRow(R.string.row_music, listOf(
            Video("music-clip-1", "Clip — Plein cadre",
                "Clip musical de demonstration.",
                R.drawable.card_music, GTV + "ForBiggerBlazes.mp4",
                kind = ContentKind.MUSIC),
            Video("music-clip-2", "Clip — Nuit electrique",
                "Clip musical de demonstration.",
                R.drawable.card_music, GTV + "ForBiggerFun.mp4",
                kind = ContentKind.MUSIC),
            Video("music-clip-3", "Clip — Route infinie",
                "Clip musical de demonstration.",
                R.drawable.card_music, GTV + "ForBiggerJoyrides.mp4",
                kind = ContentKind.MUSIC),
            Video("music-poptron", "PopTron — Webradio",
                "Webradio indie pop / electro — SomaFM (ecoute libre).",
                R.drawable.card_music, SOMAFM + "poptron-128-mp3",
                kind = ContentKind.MUSIC, format = "mp3"),
            Video("music-bootliquor", "Boot Liquor — Webradio",
                "Webradio americana / country — SomaFM (ecoute libre).",
                R.drawable.card_music, SOMAFM + "bootliquor-128-mp3",
                kind = ContentKind.MUSIC, format = "mp3"),
        )),

        // ── 7. RADIO (webradios publiques SomaFM — flux MP3 libres) ──
        VideoRow(R.string.row_radio, listOf(
            Video("radio-groove", "Groove Salad",
                "Webradio ambient / downtempo — SomaFM (ecoute libre, flou donation).",
                R.drawable.card_radio, SOMAFM + "groovesalad-128-mp3",
                kind = ContentKind.RADIO, format = "mp3"),
            Video("radio-lush", "Lush",
                "Webradio voix feminines / electronica — SomaFM.",
                R.drawable.card_radio, SOMAFM + "lush-128-mp3",
                kind = ContentKind.RADIO, format = "mp3"),
            Video("radio-drone", "Drone Zone",
                "Webradio ambient spatial — SomaFM.",
                R.drawable.card_radio, SOMAFM + "dronezone-128-mp3",
                kind = ContentKind.RADIO, format = "mp3"),
            Video("radio-poptron", "PopTron",
                "Webradio indie pop / electro — SomaFM.",
                R.drawable.card_radio, SOMAFM + "poptron-128-mp3",
                kind = ContentKind.RADIO, format = "mp3"),
            Video("radio-secretagent", "Secret Agent",
                "Webradio cinematographique / espionnage — SomaFM.",
                R.drawable.card_radio, SOMAFM + "secretagent-128-mp3",
                kind = ContentKind.RADIO, format = "mp3"),
            Video("radio-indiepop", "Indie Pop Rocks",
                "Webradio indie pop — SomaFM.",
                R.drawable.card_radio, SOMAFM + "indiepop-128-mp3",
                kind = ContentKind.RADIO, format = "mp3"),
            Video("radio-metal", "Metal Detector",
                "Webradio metal / heavy — SomaFM.",
                R.drawable.card_radio, SOMAFM + "metal-128-mp3",
                kind = ContentKind.RADIO, format = "mp3"),
        )),

        // ── 8. PODCASTS ──────────────────────────────────────────────
        VideoRow(R.string.row_podcast, listOf(
            Video("pod-spacestation", "Space Station — Ambient",
                "Longue session ambient, ideale en podcast d'ambiance — SomaFM.",
                R.drawable.card_podcast, SOMAFM + "spacestation-128-mp3",
                kind = ContentKind.PODCAST, format = "mp3"),
            Video("pod-defcon", "DEF CON Radio",
                "Selection electronique — SomaFM.",
                R.drawable.card_podcast, SOMAFM + "defcon-128-mp3",
                kind = ContentKind.PODCAST, format = "mp3"),
            Video("pod-fluid", "Fluid — Instrumental Hip-Hop",
                "Instrumental hip-hop — SomaFM.",
                R.drawable.card_podcast, SOMAFM + "fluid-128-mp3",
                kind = ContentKind.PODCAST, format = "mp3"),
            Video("pod-groove", "Groove Salad Classic",
                "Downtempo classique — SomaFM.",
                R.drawable.card_podcast, SOMAFM + "groovesalad-128-mp3",
                kind = ContentKind.PODCAST, format = "mp3"),
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
