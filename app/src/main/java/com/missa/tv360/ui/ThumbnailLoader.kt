package com.missa.tv360.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Chargement asynchrone des vignettes distantes du catalogue (dépôt GitHub).
 *
 * Pas de bibliothèque d'images : un pool de threads, un GET, un décodage
 * réduit (inSampleSize) puis application sur l'UI thread. Le tag de la carte
 * sert de garde-fou : si la carte a été recyclée pour un autre contenu, la
 * bitmap arrivée tardivement est ignorée.
 */
object ThumbnailLoader {

    private val pool = Executors.newFixedThreadPool(3)

    /** Décodage cible ~ 320px de large (taille d'une carte Leanback). */
    private const val TARGET_WIDTH = 320

    fun load(card: ImageCardView, url: String) {
        val target: ImageView = card.mainImageView ?: return
        // Image locale déjà en place ? On la laisse tant que la distante n'est pas prête.
        pool.execute {
            val bitmap = fetch(url) ?: return@execute
            card.post {
                if (card.tag == url) {
                    target.setImageBitmap(bitmap)
                    card.mainImage = BitmapDrawable(target.resources, bitmap)
                }
            }
        }
    }

    private fun fetch(url: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null

            // Décodage avec sous-échantillonnage pour limiter la mémoire.
            val bytes = conn.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
