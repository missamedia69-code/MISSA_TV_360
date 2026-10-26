package com.missa.tv360.cloudstream.compat.extract

/** Outils de diagnostic purs : savoir QUELLE page un site renvoie quand une extension « ne trouve pas le titre ». */
object HtmlDiagnostics {
    private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** Texte de la balise <title>, nettoyé et limité à [max] caractères ; null s'il n'y en a pas. */
    fun title(html: String, max: Int = 80): String? {
        val raw = TITLE.find(html)?.groupValues?.get(1) ?: return null
        val clean = raw.replace(Regex("""\s+"""), " ").trim()
        if (clean.isEmpty()) return null
        return if (clean.length > max) clean.take(max) + "…" else clean
    }

    /** Vrai si le titre ressemble à une page de vérification / d'erreur plutôt qu'au contenu attendu. */
    fun looksLikeInterstitial(title: String?): Boolean {
        val t = title?.lowercase() ?: return false
        return listOf(
            "just a moment", "attention required", "checking your browser", "vérification",
            "verification", "access denied", "403", "404", "not found", "error", "erreur",
            "ddos", "captcha", "domain", "parked", "for sale",
        ).any { t.contains(it) }
    }
}
