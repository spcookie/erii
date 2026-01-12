package uesugi.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class ConnectionFactoryConfig {

    val dataSourceConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:h2:file:./store/data;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
        driverClassName = "org.h2.Driver"
        maximumPoolSize = 6
        isReadOnly = false
    }

    val dataSource = HikariDataSource(dataSourceConfig)

    val database = Database.connect(
        dataSource,
        {},
        DatabaseConfig {
            useNestedTransactions = true
        }
    )

    init {
        TransactionManager.defaultDatabase = database
    }

}