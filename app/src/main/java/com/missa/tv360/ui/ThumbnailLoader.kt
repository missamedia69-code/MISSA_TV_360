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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Chargeur partagé de vignettes : caches bornés, décodage sous-échantillonné et repli local. */
object ThumbnailLoader {
    private val pool = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val DEFAULT_CARD_WIDTH = 640
    private const val DEFAULT_BACKDROP_WIDTH = 1280
    private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
    private const val DISK_CACHE_DIR = "thumbnails"
    private const val MAX_DISK_BYTES = 32L * 1024 * 1024
    private const val TRIM_INTERVAL_MS = 60_000L

    private val cache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : LruCache<String, Bitmap>(maxKb.coerceAtLeast(4 * 1024)) {
            override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    @Volatile private var diskCache: File? = null
    @Volatile private var lastTrimAt = 0L

    private fun key(url: String, targetWidth: Int) = "$targetWidth|$url"
    private fun cached(url: String, targetWidth: Int): Bitmap? = cache.get(key(url, targetWidth))

    fun clearCache() = cache.evictAll()

    fun initDiskCache(context: Context) {
        if (diskCache != null) return
        val dir = File(context.cacheDir, DISK_CACHE_DIR)
        dir.mkdirs()
        diskCache = dir
        trimDiskCache(dir)
    }

    private fun diskFile(url: String, targetWidth: Int): File? {
        val dir = diskCache ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key(url, targetWidth).toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return File(dir, digest)
    }

    private fun readDisk(url: String, targetWidth: Int): Bitmap? {
        val file = diskFile(url, targetWidth) ?: return null
        if (!file.isFile || file.length() == 0L) return null
        return try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
    }

    private fun writeDisk(url: String, targetWidth: Int, bitmap: Bitmap) {
        val file = diskFile(url, targetWidth) ?: return
        try {
            file.outputStream().use { out ->
                if (bitmap.hasAlpha()) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                else bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            val now = System.currentTimeMillis()
            if (now - lastTrimAt > TRIM_INTERVAL_MS) {
                lastTrimAt = now
                file.parentFile?.let(::trimDiskCache)
            }
        } catch (_: Exception) {
            // Le cache reste facultatif : l'image déjà décodée demeure utilisable.
        }
    }

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
            // Purge best effort.
        }
    }

    fun load(
        card: ImageCardView,
        url: String,
        drawableFallback: Drawable? = null,
        targetWidth: Int = DEFAULT_CARD_WIDTH,
    ) {
        val target: ImageView = card.mainImageView ?: return
        val apply = { bitmap: Bitmap ->
            target.setImageBitmap(bitmap)
            card.mainImage = BitmapDrawable(target.resources, bitmap)
        }
        cached(url, targetWidth)?.let { apply(it); return }
        pool.execute {
            val bitmap = (readDisk(url, targetWidth) ?: fetch(url, targetWidth = targetWidth)?.also {
                writeDisk(url, targetWidth, it)
            })?.also { cache.put(key(url, targetWidth), it) }
            card.post {
                if (card.tag != url) return@post
                if (bitmap != null) apply(bitmap)
                else {
                    target.setImageDrawable(drawableFallback)
                    card.mainImage = drawableFallback
                }
            }
        }
    }

    /** Charge une image à la taille demandée et livre le bitmap sur le thread UI. */
    fun loadArtwork(
        url: String,
        targetWidth: Int = DEFAULT_CARD_WIDTH,
        onLoaded: (Bitmap) -> Unit,
    ) {
        cached(url, targetWidth)?.let { onLoaded(it); return }
        pool.execute {
            val bitmap = (readDisk(url, targetWidth) ?: fetch(url, targetWidth = targetWidth)?.also {
                writeDisk(url, targetWidth, it)
            })?.also { cache.put(key(url, targetWidth), it) } ?: return@execute
            mainHandler.post { onLoaded(bitmap) }
        }
    }

    fun loadDrawable(
        context: Context,
        url: String?,
        @DrawableRes fallbackRes: Int,
        targetWidth: Int = DEFAULT_BACKDROP_WIDTH,
        onLoaded: (Drawable) -> Unit,
    ) {
        ContextCompat.getDrawable(context, fallbackRes)?.let(onLoaded)
        if (url.isNullOrBlank()) return
        loadArtwork(url, targetWidth) { bitmap ->
            onLoaded(BitmapDrawable(context.resources, bitmap))
        }
    }

    private fun fetch(urlStr: String, maxRedirects: Int = 5, targetWidth: Int): Bitmap? {
        var currentUrl = urlStr
        var redirects = 0
        val width = targetWidth.coerceIn(160, 1920)
        while (redirects < maxRedirects) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MISSA-TV-360/1.0 (Android)")
                }
                val code = conn.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = URL(URL(currentUrl), location).toString()
                        redirects++
                        conn.disconnect()
                        continue
                    }
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                val contentType = conn.contentType?.lowercase()?.substringBefore(';')?.trim()
                if (contentType != null && !isPotentiallyDecodable(contentType)) return null
                if (conn.contentLength.toLong() > MAX_IMAGE_BYTES) return null
                val bytes = conn.inputStream.use { it.readBounded(MAX_IMAGE_BYTES) } ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= width && sample < 32) sample *= 2
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

    private fun isPotentiallyDecodable(contentType: String): Boolean = when {
        contentType.startsWith("image/") -> contentType !in setOf("image/svg+xml", "image/svg")
        contentType == "application/octet-stream" || contentType == "binary/octet-stream" -> true
        else -> false
    }

    private fun InputStream.readBounded(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
