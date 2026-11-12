package space.wavedot.core.domain.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckForUpdatesTest {
    private fun offer(version: String) = UpdateChecker { AvailableUpdate(version, "", false, "https://u.test/a", "00", 1) }
    private val app = AppInfo("1.2.0", installId = "i")

    @Test
    fun ordersVersionsLikeSemVer() {
        assertTrue(VersionOrder.isNewer("1.2.1", than = "1.2.0"))
        assertTrue(VersionOrder.isNewer("1.10.0", than = "1.9.9"))
        assertTrue(VersionOrder.isNewer("1.2.0", than = "1.2.0-rc.1"))
        assertTrue(VersionOrder.isNewer("1.2.0-beta.11", than = "1.2.0-beta.2"))
        assertTrue(VersionOrder.isNewer("v2.0.0+build.5", than = "1.9.0"))
        assertFalse(VersionOrder.isNewer("1.2.0", than = "1.2.0"))
        assertFalse(VersionOrder.isNewer("1.2.0-rc.1", than = "1.2.0"))
        assertFalse(VersionOrder.isNewer("not-a-version", than = "1.2.0"))
        assertFalse(VersionOrder.isNewer("99999999999.0.0", than = "1.2.0"))
    }

    @Test
    fun ignoresOffersThatAreNotNewer() = runTest {
        assertEquals("1.3.0", CheckForUpdates(offer("1.3.0"), app)()?.version)
        assertNull(CheckForUpdates(offer("1.2.0"), app)())
        assertNull(CheckForUpdates(offer("1.1.9"), app)())
        assertNull(CheckForUpdates(offer("garbage"), app)())
    }
}
