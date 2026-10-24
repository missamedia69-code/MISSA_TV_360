package com.missa.tv360.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinRuntimeContractTest {
    @Test
    fun `runtime Kotlin expose la fonction de spillage attendue par le DEX`() {
        val spilling = Class.forName("kotlin.coroutines.jvm.internal.SpillingKt")

        val method = spilling.getDeclaredMethod("nullOutSpilledVariable", Any::class.java)

        assertEquals(Any::class.java, method.returnType)
    }
}
