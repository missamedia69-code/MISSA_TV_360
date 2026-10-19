package com.missa.tv360.data

import android.content.Context

object MyListStore {
    private const val PREFS = "my_list"

    fun contains(ctx: Context, id: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(id, false)

    fun toggle(ctx: Context, id: String): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getBoolean(id, false)
        prefs.edit().putBoolean(id, !current).apply()
        return !current
    }
}