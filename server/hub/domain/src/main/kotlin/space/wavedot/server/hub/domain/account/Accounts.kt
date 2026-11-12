package space.wavedot.server.hub.domain.account

import java.time.Instant

@JvmInline
value class AccountId(val value: String)

@JvmInline
value class DeviceId(val value: String)

data class Account(
    val id: AccountId,
    val createdAt: Instant,
)

data class Device(
    val id: DeviceId,
    val accountId: AccountId,
    val name: String,
    val platform: String,
    val tokenHash: String,
    val createdAt: Instant,
    val lastSeenAt: Instant,
)

data class DeviceSession(
    val accountId: AccountId,
    val deviceId: DeviceId,
)

interface AccountRepository {
    suspend fun create(account: Account)

    suspend fun lockRevision(id: AccountId): Long

    suspend fun saveRevision(id: AccountId, revision: Long)

    suspend fun delete(id: AccountId)
}

interface DeviceRepository {
    suspend fun create(device: Device)

    suspend fun findByTokenHash(tokenHash: String): Device?

    suspend fun listByAccount(accountId: AccountId): List<Device>

    suspend fun touch(id: DeviceId, at: Instant)

    suspend fun delete(id: DeviceId)
}

interface TokenService {
    fun generate(): String

    fun hash(token: String): String
}
