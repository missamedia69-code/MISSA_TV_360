package com.missa.tv360.extensions

import java.io.File
import java.security.MessageDigest

/**
 * Vérification d'intégrité des artefacts téléchargés.
 *
 * Le dépôt publie `fileHash` au format `sha256-<hex>` (ou `sha256:<hex>`).
 * Contrairement à d'autres écosystèmes, MISSA refuse un plugin sans hash :
 * charger du code non vérifié reviendrait à exécuter un binaire arbitraire.
 */
internal object ExtensionHash {

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Normalise `sha256-ABC…` / `sha256:abc…` / `abc…` en hexadécimal minuscule. */
    fun normalize(expected: String): String {
        val trimmed = expected.trim()
        val withoutPrefix = trimmed
            .removePrefix("sha256-")
            .removePrefix("sha256:")
            .removePrefix("SHA256-")
            .removePrefix("SHA256:")
        return withoutPrefix.lowercase()
    }

    /** Comparaison à temps constant de deux empreintes hexadécimales. */
    fun matches(expected: String?, actual: String): Boolean {
        if (expected.isNullOrBlank()) return false
        val normalized = normalize(expected)
        if (normalized.length != actual.length) return false
        return MessageDigest.isEqual(
            normalized.toByteArray(Charsets.US_ASCII),
            actual.lowercase().toByteArray(Charsets.US_ASCII),
        )
    }
}
