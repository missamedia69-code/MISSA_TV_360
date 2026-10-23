package com.lagradost.cloudstream3

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore

/** Contexte et clés utilisés par les anciens réglages Cloudstream. */
open class AcraApplication {
    companion object {
        private var applicationContext: Context? = null

        fun setContext(context: Context) {
            applicationContext = context.applicationContext
        }

        fun getContext(): Context = applicationContext
            ?: throw IllegalStateException("CloudstreamCompat n'est pas initialisé")

        fun setKey(key: String, value: Any?) {
            val editor = DataStore.getSharedPrefs(getContext()).edit()
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                else -> editor.putString(key, mapper.writeValueAsString(value))
            }.apply()
        }
    }
}
