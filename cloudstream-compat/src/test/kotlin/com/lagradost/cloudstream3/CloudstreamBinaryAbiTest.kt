package com.lagradost.cloudstream3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression tests for ABI signatures observed in real .cs3 extensions.
 *
 * These tests intentionally verify JVM constructors/fields, not just Kotlin
 * source-level properties: plugins are precompiled and link against descriptors.
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
    fun loadResponseExposesKotlinCompanionField() {
        assertNotNull(LoadResponse::class.java.getField("Companion"))
    }
}
