package com.missa.tv360.data

import android.content.Context

/**
 * Signalements utilisateurs (« Signaler un problème »).
 *
 * Stockage local aujourd'hui, envoi différé demain : chaque entrée conserve
 * l'id du contenu, le motif choisi et l'horodatage.
 */
object ReportStore {
    private const val PREFS = "reports"
    private const val KEY = "entries"

    fun save(ctx: Context, videoId: String, reason: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY, emptySet())!!.toMutableSet()
        current.add("$videoId|${System.currentTimeMillis()}|$reason")
        prefs.edit().putStringSet(KEY, current).apply()
    }

    /** Nombre de signalements en attente d'envoi (utile pour la synchronisation). */
    fun pendingCount(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())!!.size
}
