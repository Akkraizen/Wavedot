package space.wavedot.server.hub.data

import kotlinx.coroutines.test.runTest
import space.wavedot.server.hub.domain.account.AuthenticateDevice
import space.wavedot.server.hub.domain.account.CreateAccount
import space.wavedot.server.hub.domain.sync.Change
import space.wavedot.server.hub.domain.sync.CollectionRegistry
import space.wavedot.server.hub.domain.sync.PullChanges
import space.wavedot.server.hub.domain.sync.PushChanges
import space.wavedot.server.kernel.UuidGenerator
import space.wavedot.server.persistence.ExposedTransactionRunner
import space.wavedot.server.testing.MutableClock
import space.wavedot.server.testing.TestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExposedHubRepositoriesTest {
    private val db = TestDatabase("classpath:db/migration/hub")
    private val accounts = ExposedAccountRepository(db.database)
    private val devices = ExposedDeviceRepository(db.database)
    private val library = ExposedLibraryRepository(db.database)
    private val tokens = Sha256TokenService()
    private val transactions = ExposedTransactionRunner(db.database)
    private val clock = MutableClock()

    @Test
    fun fullSyncRoundTripAgainstDatabase() = runTest {
        val registered = CreateAccount(accounts, devices, tokens, UuidGenerator, transactions, clock)("Desk", "windows")
        val session = assertNotNull(AuthenticateDevice(devices, tokens, clock)(registered.token))
        val push = PushChanges(accounts, library, CollectionRegistry.default(), transactions, { _, _ -> })

        push(session, listOf(Change("favorites", "ym:1", false, "{}", 10), Change("favorites", "ym:2", false, "{}", 10)))
        val second = push(session, listOf(Change("favorites", "ym:1", true, null, 5), Change("favorites", "ym:2", true, null, 20)))
        assertEquals(1, second.rejected.size)

        val pulled = PullChanges(library)(session, 0)
        assertEquals(listOf("ym:1" to false, "ym:2" to true), pulled.changes.map { it.change.key to it.change.deleted })
        assertEquals(3L, pulled.revision)
        assertTrue(!pulled.hasMore)
    }

    @Test
    fun tokensAreStoredHashed() = runTest {
        val registered = CreateAccount(accounts, devices, tokens, UuidGenerator, transactions, clock)("Phone", "android")
        assertEquals(null, devices.findByTokenHash(registered.token))
        assertNotNull(devices.findByTokenHash(tokens.hash(registered.token)))
    }
}
