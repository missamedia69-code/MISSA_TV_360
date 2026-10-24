package com.missa.tv360.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionClassFailureTest {

    @Test
    fun `classe du manifeste absente reste BAD_MANIFEST`() {
        val manifest = "com.lagradost.FStreamProviderPlugin"
        val error = ClassNotFoundException(
            "Didn't find class \"$manifest\" on path: DexPathList[]",
        )

        val result = classifyClassFailure(manifest, error)

        assertEquals(ExtensionErrorType.BAD_MANIFEST, result.type)
        assertEquals(manifest, result.missingClass)
    }

    @Test
    fun `ClassNotFoundException FStream nomme la dependance et pas le manifeste`() {
        val dependency = "com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager"
        val error = ClassNotFoundException(
            "Didn't find class \"$dependency\" on path: DexPathList[[zip file FStream.cs3]]",
        )

        val result = classifyClassFailure("com.lagradost.FStreamProviderPlugin", error)

        assertEquals(ExtensionErrorType.MISSING_DEPENDENCY, result.type)
        assertEquals(dependency, result.missingClass)
    }

    @Test
    fun `NoClassDefFoundError conserve la dependance imbriquee exacte`() {
        val missing = NoClassDefFoundError("Failed resolution of: Lokhttp3/OkHttpClient;")
        val error = IllegalStateException("load", missing)

        val result = classifyClassFailure("com.example.Plugin", error)

        assertEquals(ExtensionErrorType.MISSING_DEPENDENCY, result.type)
        assertEquals("okhttp3.OkHttpClient", result.missingClass)
    }

    @Test
    fun `nom JVM simple est normalise`() {
        val result = classifyClassFailure(
            "com.example.Plugin",
            ClassNotFoundException("com.example.MissingDependency"),
        )

        assertEquals(ExtensionErrorType.MISSING_DEPENDENCY, result.type)
        assertEquals("com.example.MissingDependency", result.missingClass)
    }

    @Test
    fun `erreur sans classe identifiable reste LOAD_FAILED`() {
        val result = classifyClassFailure(
            "com.example.Plugin",
            IllegalStateException("échec différent"),
        )

        assertEquals(ExtensionErrorType.LOAD_FAILED, result.type)
        assertNull(result.missingClass)
    }
}
