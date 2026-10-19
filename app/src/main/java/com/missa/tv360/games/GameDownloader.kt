package com.missa.tv360.games

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.missa.tv360.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Telecharge un fichier de jeu (APK / asset) dans le cache prive de l'app,
 * verifie la somme de controle SHA-256, puis propose l'installation.
 *
 * Securite : on n'installe jamais un fichier dont le checksum ne correspond
 * pas a celui publie dans le catalogue. L'installation d'APK externe passe
 * par le FileProvider + Intent d'installation systeme (permission
 * REQUEST_INSTALL_APPS geree par l'app).
 */
class GameDownloader(private val context: Context) {

    /** Etat d'un telechargement, expose a l'UI. */
    data class DownloadState(
        val gameId: String,
        var progress: Int = 0,        // 0..100
        var running: Boolean = false,
        var done: Boolean = false,
        var failed: String? = null,
        var installed: Boolean = false,
    )

    private val _state = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val state: StateFlow<Map<String, DownloadState>> = _state

    private fun update(gameId: String, block: (DownloadState) -> Unit) {
        val cur = _state.value[gameId] ?: DownloadState(gameId)
        val copy = cur.copy()
        block(copy)
        _state.value = _state.value + (gameId to copy)
    }

    /**
     * Telecharge le jeu puis lance l'installation si c'est un APK.
     * Retourne null en cas de succes, ou un message d'erreur lisible.
     */
    suspend fun downloadAndInstall(game: Game): String? = withContext(Dispatchers.IO) {
        if (game.downloadUrl.isBlank()) {
            return@withContext "Aucun fichier configure pour ce jeu (emplacement reserve)."
        }
        update(game.id) { it.running = true; it.progress = 0; it.failed = null; it.done = false }
        try {
            val dest = File(context.cacheDir, "games").apply { mkdirs() }
            val file = File(dest, "${game.id}.${game.packageType}")

            val ok = downloadToFile(game.downloadUrl, file) { pct ->
                update(game.id) { it.progress = pct }
            }
            if (!ok) throw Exception("Telechargement interrompu.")

            // Verification du checksum si le catalogue en fournit un.
            if (!game.sha256.isNullOrBlank()) {
                val actual = sha256Of(file)
                if (!actual.equals(game.sha256, ignoreCase = true)) {
                    file.delete()
                    throw Exception("Checksum incorrect (fichier rejete).")
                }
            }

            update(game.id) { it.progress = 100; it.running = false; it.done = true }

            if (game.packageType.equals("apk", ignoreCase = true)) {
                installApk(file, game.id)
            }
            null
        } catch (e: Exception) {
            update(game.id) { it.running = false; it.failed = e.message ?: "Erreur inconnue" }
            e.message ?: "Erreur inconnue"
        }
    }

    private fun downloadToFile(rawUrl: String, dest: File, onProgress: (Int) -> Unit): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            val total = conn.contentLength.toLong().takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input: InputStream ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var acc = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        acc += read
                        if (total > 0) {
                            val pct = ((acc * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Lance l'installation systeme d'un APK via le mecanisme Android standard. */
    private fun installApk(file: File, gameId: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        update(gameId) { it.installed = true }
    }
}
