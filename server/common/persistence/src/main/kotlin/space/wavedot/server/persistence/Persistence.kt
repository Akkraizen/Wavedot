package space.wavedot.server.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import space.wavedot.server.config.DatabaseConfig
import space.wavedot.server.kernel.TransactionRunner
import javax.sql.DataSource

fun createDataSource(config: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = config.maxPoolSize
        isAutoCommit = false
    },
)

fun migrate(dataSource: DataSource, location: String) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(location)
        .load()
        .migrate()
}

class ExposedTransactionRunner(private val database: Database) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = suspendTransaction(database) { block() }
}

suspend fun <T> Database.dbQuery(block: suspend () -> T): T = suspendTransaction(this) { block() }

fun DataSource.isReachable(): Boolean = runCatching { connection.use { it.isValid(2) } }.getOrDefault(false)
