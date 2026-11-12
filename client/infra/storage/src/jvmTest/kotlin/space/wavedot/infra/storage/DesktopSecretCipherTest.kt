package space.wavedot.infra.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopSecretCipherTest {
    @Test
    fun usesDataProtectionOnWindowsOnly() {
        assertNotNull(DesktopSecretCipher.forCurrentSystem("Windows 11"))
        assertNull(DesktopSecretCipher.forCurrentSystem("Linux"))
        assertNull(DesktopSecretCipher.forCurrentSystem("Mac OS X"))
    }

    @Test
    fun roundTripsThroughTheSystemStoreWhereAvailable() {
        val cipher = DesktopSecretCipher.forCurrentSystem() ?: return
        val secret = "hub-token".encodeToByteArray()
        val sealed = cipher.encrypt(secret)
        assertFalse(sealed.contentEquals(secret))
        assertContentEquals(secret, cipher.decrypt(sealed))
    }
}
