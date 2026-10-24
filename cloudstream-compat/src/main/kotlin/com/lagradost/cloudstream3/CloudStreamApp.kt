package com.lagradost.cloudstream3

import android.app.Application
import android.content.Context

/**
 * Surface minimale de CloudStreamApp réellement demandée par les sondes.
 *
 * AllMovieLand référence `CloudStreamApp.Companion.getContext()`. Le contexte
 * applicatif est préféré à une Activity pour ne pas retenir un écran détruit.
 */
class CloudStreamApp : Application() {
    companion object {
        @Volatile
        var context: Context? = null
            private set

        fun setApplicationContext(context: Context) {
            this.context = context.applicationContext
        }
    }
}
