package space.wavedot.server.hub.domain.account

import space.wavedot.server.kernel.IdGenerator
import space.wavedot.server.kernel.TransactionRunner
import space.wavedot.server.kernel.validate
import java.time.Clock

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
) {
    suspend operator fun invoke(deviceName: String, platform: String): RegisteredDevice {
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
) {
    suspend operator fun invoke(token: String): DeviceSession? {
        if (token.isBlank()) return null
        val device = devices.findByTokenHash(tokens.hash(token)) ?: return null
        devices.touch(device.id, clock.instant())
        return DeviceSession(device.accountId, device.id)
    }
}
