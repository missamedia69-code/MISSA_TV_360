package com.missa.tv360.data

import android.content.Context
import com.missa.tv360.data.json.JsonSourceRepository

/** Jeux issus des mêmes JSON agrégés que le catalogue principal. */
object GamesRepository {
    suspend fun loadGames(context: Context): List<GamesRow> =
        JsonSourceRepository.load(context.applicationContext).catalog.games
}
