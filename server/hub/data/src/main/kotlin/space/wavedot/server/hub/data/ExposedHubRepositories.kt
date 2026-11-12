package space.wavedot.server.hub.data

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import space.wavedot.server.hub.domain.account.Account
import space.wavedot.server.hub.domain.account.AccountId
import space.wavedot.server.hub.domain.account.AccountRepository
import space.wavedot.server.hub.domain.account.Device
import space.wavedot.server.hub.domain.account.DeviceId
import space.wavedot.server.hub.domain.account.DeviceRepository
import space.wavedot.server.hub.domain.sync.Change
import space.wavedot.server.hub.domain.sync.LibraryRepository
import space.wavedot.server.hub.domain.sync.StoredChange
import space.wavedot.server.kernel.AppError
import space.wavedot.server.persistence.dbQuery
import java.time.Instant

class ExposedAccountRepository(private val database: Database) : AccountRepository {
    override suspend fun create(account: Account) = database.dbQuery {
        AccountsTable.insert {
            it[id] = account.id.value
            it[createdAt] = account.createdAt.toEpochMilli()
            it[revision] = 0
        }
        Unit
    }

    override suspend fun lockRevision(id: AccountId): Long = database.dbQuery {
        AccountsTable.selectAll().where { AccountsTable.id eq id.value }.forUpdate()
            .singleOrNull()?.get(AccountsTable.revision) ?: throw AppError.NotFound("Account")
    }

    override suspend fun saveRevision(id: AccountId, revision: Long) = database.dbQuery {
        AccountsTable.update({ AccountsTable.id eq id.value }) { it[AccountsTable.revision] = revision }
        Unit
    }

    override suspend fun delete(id: AccountId) = database.dbQuery {
        LibraryItemsTable.deleteWhere { accountId eq id.value }
        DevicesTable.deleteWhere { accountId eq id.value }
        AccountsTable.deleteWhere { AccountsTable.id eq id.value }
        Unit
    }
}

class ExposedDeviceRepository(private val database: Database) : DeviceRepository {
    override suspend fun create(device: Device) = database.dbQuery {
        DevicesTable.insert {
            it[id] = device.id.value
            it[accountId] = device.accountId.value
            it[name] = device.name
            it[platform] = device.platform
            it[tokenHash] = device.tokenHash
            it[createdAt] = device.createdAt.toEpochMilli()
            it[lastSeenAt] = device.lastSeenAt.toEpochMilli()
        }
        Unit
    }

    override suspend fun findByTokenHash(tokenHash: String): Device? = database.dbQuery {
        DevicesTable.selectAll().where { DevicesTable.tokenHash eq tokenHash }.singleOrNull()?.toDevice()
    }

    override suspend fun listByAccount(accountId: AccountId): List<Device> = database.dbQuery {
        DevicesTable.selectAll().where { DevicesTable.accountId eq accountId.value }.map { it.toDevice() }
    }

    override suspend fun delete(id: DeviceId) = database.dbQuery {
        DevicesTable.deleteWhere { DevicesTable.id eq id.value }
        Unit
    }

    override suspend fun touch(id: DeviceId, at: Instant) = database.dbQuery {
        DevicesTable.update({ DevicesTable.id eq id.value }) { it[lastSeenAt] = at.toEpochMilli() }
        Unit
    }

    private fun ResultRow.toDevice() = Device(
        id = DeviceId(this[DevicesTable.id]),
        accountId = AccountId(this[DevicesTable.accountId]),
        name = this[DevicesTable.name],
        platform = this[DevicesTable.platform],
        tokenHash = this[DevicesTable.tokenHash],
        createdAt = Instant.ofEpochMilli(this[DevicesTable.createdAt]),
        lastSeenAt = Instant.ofEpochMilli(this[DevicesTable.lastSeenAt]),
    )
}

class ExposedLibraryRepository(private val database: Database) : LibraryRepository {
    override suspend fun find(accountId: AccountId, collection: String, key: String): StoredChange? = database.dbQuery {
        LibraryItemsTable.selectAll()
            .where {
                (LibraryItemsTable.accountId eq accountId.value) and
                    (LibraryItemsTable.collection eq collection) and
                    (LibraryItemsTable.itemKey eq key)
            }
            .singleOrNull()
            ?.toStored()
    }

    override suspend fun upsert(accountId: AccountId, stored: StoredChange) = database.dbQuery {
        LibraryItemsTable.upsert {
            it[LibraryItemsTable.accountId] = accountId.value
            it[collection] = stored.change.collection
            it[itemKey] = stored.change.key
            it[revision] = stored.revision
            it[deviceId] = stored.deviceId.value
            it[deleted] = stored.change.deleted
            it[payload] = stored.change.payload
            it[updatedAt] = stored.change.updatedAt
        }
        Unit
    }

    override suspend fun changesSince(accountId: AccountId, revision: Long, limit: Int): List<StoredChange> = database.dbQuery {
        LibraryItemsTable.selectAll()
            .where { (LibraryItemsTable.accountId eq accountId.value) and (LibraryItemsTable.revision greater revision) }
            .orderBy(LibraryItemsTable.revision to SortOrder.ASC)
            .limit(limit)
            .map { it.toStored() }
    }

    override suspend fun count(accountId: AccountId): Long = database.dbQuery {
        LibraryItemsTable.selectAll().where { LibraryItemsTable.accountId eq accountId.value }.count()
    }

    private fun ResultRow.toStored() = StoredChange(
        revision = this[LibraryItemsTable.revision],
        deviceId = DeviceId(this[LibraryItemsTable.deviceId]),
        change = Change(
            collection = this[LibraryItemsTable.collection],
            key = this[LibraryItemsTable.itemKey],
            deleted = this[LibraryItemsTable.deleted],
            payload = this[LibraryItemsTable.payload],
            updatedAt = this[LibraryItemsTable.updatedAt],
        ),
    )
}
