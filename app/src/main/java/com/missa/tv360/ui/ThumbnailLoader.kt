package com.missa.tv360.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Chargement asynchrone des vignettes distantes du catalogue.
 * Gère la mise en cache mémoire (LruCache) et le suivi des redirections HTTP (301/302/303/307/308).
 */
object ThumbnailLoader {

    private val pool = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : LruCache<String, Bitmap>(maxKb.coerceAtLeast(4 * 1024)) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }
    }

    private fun cached(url: String): Bitmap? = cache.get(url)

    fun clearCache() {
        cache.evictAll()
    }

    private const val TARGET_WIDTH = 320

    fun load(card: ImageCardView, url: String) {
        val target: ImageView = card.mainImageView ?: return
        val apply = { bitmap: Bitmap ->
            target.setImageBitmap(bitmap)
            card.mainImage = BitmapDrawable(target.resources, bitmap)
        }
        cached(url)?.let { apply(it); return }
        pool.execute {
            val bitmap = fetch(url) ?: return@execute
            cache.put(url, bitmap)
            card.post {
                if (card.tag == url) apply(bitmap)
            }
        }
    }

    /** Charge une image pour les cartes, fiches et le lecteur, puis livre le bitmap sur l'UI thread. */
    fun loadArtwork(url: String, onLoaded: (Bitmap) -> Unit) {
        cached(url)?.let { onLoaded(it); return }
        pool.execute {
            val bitmap = fetch(url) ?: return@execute
            cache.put(url, bitmap)
            mainHandler.post { onLoaded(bitmap) }
        }
    }

    fun loadDrawable(
        context: Context,
        url: String?,
        @DrawableRes fallbackRes: Int,
        onLoaded: (Drawable) -> Unit,
    ) {
        val fallback = ContextCompat.getDrawable(context, fallbackRes)
        if (fallback != null) onLoaded(fallback)
        if (url.isNullOrBlank()) return
        loadArtwork(url) { bitmap ->
            onLoaded(BitmapDrawable(context.resources, bitmap))
        }
    }

    private fun fetch(urlStr: String, maxRedirects: Int = 5): Bitmap? {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < maxRedirects) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; MISSA TV 360)")
                }
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == 303 || code == 307 || code == 308
                ) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = URL(URL(currentUrl), location).toString()
                        redirects++
                        conn.disconnect()
                        continue
                    }
                }
                if (code != HttpURLConnection.HTTP_OK) return null

                val bytes = conn.inputStream.use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sample = 1
                while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2

                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } catch (_: Exception) {
                return null
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }
}
