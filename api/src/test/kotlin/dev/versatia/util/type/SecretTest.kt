package dev.versatia.util.type

import kotlin.test.Test
import kotlin.test.assertEquals

class SecretTest {
    private data class Login(val user: String, val password: Secret)

    @Test
    fun `prints masked and reveals on request`() {
        val secret = Secret("hunter2")
        assertEquals("hunter2", secret.reveal())
        assertEquals("****", secret.toString())
        assertEquals("password ****", "password $secret")
        assertEquals("Login(user=shop, password=****)", Login("shop", secret).toString())
        assertEquals("[****]", listOf(secret).toString())
    }

    @Test
    fun `an empty secret prints as empty quotes`() {
        assertEquals("''", Secret.EMPTY.toString())
        assertEquals(Secret(""), Secret.EMPTY)
        assertEquals("", Secret.EMPTY.reveal())
        assertEquals(listOf(true, false), listOf(Secret.EMPTY.isEmpty(), Secret("x").isEmpty()))
    }
}
