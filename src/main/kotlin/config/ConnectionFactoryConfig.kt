package uesugi.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

class ConnectionFactoryConfig {

    fun getDataSource(): DataSource {
        val dataSourceConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:file:./store/data;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 6
            isReadOnly = false
        }

        return HikariDataSource(dataSourceConfig)
    }

}