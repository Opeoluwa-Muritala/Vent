package app.vent.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseFactory {
    fun connect(config: AppConfig): HikariDataSource {
        val hikari =
            HikariConfig().apply {
                jdbcUrl = config.databaseUrl
                username = config.databaseUser
                password = config.databasePassword
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
                minimumIdle = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                validate()
            }
        return HikariDataSource(hikari).also { dataSource ->
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            Database.connect(dataSource)
        }
    }
}
