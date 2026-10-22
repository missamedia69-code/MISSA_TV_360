package com.missa.tv360.data

import android.content.Context

/**
 * Liste personnelle du spectateur (« Ma liste ») : ensemble d'ids de contenus,
 * stocké localement dans SharedPreferences et persistant après redémarrage.
 */
object MyListStore {
    private const val PREFS = "my_list"
    private const val KEY = "ids"

    private fun ids(ctx: Context): MutableSet<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()).orEmpty().toMutableSet()

    fun contains(ctx: Context, id: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()).orEmpty().contains(id)

    /** Copie défensive : aucune vue ne modifie directement l'ensemble persisté. */
    fun idsSnapshot(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()).orEmpty().toSet()

    /** Ajoute le contenu s'il n'y est pas ; @return true si la liste a changé. */
    fun add(ctx: Context, id: String): Boolean {
        val current = ids(ctx)
        if (!current.add(id)) return false
        write(ctx, current)
        return true
    }

    /** Retire le contenu s'il y est ; @return true si la liste a changé. */
    fun remove(ctx: Context, id: String): Boolean {
        val current = ids(ctx)
        if (!current.remove(id)) return false
        write(ctx, current)
        return true
    }

    fun toggle(ctx: Context, id: String): Boolean =
        if (contains(ctx, id)) { remove(ctx, id); false } else { add(ctx, id); true }

    private fun write(ctx: Context, value: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, value)
            .apply()
    }
}
