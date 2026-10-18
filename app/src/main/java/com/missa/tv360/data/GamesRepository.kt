package com.missa.tv360.data

import com.missa.tv360.BuildConfig
import com.missa.tv360.data.remote.GamesCatalogDto
import com.missa.tv360.data.remote.PagesClient
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Catalogue des jeux telechargeables.
 *
 * Source 1 : data/games.json servi par GitHub Pages depuis le depot.
 * Source 2 : catalogue local de demonstration (aucun jeu rejoue — le depot
 *            fournit la vraie source de verite une fois connecte).
 */
object GamesRepository {

    private const val GAMES_PATH = "data/games.json"

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
        val type = object : TypeToken<GamesCatalogDto>() {}.type
        val dto: GamesCatalogDto? = PagesClient.fetchJson(BuildConfig.BFF_BASE_URL, GAMES_PATH, type)
        val rows = dto?.rows
        if (rows.isNullOrEmpty()) {
            localGames()
        } else {
            rows.map { row ->
                GamesRow(
                    title = row.title,
                    games = row.games.map { it.toGame() },
                )
            }.filter { it.games.isNotEmpty() }
        }
    }
}
