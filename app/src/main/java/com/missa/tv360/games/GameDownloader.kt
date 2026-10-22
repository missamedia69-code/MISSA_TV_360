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
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Telecharge un fichier de jeu (APK / asset) dans le cache prive de l'app,
 * verifie la somme de controle SHA-256, puis propose l'installation.
 *
 * Securite :
 *  - SHA-256 OBLIGATOIRE : aucun jeu sans empreinte n'est installe.
 *  - Plafonnement de taille : refuse les fichiers au-dessus de la limite.
 *  - Hotes autorisés : seuls les domaines de la liste sont acceptes.
 *  - Verification du checksum avant toute installation.
 * L'installation d'APK externe passe par le FileProvider + Intent d'installation
 * systeme (permission REQUEST_INSTALL_APPS geree par l'app).
 */
class GameDownloader(private val context: Context) {

    companion object {
        /** Taille maximale autorisee pour un telechargement de jeu (500 Mo). */
        private const val MAX_DOWNLOAD_SIZE_BYTES: Long = 500L * 1024 * 1024
    }

    /** Hotes autorises pour le telechargement de jeux (protection contre exfiltration). */
    private val ALLOWED_HOSTS = setOf(
        "f-droid.org",
        "github.com",
        "releases.github.com",
        "github.io",
    )

    private val SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$")

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

    /** Vérifie que l'hôte de l'URL est dans la liste autorisée. */
    private fun isHostAllowed(rawUrl: String): Boolean {
        return try {
            val host = URL(rawUrl).host ?: ""
            ALLOWED_HOSTS.any { host.equals(it, ignoreCase = true) || host.endsWith(".${it.lowercase()}") }
        } catch (_: MalformedURLException) {
            false
        }
    }

    /**
     * Télécharge le jeu puis lance l'installation si c'est un APK.
     * Retourne null en cas de succès, ou un message d'erreur lisible.
     */
    suspend fun downloadAndInstall(game: Game): String? = withContext(Dispatchers.IO) {
        if (game.downloadUrl.isBlank()) {
            return@withContext "Aucun fichier configuré pour ce jeu (emplacement réservé)."
        }
        // SHA-256 obligatoire : on n'installe jamais sans empreinte.
        if (game.sha256.isNullOrBlank() || !SHA256_PATTERN.matcher(game.sha256!!).matches()) {
            return@withContext "Checksum SHA-256 manquant ou invalide — installation bloquée."
        }
        // Hôte autorisé uniquement.
        if (!isHostAllowed(game.downloadUrl)) {
            return@withContext "Hôte non autorisé pour le téléchargement : ${game.downloadUrl}"
        }
        update(game.id) { it.running = true; it.progress = 0; it.failed = null; it.done = false }
        try {
            val dest = File(context.cacheDir, "games").apply { mkdirs() }
            val file = File(dest, "${game.id}.${game.packageType}")

            val ok = downloadToFile(game.downloadUrl, file, MAX_DOWNLOAD_SIZE_BYTES) { pct ->
                update(game.id) { it.progress = pct }
            }
            if (!ok) throw Exception("Téléchargement interrompu ou taille dépasse la limite.")

            // Vérification du checksum obligatoire.
            val actual = sha256Of(file)
            if (!actual.equals(game.sha256, ignoreCase = true)) {
                file.delete()
                throw Exception("Checksum incorrect (fichier rejeté).")
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

    private fun downloadToFile(rawUrl: String, dest: File, maxSizeBytes: Long, onProgress: (Int) -> Unit): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            // Vérification de la taille déclarée avant téléchargement.
            val declaredSize = conn.contentLength.toLong()
            if (declaredSize > 0 && declaredSize > maxSizeBytes) {
                return false
            }
            conn.inputStream.use { input: InputStream ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var acc = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } != -1) {
                        acc += read
                        if (acc > maxSizeBytes) {
                            return@use false // taille réelle dépasse la limite
                        }
                        out.write(buf, 0, read)
                        if (declaredSize > 0) {
                            val pct = ((acc * 100) / declaredSize).toInt().coerceIn(0, 100)
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

    /** Lance l'installation système d'un APK via le mécanisme Android standard. */
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
