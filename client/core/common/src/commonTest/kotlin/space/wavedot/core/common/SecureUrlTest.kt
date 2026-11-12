package space.wavedot.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SecureUrlTest {
    @Test
    fun acceptsHttpsAndLoopbackHttp() {
        assertTrue(SecureUrl.isAcceptable("https://hub.example.com"))
        assertTrue(SecureUrl.isAcceptable(" HTTPS://hub.example.com/path "))
        assertTrue(SecureUrl.isAcceptable("http://localhost:8081"))
        assertTrue(SecureUrl.isAcceptable("http://127.0.0.1:8081/x"))
        assertTrue(SecureUrl.isAcceptable("http://[::1]:8081"))
    }

    @Test
    fun rejectsPlainHttpAndOtherSchemes() {
        assertFalse(SecureUrl.isAcceptable("http://hub.example.com"))
        assertFalse(SecureUrl.isAcceptable("http://localhost.evil.test"))
        assertFalse(SecureUrl.isAcceptable("http://127.0.0.1@evil.test/"))
        assertFalse(SecureUrl.isAcceptable("http://evil.test/?http://localhost"))
        assertFalse(SecureUrl.isAcceptable("ftp://localhost"))
        assertFalse(SecureUrl.isAcceptable("hub.example.com"))
        assertFalse(SecureUrl.isAcceptable("https://"))
    }

    @Test
    fun requireTrimsOrFailsWithInvalid() {
        assertEquals("https://hub.test", SecureUrl.require(" https://hub.test ", "Hub URL"))
        val error = assertFailsWith<WavedotException> { SecureUrl.require("http://hub.test", "Hub URL") }
        assertIs<WavedotError.Invalid>(error.error)
    }
}
