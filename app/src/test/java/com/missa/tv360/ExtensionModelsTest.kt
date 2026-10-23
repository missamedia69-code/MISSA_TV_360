package com.missa.tv360

import com.missa.tv360.extensions.PluginEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionModelsTest {

    @Test
    fun `manifeste Cloudstream sans apiVersion reste installable`() {
        val entry = PluginEntry(internalName = "French", apiVersion = null, fileHash = null)

        assertTrue(entry.isCompatible)
        assertTrue(entry.hashOrNull == null)
    }

    @Test
    fun `API native version 1 reste acceptee`() {
        assertTrue(PluginEntry(apiVersion = 1).isCompatible)
    }

    @Test
    fun `API future inconnue est refusee`() {
        assertFalse(PluginEntry(apiVersion = 2).isCompatible)
    }
}
