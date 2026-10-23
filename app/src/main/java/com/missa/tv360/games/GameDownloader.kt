package com.missa.tv360.games

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.missa.tv360.data.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/** Téléchargement borné, annulable avec vérification de checksum optionnelle avant installation. */
class GameDownloader(private val context: Context) {
    companion object {
        private const val MAX_DOWNLOAD_SIZE_BYTES: Long = 500L * 1024 * 1024
    }

    private val allowedHosts = setOf("github.com", "github.io", "githubusercontent.com", "f-droid.org")
    private val sha256Pattern = Pattern.compile("^[a-fA-F0-9]{64}$")
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    data class DownloadState(
        val gameId: String,
        var progress: Int = 0,
        var running: Boolean = false,
        var done: Boolean = false,
        var failed: String? = null,
        var installed: Boolean = false,
    )

    private val _state = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val state: StateFlow<Map<String, DownloadState>> = _state

    private fun update(gameId: String, block: (DownloadState) -> Unit) {
        val current = _state.value[gameId] ?: DownloadState(gameId)
        val copy = current.copy()
        block(copy)
        _state.value = _state.value + (gameId to copy)
    }

    fun cancel(gameId: String) {
        cancelled += gameId
        update(gameId) {
            it.running = false
            it.progress = 0
            it.done = false
            it.failed = null
        }
    }

    private fun isHostAllowed(rawUrl: String): Boolean = try {
        val url = URL(rawUrl)
        val host = url.host.orEmpty()
        url.protocol.equals("https", true) &&
            allowedHosts.any { host.equals(it, true) || host.endsWith(".${it.lowercase()}") }
    } catch (_: Exception) {
        false
    }

    /** @return null après un paquet vérifié ou une annulation, sinon une erreur affichable. */
    suspend fun downloadAndInstall(game: Game): String? = withContext(Dispatchers.IO) {
        if (game.downloadUrl.isBlank()) return@withContext "Aucun fichier configuré pour ce jeu."
        val checksum = game.sha256?.trim().orEmpty()
        if (checksum.isNotBlank() && !sha256Pattern.matcher(checksum).matches()) {
            return@withContext "Checksum SHA-256 invalide — installation bloquée."
        }
        if (!isHostAllowed(game.downloadUrl)) return@withContext "Hôte non autorisé pour le téléchargement."
        cancelled -= game.id
        update(game.id) {
            it.running = true
            it.progress = 0
            it.failed = null
            it.done = false
            it.installed = false
        }
        val directory = File(context.cacheDir, "games").apply { mkdirs() }
        val safeId = game.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeType = game.packageType.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "bin" }
        val destination = File(directory, "$safeId.$safeType")
        val partial = File(directory, "$safeId.$safeType.part")
        try {
            downloadToFile(game, partial)
            if (cancelled.contains(game.id)) throw DownloadCancelled
            if (checksum.isNotBlank()) {
                val actual = sha256Of(partial)
                if (!actual.equals(checksum, ignoreCase = true)) {
                    throw IOException("Checksum incorrect — fichier rejeté.")
                }
            }
            if (destination.exists() && !destination.delete()) throw IOException("Impossible de remplacer l’ancien paquet.")
            if (!partial.renameTo(destination)) throw IOException("Impossible de finaliser le téléchargement.")
            update(game.id) { it.progress = 100; it.running = false; it.done = true; it.failed = null }
            if (game.packageType.equals("apk", true)) {
                if (cancelled.contains(game.id)) throw DownloadCancelled
                installApk(destination)
            }
            null
        } catch (_: DownloadCancelled) {
            partial.delete()
            update(game.id) { it.running = false; it.progress = 0; it.done = false; it.failed = null }
            null
        } catch (cancelledError: CancellationException) {
            partial.delete()
            update(game.id) { it.running = false }
            throw cancelledError
        } catch (error: Exception) {
            partial.delete()
            val message = error.message ?: "Erreur de téléchargement inconnue."
            update(game.id) { it.running = false; it.failed = message; it.done = false }
            message
        }
    }

    private fun downloadToFile(game: Game, destination: File) {
        val connection = (URL(game.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Le serveur du jeu a répondu HTTP $code.")
            if (!isHostAllowed(connection.url.toString())) throw IOException("Redirection vers un hôte non autorisé.")
            val declaredSize = connection.contentLength.toLong()
            if (declaredSize > MAX_DOWNLOAD_SIZE_BYTES) throw IOException("Le fichier dépasse la limite de 500 Mo.")
            connection.inputStream.use { input: InputStream ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastProgress = -1
                    while (true) {
                        if (cancelled.contains(game.id)) throw DownloadCancelled
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_SIZE_BYTES) throw IOException("Le fichier dépasse la limite de 500 Mo.")
                        output.write(buffer, 0, read)
                        if (declaredSize > 0) {
                            val progress = ((total * 100) / declaredSize).toInt().coerceIn(0, 99)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                update(game.id) { it.progress = progress }
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun openVerified(game: Game): Boolean {
        if (state.value[game.id]?.done != true || !game.packageType.equals("apk", true)) return false
        val safeId = game.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(File(context.cacheDir, "games"), "$safeId.apk")
        if (!file.isFile) return false
        installApk(file)
        return true
    }

    private fun installApk(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private object DownloadCancelled : Exception()
}
