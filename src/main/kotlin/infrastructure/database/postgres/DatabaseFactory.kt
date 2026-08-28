package com.doduohor.infrastructure.database.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.flywaydb.core.Flyway

class DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun connect(config: DatabaseConfig): Database {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
        }

        val newDataSource = HikariDataSource(hikariConfig)
        return try {
            dataSource = newDataSource
            Flyway.configure().dataSource(newDataSource).locations("classpath:db/migration").load().migrate()
            Database.connect(newDataSource)
        } catch (exception: Throwable) {
            newDataSource.close()
            if (dataSource === newDataSource) dataSource = null
            throw exception
        }
    }

    fun close() {
        dataSource?.close()
    }
}
