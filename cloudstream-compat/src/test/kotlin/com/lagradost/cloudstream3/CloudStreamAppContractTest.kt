package com.lagradost.cloudstream3

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudStreamAppContractTest {
    @Test
    fun `companion expose le getter de contexte attendu par le DEX`() {
        val companionField = CloudStreamApp::class.java.getField("Companion")
        val companion = companionField.get(null)

        val getter = companion.javaClass.getMethod("getContext")

        assertEquals(Context::class.java, getter.returnType)
    }
}
