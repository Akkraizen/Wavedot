package space.wavedot.server.testing

import org.jetbrains.exposed.v1.jdbc.Database
import space.wavedot.server.config.DatabaseConfig
import space.wavedot.server.persistence.createDataSource
import space.wavedot.server.persistence.migrate
import java.util.UUID
import javax.sql.DataSource

class TestDatabase(migrations: String) {
    val dataSource: DataSource = createDataSource(
        DatabaseConfig(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            maxPoolSize = 4,
        ),
    ).also { migrate(it, migrations) }

    val database: Database = Database.connect(dataSource)
}
