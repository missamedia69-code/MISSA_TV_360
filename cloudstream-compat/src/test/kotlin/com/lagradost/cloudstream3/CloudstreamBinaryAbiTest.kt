package com.lagradost.cloudstream3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression tests for ABI signatures observed in real .cs3 extensions.
 * These tests verify JVM descriptors, not just Kotlin source-level properties:
 * plugins are precompiled and link against exact binary signatures.
 */
class CloudstreamBinaryAbiTest {
    @Test
    fun movieSearchResponseHasExpectedNineArgumentPrimaryConstructor() {
        val constructor = MovieSearchResponse::class.java.declaredConstructors
            .first { it.parameterCount == 9 }
        val types = constructor.parameterTypes
        assertEquals(String::class.java, types[0])
        assertEquals(String::class.java, types[1])
        assertEquals(String::class.java, types[2])
        assertEquals(TvType::class.java, types[3])
        assertEquals(String::class.java, types[4])
        assertEquals(Integer::class.java, types[5])
        assertEquals(Integer::class.java, types[6])
        assertEquals(SearchQuality::class.java, types[7])
        assertEquals(Map::class.java, types[8])
    }

    @Test
    fun movieSearchResponseHasKotlinDefaultArgumentsConstructor() {
        val constructor = MovieSearchResponse::class.java.declaredConstructors
            .firstOrNull {
                it.parameterCount == 11 &&
                    it.parameterTypes[9] == Integer.TYPE &&
                    it.parameterTypes[10].name == "kotlin.jvm.internal.DefaultConstructorMarker"
            }
        assertNotNull(
            "Missing MovieSearchResponse synthetic constructor required by Kotlin plugin defaults",
            constructor
        )
    }

    @Test
    fun episodeHasExpectedEightArgumentPrimaryConstructor() {
        val constructor = Episode::class.java.declaredConstructors
            .first { it.parameterCount == 8 }
        val types = constructor.parameterTypes
        assertEquals(String::class.java, types[0])
        assertEquals(String::class.java, types[1])
        assertEquals(Integer::class.java, types[2])
        assertEquals(Integer::class.java, types[3])
        assertEquals(String::class.java, types[4])
        assertEquals(Integer::class.java, types[5])
        assertEquals(String::class.java, types[6])
        assertEquals(Long::class.javaObjectType, types[7])
    }

    @Test
    fun episodeHasKotlinDefaultArgumentsConstructor() {
        val constructor = Episode::class.java.declaredConstructors
            .firstOrNull {
                it.parameterCount == 10 &&
                    it.parameterTypes[8] == Integer.TYPE &&
                    it.parameterTypes[9].name == "kotlin.jvm.internal.DefaultConstructorMarker"
            }
        assertNotNull(
            "Missing Episode synthetic constructor required by Kotlin plugin defaults",
            constructor
        )
    }

    @Test
    fun loadResponseExposesKotlinCompanionField() {
        assertNotNull(LoadResponse::class.java.getField("Companion"))
    }

    @Test
    fun loadResponseCompanionExposesAddDurationJvmMethod() {
        val companion = LoadResponse::class.java.getField("Companion").get(null)
        val method = companion.javaClass.getMethod(
            "addDuration",
            LoadResponse::class.java,
            String::class.java
        )
        assertNotNull(method)
    }
}
