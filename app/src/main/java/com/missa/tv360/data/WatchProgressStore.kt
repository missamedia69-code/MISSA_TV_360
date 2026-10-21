package com.missa.tv360.data

import android.content.Context
import android.content.SharedPreferences

object WatchProgressStore {
    private const val PREFS = "watch_progress"

    /**
     * Instance mémorisée : `getSharedPreferences` était appelé à chaque liaison
     * de carte (et plusieurs fois par liaison), ce qui coûtait cher pendant le
     * défilement.
     */
    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences =
        prefs ?: synchronized(this) {
            prefs ?: ctx.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .also { prefs = it }
        }

    fun save(ctx: Context, id: String, pos: Long, dur: Long) {
        prefs(ctx).edit()
            .putLong("${id}_pos", pos)
            .putLong("${id}_dur", dur)
            .putLong("${id}_updated", System.currentTimeMillis())
            .apply()
    }

    fun fraction(ctx: Context, id: String): Float? {
        val p = prefs(ctx)
        val pos = p.getLong("${id}_pos", -1L)
        val dur = p.getLong("${id}_dur", -1L)
        if (pos < 0 || dur <= 0) return null
        val f = (pos.toFloat() / dur).coerceIn(0f, 1f)
        if (f <= 0.03f || f >= 0.95f) return null
        return f
    }

    /** Position de reprise du lecteur (null si progression absente ou hors zone utile). */
    fun position(ctx: Context, id: String): Long? {
        if (fraction(ctx, id) == null) return null
        return prefs(ctx).getLong("${id}_pos", -1L).takeIf { it > 0 }
    }

    fun recent(ctx: Context, limit: Int = 10): List<String> {
        val p = prefs(ctx)
        val all = p.all.keys.filter { it.endsWith("_updated") }.map { it.removeSuffix("_updated") }
        return all.sortedByDescending { p.getLong("${it}_updated", 0L) }.take(limit)
    }
}
