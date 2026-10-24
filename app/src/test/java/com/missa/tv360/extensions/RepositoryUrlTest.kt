package com.missa.tv360.extensions

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RepositoryUrlTest {

    @Test
    fun `code cloudstreamrepo est un lien court et non un hote`() {
        assertEquals(
            RepositoryInput.ShortLink("https://cutt.ly/cloudstreamrepo"),
            parseRepositoryInput("cloudstreamrepo"),
        )
    }

    @Test
    fun `format nom code conserve la resolution du code`() {
        assertEquals(
            RepositoryInput.ShortLink("https://cutt.ly/cloudstreamrepo"),
            parseRepositoryInput("Dépôt principal : cloudstreamrepo"),
        )
    }

    @Test
    fun `code alternatif Cloudstream utilise son service documente`() {
        assertEquals(
            RepositoryInput.ShortLink("https://py.md/mon-code"),
            parseRepositoryInput("!mon-code"),
        )
    }

    @Test
    fun `URL HTTPS directe reste inchangee`() {
        val url = "https://raw.githubusercontent.com/example/repo/main/repo.json"
        assertEquals(RepositoryInput.Direct(url), parseRepositoryInput("  $url  "))
    }

    @Test
    fun `lien de partage cloudstreamrepo contenant une URL est deballe`() {
        val url = "https://example.org/extensions/repo.json"
        assertEquals(
            RepositoryInput.Direct(url),
            parseRepositoryInput("cloudstreamrepo://$url"),
        )
    }

    @Test
    fun `lien cs repo sans scheme dans la cible ajoute HTTPS`() {
        assertEquals(
            RepositoryInput.Direct("https://example.org/extensions/repo.json"),
            parseRepositoryInput("https://cs.repo/?example.org/extensions/repo.json"),
        )
    }

    @Test
    fun `domaine sans scheme conserve le comportement existant`() {
        assertEquals(
            RepositoryInput.Direct("https://example.org/repo.json"),
            parseRepositoryInput("example.org/repo.json"),
        )
    }

    @Test
    fun `DNS absent produit un message court et conserve la cause separement`() {
        val cause = UnknownHostException("Unable to resolve host cloudstreamrepo")
        val error = ExtensionException("lecture impossible", cause)

        assertEquals(
            "Dépôt injoignable. Vérifiez le code ou l’adresse, puis votre connexion.",
            repositoryErrorMessage(error),
        )
        assertEquals(cause, error.cause)
    }

    @Test
    fun `timeout produit un message dedie`() {
        assertEquals(
            "Le dépôt ne répond pas. Réessayez dans un moment.",
            repositoryErrorMessage(SocketTimeoutException("timeout")),
        )
    }
}
