package space.wavedot.server.hub.domain

import kotlinx.coroutines.test.runTest
import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.account.AuthenticateDevice
import space.wavedot.server.hub.domain.account.CreateAccount
import space.wavedot.server.hub.domain.account.DeleteAccount
import space.wavedot.server.hub.domain.account.ListDevices
import space.wavedot.server.hub.domain.account.RegistrationMode
import space.wavedot.server.hub.domain.account.RegistrationPolicy
import space.wavedot.server.hub.domain.account.RevokeDevice
import space.wavedot.server.hub.domain.sync.Change
import space.wavedot.server.hub.domain.sync.CollectionRegistry
import space.wavedot.server.hub.domain.sync.PullChanges
import space.wavedot.server.hub.domain.sync.PushChanges
import space.wavedot.server.hub.domain.sync.SyncLimits
import space.wavedot.server.kernel.AppError
import space.wavedot.server.kernel.DirectTransactionRunner
import java.time.Clock
import java.time.Duration
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
    private val start = Instant.parse("2026-11-05T00:00:00Z")
    private var now = start
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private var ids = 0
    private val createAccount = CreateAccount(hub, hub, hub, { "id-${++ids}" }, DirectTransactionRunner, clock)
    private val authenticate = AuthenticateDevice(hub, hub, clock)
    private val notified = mutableListOf<Pair<AccountId, Long>>()
    private val push = PushChanges(hub, hub, CollectionRegistry.default(), DirectTransactionRunner, { a, r -> notified += a to r }, clock)
    private val pull = PullChanges(hub, maxLimit = 2)

    private fun favorite(key: String, at: Long, deleted: Boolean = false) =
        Change("favorites", "test:$key", deleted, if (deleted) null else """{"title":"$key"}""", at)

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
        assertEquals("test:a", second.rejected.single().change.key)
        assertEquals(3L, second.revision)
        assertEquals(session.accountId to 3L, notified.last())
    }

    @Test
    fun rejectsUnknownCollectionsAndInvalidPayloads() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        val result = push(session, listOf(Change("nope", "k", false, "{}", 1), Change("favorites", "test:k", false, null, 1)))
        assertEquals(2, result.rejected.size)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun pullsCompactedChangesInPages() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        push(session, listOf(favorite("a", 1), favorite("b", 1), favorite("c", 1)))
        push(session, listOf(favorite("a", 2, deleted = true)))

        val first = pull(session, 0)
        assertEquals(listOf("test:b", "test:c"), first.changes.map { it.change.key })
        assertTrue(first.hasMore)
        val second = pull(session, first.revision)
        assertEquals(listOf("test:a"), second.changes.map { it.change.key })
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

    @Test
    fun rejectsMalformedKeysAndPayloads() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        val result = push(
            session,
            listOf(
                Change("favorites", "no-separator", false, "{}", 1),
                Change("favorites", "test:1", false, "[1]", 1),
                Change("favorites", "test:2", false, "{\"t\":\"${"я".repeat(9000)}\"}", 1),
                Change("settings", "theme", false, "{}", 1),
            ),
        )
        assertEquals(listOf("no-separator", "test:1", "test:2"), result.rejected.map { it.change.key })
        assertEquals(1L, result.revision)
    }

    @Test
    fun rejectsTimestampsFarInTheFuture() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        val tomorrow = start.plus(Duration.ofHours(23)).toEpochMilli()
        val nextWeek = start.plus(Duration.ofDays(7)).toEpochMilli()
        val result = push(session, listOf(favorite("a", tomorrow), favorite("b", nextWeek), favorite("c", -1)))
        assertEquals(listOf("test:b", "test:c"), result.rejected.map { it.change.key })
    }

    @Test
    fun enforcesTheAccountItemLimit() = runTest {
        val limited = PushChanges(hub, hub, CollectionRegistry.default(), DirectTransactionRunner, { _, _ -> }, clock, SyncLimits(maxItemsPerAccount = 2))
        val session = authenticate(createAccount("Desk", "windows").token)!!
        val result = limited(session, listOf(favorite("a", 1), favorite("b", 1), favorite("c", 1), favorite("a", 2)))
        assertEquals(listOf("test:c"), result.rejected.map { it.change.key })
        assertEquals(3L, result.revision)
    }

    @Test
    fun reportsTheCurrentRevisionWhenNothingApplies() = runTest {
        val session = authenticate(createAccount("Desk", "windows").token)!!
        push(session, listOf(favorite("a", 100)))
        val result = push(session, listOf(favorite("a", 50)))
        assertEquals(1L, result.revision)
        assertEquals(1, notified.size)
    }

    @Test
    fun registrationFollowsThePolicy() = runTest {
        fun create(policy: RegistrationPolicy) = CreateAccount(hub, hub, hub, { "id-${++ids}" }, DirectTransactionRunner, clock, policy)
        val byToken = create(RegistrationPolicy(RegistrationMode.TOKEN, "invite"))
        assertFailsWith<AppError.Forbidden> { byToken("Desk", "windows") }
        assertFailsWith<AppError.Forbidden> { byToken("Desk", "windows", "wrong") }
        assertNotNull(byToken("Desk", "windows", "invite"))
        assertFailsWith<AppError.Forbidden> { create(RegistrationPolicy(RegistrationMode.TOKEN, ""))("Desk", "windows", "") }
        assertFailsWith<AppError.Forbidden> { create(RegistrationPolicy(RegistrationMode.CLOSED, "invite"))("Desk", "windows", "invite") }
    }

    @Test
    fun revokedDevicesAndDeletedAccountsLoseAccess() = runTest {
        val first = createAccount("Desk", "windows")
        val session = authenticate(first.token)!!
        val stranger = authenticate(createAccount("Other", "android").token)!!

        assertEquals(listOf("Desk"), ListDevices(hub)(session).map { it.name })
        assertFailsWith<AppError.NotFound> { RevokeDevice(hub)(stranger, session.deviceId) }
        assertNotNull(authenticate(first.token))

        RevokeDevice(hub)(session, session.deviceId)
        assertNull(authenticate(first.token))

        push(stranger, listOf(favorite("x", 1)))
        DeleteAccount(hub)(stranger)
        assertTrue(pull(stranger, 0).changes.isEmpty())
        assertTrue(stranger.accountId !in hub.accounts)
    }

    @Test
    fun lastSeenIsRefreshedAtMostOncePerInterval() = runTest {
        val token = createAccount("Desk", "windows").token
        repeat(3) { authenticate(token) }
        assertEquals(0, hub.touches)
        now = now.plus(Duration.ofMinutes(6))
        repeat(3) { authenticate(token) }
        assertEquals(1, hub.touches)
    }
}
