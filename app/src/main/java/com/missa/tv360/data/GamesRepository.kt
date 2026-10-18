package com.missa.tv360.data

import com.missa.tv360.BuildConfig
import com.missa.tv360.data.remote.PagesCatalogDto
import com.missa.tv360.data.remote.PagesClient
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Catalogue des jeux telechargeables.
 *
 * Source 1 : data/jeux.json servi par GitHub Pages depuis le depot
 *            (moteur engines/jeux/, meme schema que le catalogue).
 * Source 2 : catalogue local de demonstration (aucun jeu rejoue — le depot
 *            fournit la vraie source de verite une fois connecte).
 */
object GamesRepository {

    private const val GAMES_PATH = "data/jeux.json"

    /** Jeux de demonstration : des experiences web/libres, aucun binaire tiers. */
    private fun localGames(): List<GamesRow> = listOf(
        GamesRow("Jeux de demonstration", listOf(
            Game(
                id = "game-demo-web",
                title = "Exemple · Jeu web (HTML5)",
                description = "Emplacement reserve. L'engine « jeux » du depot publie " +
                    "ici vos jeux telechargeables (APK, assets ou paquets web).",
                downloadUrl = "",
                packageType = "web",
                genre = "Demonstration",
            ),
        )),
    )

    suspend fun loadGames(): List<GamesRow> = withContext(Dispatchers.IO) {
        // Source 1 : le depot (data/jeux.json, schema standard rows/items).
        try {
            val type = object : TypeToken<PagesCatalogDto>() {}.type
            val dto: PagesCatalogDto? =
                PagesClient.fetchJson(BuildConfig.BFF_BASE_URL, GAMES_PATH, type)
            val rows = dto?.rows.orEmpty().mapNotNull { row ->
                val games = row.items.mapNotNull { it.toGame() }
                if (games.isEmpty()) null
                else GamesRow(row.title ?: "Jeux video", games)
            }
            if (rows.isNotEmpty()) return@withContext rows
        } catch (_: Exception) {
            // on passe au repli local
        }
        // Source 2 : repli local.
        localGames()
    }
}
