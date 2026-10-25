package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.Uqload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractorHostSelectionTest {
    private class WrongAliasExtractor : ExtractorApi() {
        override val name = "Vudeo"
        override val mainUrl = "https://vudeo.io"
        override val requiresReferer = false
        override val supportedHosts = setOf("vudeo.io", "uqload.com")
    }

    private class DoodAliasExtractor : ExtractorApi() {
        override val name = "DoodStream"
        override val mainUrl = "https://dood.la"
        override val requiresReferer = false
        override val supportedHosts = setOf("dood.la", "dood.so")
    }

    @Test
    fun canonicalHostWinsOverAnotherExtractorsIncorrectAlias() {
        val selected = findExtractorForHost(
            "uqload.com",
            listOf(Uqload(), WrongAliasExtractor())
        )

        assertEquals("Uqload", selected?.name)
    }

    @Test
    fun uqloadDoesNotFallBackToAnIncorrectAliasWhenItsExtractorIsMissing() {
        val selected = findExtractorForHost("uqload.com", listOf(WrongAliasExtractor()))

        assertNull(selected)
    }

    @Test
    fun aliasesStillResolveWhenNoCanonicalHostMatches() {
        val selected = findExtractorForHost("dood.so", listOf(DoodAliasExtractor()))

        assertEquals("DoodStream", selected?.name)
    }

    @Test
    fun subdomainsMatchWithoutAcceptingLookalikeDomains() {
        val extractors = listOf(Uqload())

        assertEquals("Uqload", findExtractorForHost("cdn.uqload.com", extractors)?.name)
        assertNull(findExtractorForHost("uqload.com.example.org", extractors))
    }
}
