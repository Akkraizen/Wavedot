package space.wavedot.server.hub.domain.account

import space.wavedot.server.kernel.AppError
import space.wavedot.server.kernel.IdGenerator
import space.wavedot.server.kernel.TransactionRunner
import space.wavedot.server.kernel.validate
import java.time.Clock
import java.time.Duration

data class RegisteredDevice(
    val accountId: AccountId,
    val deviceId: DeviceId,
    val token: String,
)

class CreateAccount(
    private val accounts: AccountRepository,
    private val devices: DeviceRepository,
    private val tokens: TokenService,
    private val ids: IdGenerator,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val registration: RegistrationPolicy = RegistrationPolicy.OPEN,
) {
    suspend operator fun invoke(deviceName: String, platform: String, registrationToken: String? = null): RegisteredDevice {
        registration.check(registrationToken)
        validate(deviceName.isNotBlank() && deviceName.length <= 120) { "Device name must be 1..120 characters" }
        val now = clock.instant()
        val token = tokens.generate()
        val account = Account(AccountId(ids.newId()), now)
        val device = Device(DeviceId(ids.newId()), account.id, deviceName.trim(), platform, tokens.hash(token), now, now)
        transactions.inTransaction {
            accounts.create(account)
            devices.create(device)
        }
        return RegisteredDevice(account.id, device.id, token)
    }
}

class AuthenticateDevice(
    private val devices: DeviceRepository,
    private val tokens: TokenService,
    private val clock: Clock,
    private val touchInterval: Duration = Duration.ofMinutes(5),
) {
    suspend operator fun invoke(token: String): DeviceSession? {
        if (token.isBlank()) return null
        val device = devices.findByTokenHash(tokens.hash(token)) ?: return null
        val now = clock.instant()
        if (Duration.between(device.lastSeenAt, now) >= touchInterval) devices.touch(device.id, now)
        return DeviceSession(device.accountId, device.id)
    }
}

class ListDevices(private val devices: DeviceRepository) {
    suspend operator fun invoke(session: DeviceSession): List<Device> =
        devices.listByAccount(session.accountId).sortedBy { it.createdAt }
}

class RevokeDevice(private val devices: DeviceRepository) {
    suspend operator fun invoke(session: DeviceSession, deviceId: DeviceId) {
        val owned = devices.listByAccount(session.accountId).any { it.id == deviceId }
        if (!owned) throw AppError.NotFound("Device")
        devices.delete(deviceId)
    }
}

class DeleteAccount(private val accounts: AccountRepository) {
    suspend operator fun invoke(session: DeviceSession) = accounts.delete(session.accountId)
}
