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
import java.io.File
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

    // ── Cache disque ────────────────────────────────────────────────────────
    // Les vignettes du catalogue sont stables : sans cache disque, chaque
    // défilement re-téléchargeait les mêmes images (lent, coûteux en data).
    // Le dossier est unique par URL, donc deux contenus ne se marchent pas
    // dessus.

    private const val DISK_CACHE_DIR = "thumbnails"
    private const val MAX_DISK_BYTES = 24L * 1024 * 1024

    @Volatile
    private var diskCache: File? = null

    /** Initialise le cache disque (à appeler une fois, au démarrage). */
    fun initDiskCache(context: Context) {
        if (diskCache != null) return
        val dir = File(context.cacheDir, DISK_CACHE_DIR)
        dir.mkdirs()
        diskCache = dir
        trimDiskCache(dir)
    }

    private fun diskFile(url: String): File? {
        val dir = diskCache ?: return null
        val name = Integer.toHexString(url.hashCode()) + "-" +
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }
        return File(dir, name)
    }

    private fun readDisk(url: String): Bitmap? {
        val file = diskFile(url) ?: return null
        if (!file.isFile || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDisk(url: String, bitmap: Bitmap) {
        val file = diskFile(url) ?: return
        try {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
        } catch (_: Exception) {
            // Cache best-effort : un échec d'écriture n'affecte pas l'affichage.
        }
    }

    /** Purge les fichiers les plus anciens quand le cache dépasse sa limite. */
    private fun trimDiskCache(dir: File) {
        try {
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_DISK_BYTES) return
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= MAX_DISK_BYTES) return@forEach
                val size = file.length()
                if (file.delete()) total -= size
            }
        } catch (_: Exception) {
            // Purge best-effort.
        }
    }

    private const val TARGET_WIDTH = 320

    /**
     * Un type MIME est-il potentiellement décodable par BitmapFactory ?
     * Les types génériques (application/octet-stream, absent) sont laissés
     * au chance : seule l'absence de bitmap tranchera.
     */
    private fun isDecodableType(contentType: String): Boolean =
        contentType.startsWith("image/") && contentType !in NON_DECODABLE_TYPES

    /** Types d'image servis par les CDN mais non décodables nativement. */
    private val NON_DECODABLE_TYPES = setOf("image/svg+xml", "image/svg")

    fun load(card: ImageCardView, url: String, drawableFallback: Drawable? = null) {
        val target: ImageView = card.mainImageView ?: return
        val apply = { bitmap: Bitmap ->
            target.setImageBitmap(bitmap)
            card.mainImage = BitmapDrawable(target.resources, bitmap)
        }
        cached(url)?.let { apply(it); return }
        pool.execute {
            val bitmap = readDisk(url) ?: fetch(url)?.also { writeDisk(url, it) }
            card.post {
                if (card.tag != url) return@post
                if (bitmap != null) {
                    apply(bitmap)
                } else {
                    // Aucun bitmap : on restaure le dessin de secours pour éviter une tuile vide.
                    target.setImageDrawable(drawableFallback)
                    card.mainImage = drawableFallback
                }
            }
        }
    }

    /**
     * Charge une image pour les cartes, fiches et le lecteur, puis livre le
     * bitmap sur l'UI thread. Le callback n'est appelé QUE si l'image a pu être
     * décodée : en cas d'échec (SVG, 404, 403, réseau), l'appelant garde son
     * repli local.
     */
    fun loadArtwork(url: String, onLoaded: (Bitmap) -> Unit) {
        cached(url)?.let { onLoaded(it); return }
        pool.execute {
            val bitmap = readDisk(url) ?: fetch(url)?.also { writeDisk(url, it) }
            if (bitmap == null) return@execute
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

                // Les vignettes servies en SVG ne sont pas décodables par
                // BitmapFactory : inutile de télécharger le corps, on renvoie
                // null et l'appelant affiche son repli local. Les types
                // génériques (application/octet-stream) sont acceptés : le
                // décodage réel décidera.
                val contentType = conn.contentType?.lowercase()?.substringBefore(';')?.trim()
                if (contentType != null && !isDecodableType(contentType)) return null

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
