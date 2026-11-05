package space.wavedot.server.hub.domain

import kotlinx.coroutines.test.runTest
import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.account.AuthenticateDevice
import space.wavedot.server.hub.domain.account.CreateAccount
import space.wavedot.server.hub.domain.sync.Change
import space.wavedot.server.hub.domain.sync.CollectionRegistry
import space.wavedot.server.hub.domain.sync.PullChanges
import space.wavedot.server.hub.domain.sync.PushChanges
import space.wavedot.server.kernel.AppError
import space.wavedot.server.kernel.DirectTransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncUseCasesTest {
    private val hub = InMemoryHub()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC)
    private var ids = 0
    private val createAccount = CreateAccount(hub, hub, hub, { "id-${++ids}" }, DirectTransactionRunner, clock)
    private val authenticate = AuthenticateDevice(hub, hub, clock)
    private val notified = mutableListOf<Pair<AccountId, Long>>()
    private val push = PushChanges(hub, hub, CollectionRegistry.default(), DirectTransactionRunner, { a, r -> notified += a to r })
    private val pull = PullChanges(hub, maxLimit = 2)

    private fun favorite(key: String, at: Long, deleted: Boolean = false) =
        Change("favorites", key, deleted, if (deleted) null else """{"title":"$key"}""", at)

    @Test
    fun registeredDeviceAuthenticatesWithItsToken() = runTest {
        val registered = createAccount("Desk", "windows")
        val session = assertNotNull(authenticate(registered.token))
        assertEquals(registered.accountId, session.accountId)
        assertNull(authenticate("forged"))
    }

    @Test
    fun lastWriterWinsPerKey() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        val first = push(session, listOf(favorite("a", 100), favorite("b", 100)))
        assertEquals(2L, first.revision)
        val second = push(session, listOf(favorite("a", 50, deleted = true), favorite("b", 200, deleted = true)))
        assertEquals(1, second.rejected.size)
        assertEquals("a", second.rejected.single().change.key)
        assertEquals(3L, second.revision)
        assertEquals(session.accountId to 3L, notified.last())
    }

    @Test
    fun rejectsUnknownCollectionsAndInvalidPayloads() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        val result = push(session, listOf(Change("nope", "k", false, "{}", 1), Change("favorites", "k", false, null, 1)))
        assertEquals(2, result.rejected.size)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun pullsCompactedChangesInPages() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        push(session, listOf(favorite("a", 1), favorite("b", 1), favorite("c", 1)))
        push(session, listOf(favorite("a", 2, deleted = true)))

        val first = pull(session, 0)
        assertEquals(listOf("b", "c"), first.changes.map { it.change.key })
        assertTrue(first.hasMore)
        val second = pull(session, first.revision)
        assertEquals(listOf("a"), second.changes.map { it.change.key })
        assertTrue(second.changes.single().change.deleted)
        assertEquals(false, second.hasMore)
    }

    @Test
    fun accountsAreIsolated() = runTest {
        val alice = authenticate(createAccount("A", "android").token)!!
        val bob = authenticate(createAccount("B", "android").token)!!
        push(alice, listOf(favorite("x", 1)))
        assertTrue(pull(bob, 0).changes.isEmpty())
    }

    @Test
    fun validatesInput() = runTest {
        assertFailsWith<AppError.Validation> { createAccount(" ", "windows") }
        val session = authenticate(createAccount("Desk", "windows").token)!!
        assertFailsWith<AppError.Validation> { pull(session, -1) }
    }
}
