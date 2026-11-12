package space.wavedot.server.hub.app

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import space.wavedot.server.config.DatabaseConfig
import space.wavedot.server.config.ServerConfig
import space.wavedot.server.config.loadServiceConfig
import space.wavedot.server.hub.api.HubUseCases
import space.wavedot.server.hub.api.hubApi
import space.wavedot.server.hub.data.ExposedAccountRepository
import space.wavedot.server.hub.data.ExposedDeviceRepository
import space.wavedot.server.hub.data.ExposedLibraryRepository
import space.wavedot.server.hub.data.InMemoryRevisionFeed
import space.wavedot.server.hub.data.Sha256TokenService
import space.wavedot.server.hub.domain.account.AccountRepository
import space.wavedot.server.hub.domain.account.AuthenticateDevice
import space.wavedot.server.hub.domain.account.CreateAccount
import space.wavedot.server.hub.domain.account.DeleteAccount
import space.wavedot.server.hub.domain.account.DeviceRepository
import space.wavedot.server.hub.domain.account.ListDevices
import space.wavedot.server.hub.domain.account.RegistrationMode
import space.wavedot.server.hub.domain.account.RegistrationPolicy
import space.wavedot.server.hub.domain.account.RevokeDevice
import space.wavedot.server.hub.domain.account.TokenService
import space.wavedot.server.hub.domain.sync.ChangeNotifier
import space.wavedot.server.hub.domain.sync.CollectionRegistry
import space.wavedot.server.hub.domain.sync.LibraryRepository
import space.wavedot.server.hub.domain.sync.PullChanges
import space.wavedot.server.hub.domain.sync.PushChanges
import space.wavedot.server.hub.domain.sync.RevisionFeed
import space.wavedot.server.hub.domain.sync.SyncLimits
import space.wavedot.server.kernel.IdGenerator
import space.wavedot.server.kernel.TransactionRunner
import space.wavedot.server.kernel.UuidGenerator
import space.wavedot.server.ktor.ReadinessCheck
import space.wavedot.server.ktor.installServiceDefaults
import space.wavedot.server.persistence.ExposedTransactionRunner
import space.wavedot.server.persistence.createDataSource
import space.wavedot.server.persistence.isReachable
import space.wavedot.server.persistence.migrate
import java.time.Clock

data class RegistrationConfig(
    val mode: RegistrationMode = RegistrationMode.TOKEN,
    val token: String = "",
)

data class HubConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val registration: RegistrationConfig = RegistrationConfig(),
    val limits: SyncLimits = SyncLimits(),
)

fun hubModule(config: HubConfig, database: Database) = module {
    single { database }
    single<Clock> { Clock.systemUTC() }
    single<IdGenerator> { UuidGenerator }
    single<TransactionRunner> { ExposedTransactionRunner(get()) }
    single<AccountRepository> { ExposedAccountRepository(get()) }
    single<DeviceRepository> { ExposedDeviceRepository(get()) }
    single<LibraryRepository> { ExposedLibraryRepository(get()) }
    single<TokenService> { Sha256TokenService() }
    single { InMemoryRevisionFeed() }
    single<ChangeNotifier> { get<InMemoryRevisionFeed>() }
    single<RevisionFeed> { get<InMemoryRevisionFeed>() }
    single { config.limits }
    single { RegistrationPolicy(config.registration.mode, config.registration.token) }
    single { CollectionRegistry.default(get()) }
    single { CreateAccount(get(), get(), get(), get(), get(), get(), get()) }
    single { AuthenticateDevice(get(), get(), get()) }
    single { PushChanges(get(), get(), get(), get(), get(), get(), get()) }
    single { PullChanges(get(), get<SyncLimits>().maxBatch) }
    single { ListDevices(get()) }
    single { RevokeDevice(get()) }
    single { DeleteAccount(get()) }
    single { HubUseCases(get(), get(), get(), get(), get(), get(), get(), get()) }
}

fun main() {
    val config = loadServiceConfig<HubConfig>("/hub.yaml")
    val dataSource = createDataSource(config.database)
    migrate(dataSource, "classpath:db/migration/hub")
    val database = Database.connect(dataSource)
    embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        install(Koin) { modules(hubModule(config, database)) }
        installServiceDefaults("wavedot-hub", ReadinessCheck { dataSource.isReachable() }, config.server.trustForwardedHeaders)
        hubApi(get())
    }.start(wait = true)
}
