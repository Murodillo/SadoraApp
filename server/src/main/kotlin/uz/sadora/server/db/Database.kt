package uz.sadora.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import uz.sadora.server.config.DatabaseConfig

private val logger = LoggerFactory.getLogger("uz.sadora.server.db")

class DatabaseFactory private constructor(
    private val dataSource: HikariDataSource,
    val database: Database,
) : AutoCloseable {

    override fun close() = dataSource.close()

    companion object {
        fun connect(config: DatabaseConfig): DatabaseFactory {
            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = config.jdbcUrl
                    username = config.user
                    password = config.password
                    driverClassName = "org.postgresql.Driver"
                    maximumPoolSize = config.maxPoolSize
                    isAutoCommit = false
                    transactionIsolation = "TRANSACTION_READ_COMMITTED"
                    poolName = "sadora-pool"
                    // Every timestamp column is `timestamptz`; keeping the JVM and the
                    // session on UTC removes the host timezone from the equation.
                    connectionInitSql = "SET TIME ZONE 'UTC'"
                    validate()
                },
            )
            if (config.runMigrations) migrate(dataSource)
            return DatabaseFactory(dataSource, Database.connect(dataSource))
        }

        /**
         * Flyway owns the schema. It runs before Exposed sees the database so a fresh
         * environment and an upgraded one reach the same place.
         */
        fun migrate(dataSource: DataSource) {
            val result = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_schema_history")
                .load()
                .migrate()
            logger.info(
                "Flyway: {} migration(s) applied, schema at {}",
                result.migrationsExecuted,
                result.targetSchemaVersion ?: "baseline",
            )
        }
    }
}

/**
 * Runs a transaction off the event loop. Exposed's JDBC layer is blocking, so every call
 * site goes through here rather than blocking a Netty thread.
 */
suspend fun <T> dbQuery(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }
