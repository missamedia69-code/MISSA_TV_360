package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.json.JsonMapper
import com.lagradost.cloudstream3.mapper

object DataStore {
    fun getSharedPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("cloudstream_compat", Context.MODE_PRIVATE)

    fun getMapper(): JsonMapper = mapper
}
