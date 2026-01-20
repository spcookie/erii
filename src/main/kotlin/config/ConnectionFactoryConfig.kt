package uesugi.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class ConnectionFactoryConfig {

    val dataSourceConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:h2:file:./store/test/data;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
        driverClassName = "org.h2.Driver"
        maximumPoolSize = 6
        isReadOnly = false
    }

    val dataSource = HikariDataSource(dataSourceConfig)

}