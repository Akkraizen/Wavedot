package space.wavedot.server.hub.domain

import space.wavedot.server.hub.domain.account.Account
import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.account.AccountRepository
import space.wavedot.server.hub.domain.account.Device
import space.wavedot.server.hub.domain.account.DeviceId
import space.wavedot.server.hub.domain.account.DeviceRepository
import space.wavedot.server.hub.domain.account.TokenService
import space.wavedot.server.hub.domain.sync.LibraryRepository
import space.wavedot.server.hub.domain.sync.StoredChange
import java.time.Instant

class InMemoryHub : AccountRepository, DeviceRepository, LibraryRepository, TokenService {
    val accounts = mutableMapOf<AccountId, Pair<Account, Long>>()
    val devices = mutableMapOf<DeviceId, Device>()
    private val items = mutableMapOf<Triple<AccountId, String, String>, StoredChange>()
    private var tokenCounter = 0
    var touches = 0

    override suspend fun create(account: Account) {
        accounts[account.id] = account to 0L
    }

    override suspend fun lockRevision(id: AccountId): Long = accounts.getValue(id).second

    override suspend fun saveRevision(id: AccountId, revision: Long) {
        accounts[id] = accounts.getValue(id).first to revision
    }

    override suspend fun delete(id: AccountId) {
        accounts.remove(id)
        devices.values.removeAll { it.accountId == id }
        items.keys.removeAll { it.first == id }
    }

    override suspend fun create(device: Device) {
        devices[device.id] = device
    }

    override suspend fun findByTokenHash(tokenHash: String): Device? = devices.values.firstOrNull { it.tokenHash == tokenHash }

    override suspend fun listByAccount(accountId: AccountId): List<Device> = devices.values.filter { it.accountId == accountId }

    override suspend fun delete(id: DeviceId) {
        devices.remove(id)
    }

    override suspend fun count(accountId: AccountId): Long = items.keys.count { it.first == accountId }.toLong()

    override suspend fun touch(id: DeviceId, at: Instant) {
        touches++
        devices[id] = devices.getValue(id).copy(lastSeenAt = at)
    }

    override suspend fun find(accountId: AccountId, collection: String, key: String): StoredChange? =
        items[Triple(accountId, collection, key)]

    override suspend fun upsert(accountId: AccountId, stored: StoredChange) {
        items[Triple(accountId, stored.change.collection, stored.change.key)] = stored
    }

    override suspend fun changesSince(accountId: AccountId, revision: Long, limit: Int): List<StoredChange> =
        items.filterKeys { it.first == accountId }.values.filter { it.revision > revision }.sortedBy { it.revision }.take(limit)

    override fun generate(): String = "token-${++tokenCounter}"

    override fun hash(token: String): String = "hash:$token"
}
