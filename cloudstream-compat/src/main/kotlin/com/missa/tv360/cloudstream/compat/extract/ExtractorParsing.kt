package com.missa.tv360.cloudstream.compat.extract

import kotlin.random.Random

/**
 * Fonctions pures (sans réseau, sans Android) qui lisent les pages des hébergeurs vidéo.
 * Elles sont testables sur des pages HTML enregistrées : aucune ne contacte un vrai site.
 */
object ExtractorParsing {

    private val MEDIA_REGEX = Regex(
        """https?://[^\s"'\\<>]+\.(?:m3u8|mp4)(?:\?[^\s"'\\<>]*)?""",
        RegexOption.IGNORE_CASE,
    )

    private val SOURCES_ARRAY = Regex("""sources\s*:\s*\[\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
    private val SRC_PROPERTY = Regex("""\bsrc\s*:\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
    private val SOURCE_TAG = Regex("""<source[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val VOE_HLS = Regex("""["']hls["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val VOE_MP4 = Regex("""["']mp4["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val VOE_SOURCE = Regex("""["']source["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val JS_REDIRECT = Regex("""window\.location(?:\.href)?\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val DOOD_PASS = Regex("""/pass_md5/[^'"\s<>]+""")
    private val DOOD_TOKEN = Regex("""[?&]token=([A-Za-z0-9]+)""")
    private val PACKED = Regex(
        """\}\(\s*'((?:[^'\\]|\\.)*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:[^'\\]|\\.)*)'\s*\.split\(\s*'\|'\s*\)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val FEMBED_ENTRY = Regex("""\{[^{}]*"file"[^{}]*\}""")
    private val FEMBED_FILE = Regex(""""file"\s*:\s*"([^"]+)"""")
    private val FEMBED_LABEL = Regex(""""label"\s*:\s*"(\d{3,4})""")
    private const val ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    /** Retire les échappements courants des JSON/JS incorporés dans une page. */
    fun normalizeEscapes(text: String): String =
        text.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")

    /** Tous les liens .mp4 / .m3u8 d'un texte, sans doublon, dans l'ordre. */
    fun findMediaUrls(text: String): List<String> =
        MEDIA_REGEX.findAll(normalizeEscapes(text)).map { it.value }.distinct().toList()

    // ---- JavaScript « packed » (eval(function(p,a,c,k,e,d){...})) ---------------------------------

    /** Chiffre d'un indice dans la base `base` (2..62), comme le packer standard. */
    fun toBase(value: Int, base: Int): String {
        require(base in 2..62) { "base invalide : $base" }
        val prefix = if (value < base) "" else toBase(value / base, base)
        val digit = value % base
        val c = if (digit > 35) (digit + 29).toChar() else Character.forDigit(digit, 36)
        return prefix + c
    }

    private fun unescapeJsString(s: String): String =
        s.replace("\\'", "'").replace("\\\\", "\\")

    /** Dépaquette chaque bloc packed trouvé ; renvoie null s'il n'y en a pas. */
    fun unpackJs(text: String): String? {
        val blocks = PACKED.findAll(text).toList()
        if (blocks.isEmpty()) return null
        val out = StringBuilder()
        for (m in blocks) {
            val payload = unescapeJsString(m.groupValues[1])
            val base = m.groupValues[2].toIntOrNull() ?: continue
            val count = m.groupValues[3].toIntOrNull() ?: continue
            if (base !in 2..62) continue
            val words = m.groupValues[4].split('|')
            val dictionary = HashMap<String, String>()
            for (i in 0 until count) {
                val word = words.getOrNull(i).orEmpty()
                if (word.isNotEmpty()) dictionary[toBase(i, base)] = word
            }
            out.append(Regex("""\b\w+\b""").replace(payload) { dictionary[it.value] ?: it.value })
            out.append('\n')
        }
        return out.toString().takeIf { it.isNotBlank() }
    }

    // ---- Décodage base64 sans dépendance (java.util.Base64 n'existe pas sous Android 8) -----------

    fun decodeBase64(input: String): String? {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val clean = input.trim().replace('-', '+').replace('_', '/').trimEnd('=')
        if (clean.isEmpty() || clean.any { table.indexOf(it) < 0 }) return null
        val bytes = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            buffer = (buffer shl 6) or table.indexOf(ch)
            bits += 6
            if (bits >= 8) {
                bits -= 8
                bytes.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return runCatching { String(bytes.toByteArray(), Charsets.UTF_8) }.getOrNull()
    }

    private fun looksLikeUrl(s: String) = s.startsWith("http://") || s.startsWith("https://")

    /** Valeur directe, ou valeur décodée en base64 si elle donne une URL. */
    private fun resolveMaybeEncoded(value: String): String? {
        val v = normalizeEscapes(value).trim()
        if (looksLikeUrl(v)) return v
        val decoded = decodeBase64(v)?.let { normalizeEscapes(it).trim() }
        return decoded?.takeIf { looksLikeUrl(it) }
    }

    // ---- Hébergeurs -------------------------------------------------------------------------------

    private fun fallbackMedia(html: String): List<String> {
        val direct = findMediaUrls(html)
        if (direct.isNotEmpty()) return direct
        val unpacked = unpackJs(html) ?: return emptyList()
        return findMediaUrls(unpacked)
    }

    /** Uqload, Vudeo : `sources: ["https://....mp4"]`, sinon recherche générale. */
    fun parseSourcesPage(html: String): List<String> {
        val text = normalizeEscapes(html)
        val fromArray = SOURCES_ARRAY.findAll(text).map { it.groupValues[1] }.distinct().toList()
        if (fromArray.isNotEmpty()) return fromArray
        val unpacked = unpackJs(text)
        if (unpacked != null) {
            val u = SOURCES_ARRAY.findAll(normalizeEscapes(unpacked)).map { it.groupValues[1] }.distinct().toList()
            if (u.isNotEmpty()) return u
        }
        return fallbackMedia(text)
    }

    /** Vidoza : `src: "https://....mp4"` ou balise <source>. */
    fun parseVidoza(html: String): List<String> {
        val text = normalizeEscapes(html)
        val found = (SRC_PROPERTY.findAll(text).map { it.groupValues[1] } +
            SOURCE_TAG.findAll(text).map { it.groupValues[1] })
            .filter { looksLikeUrl(it) }
            .distinct().toList()
        return found.ifEmpty { fallbackMedia(text) }
    }

    /** Voe : champs hls / mp4 / source (éventuellement en base64), sinon recherche générale. */
    fun parseVoe(html: String): List<String> {
        val text = normalizeEscapes(html)
        val ordered = ArrayList<String>()
        for (regex in listOf(VOE_HLS, VOE_MP4, VOE_SOURCE)) {
            regex.findAll(text).forEach { m -> resolveMaybeEncoded(m.groupValues[1])?.let(ordered::add) }
        }
        val result = ordered.distinct()
        return result.ifEmpty { fallbackMedia(text) }
    }

    /** Redirection JavaScript d'une page Voe (`window.location.href = '...'`), ou null. */
    fun findJsRedirect(html: String): String? =
        JS_REDIRECT.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** DoodStream : chemin /pass_md5/... et jeton, ou null si la page est inattendue. */
    fun parseDood(html: String): Pair<String, String>? {
        val pass = DOOD_PASS.find(html)?.value ?: return null
        val token = DOOD_TOKEN.find(html)?.groupValues?.get(1) ?: return null
        return pass to token
    }

    /** Lien final DoodStream ; null si la réponse de /pass_md5 n'est pas une URL. */
    fun buildDoodUrl(base: String, token: String, random: String, nowMs: Long): String? {
        val b = base.trim()
        if (!looksLikeUrl(b)) return null
        return "$b$random?token=$token&expiry=$nowMs"
    }

    fun randomAlnum(length: Int, random: Random = Random.Default): String =
        buildString { repeat(length) { append(ALPHANUM[random.nextInt(ALPHANUM.length)]) } }

    /** Fembed / XStreamCdn : JSON {"success":true,"data":[{"file":"...","label":"720p"}]} -> (url, qualité). */
    fun parseFembedJson(json: String): List<Pair<String, Int>> {
        if (!Regex(""""success"\s*:\s*true""").containsMatchIn(json)) return emptyList()
        return FEMBED_ENTRY.findAll(normalizeEscapes(json)).mapNotNull { entry ->
            val file = FEMBED_FILE.find(entry.value)?.groupValues?.get(1)?.takeIf { looksLikeUrl(it) }
                ?: return@mapNotNull null
            val quality = FEMBED_LABEL.find(entry.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            file to quality
        }.toList()
    }
}
