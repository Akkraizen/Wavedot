package space.wavedot.server.updates.app

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
import space.wavedot.server.kernel.IdGenerator
import space.wavedot.server.kernel.UuidGenerator
import space.wavedot.server.ktor.ReadinessCheck
import space.wavedot.server.ktor.installServiceDefaults
import space.wavedot.server.persistence.createDataSource
import space.wavedot.server.persistence.isReachable
import space.wavedot.server.persistence.migrate
import space.wavedot.server.updates.api.UpdatesUseCases
import space.wavedot.server.updates.api.updatesApi
import space.wavedot.server.updates.data.ExposedReleaseRepository
import space.wavedot.server.updates.data.FileSystemArtifactStorage
import space.wavedot.server.updates.domain.ArtifactStorage
import space.wavedot.server.updates.domain.PublishRelease
import space.wavedot.server.updates.domain.ReleaseRepository
import space.wavedot.server.updates.domain.ResolveLatest
import space.wavedot.server.updates.domain.YankRelease
import java.nio.file.Path
import java.time.Clock

data class StorageConfig(val root: String)

data class AdminConfig(val token: String = "")

data class UpdatesConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val storage: StorageConfig,
    val admin: AdminConfig,
)

fun updatesModule(config: UpdatesConfig, database: Database) = module {
    single { database }
    single<Clock> { Clock.systemUTC() }
    single<IdGenerator> { UuidGenerator }
    single<ReleaseRepository> { ExposedReleaseRepository(get()) }
    single { FileSystemArtifactStorage(Path.of(config.storage.root)) }
    single<ArtifactStorage> { get<FileSystemArtifactStorage>() }
    single { ResolveLatest(get()) }
    single { PublishRelease(get(), get(), get(), get()) }
    single { YankRelease(get()) }
    single {
        val storage = get<FileSystemArtifactStorage>()
        UpdatesUseCases(get(), get(), get(), get(), { storage.resolve(it).toFile() }, config.admin.token)
    }
}

fun main() {
    val config = loadServiceConfig<UpdatesConfig>("/updates.yaml")
    val dataSource = createDataSource(config.database)
    migrate(dataSource, "classpath:db/migration/updates")
    val database = Database.connect(dataSource)
    embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        install(Koin) { modules(updatesModule(config, database)) }
        installServiceDefaults("wavedot-updates", ReadinessCheck { dataSource.isReachable() })
        updatesApi(get())
    }.start(wait = true)
}
