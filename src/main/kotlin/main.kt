package com.doduohor

import io.ktor.server.application.*
import com.doduohor.di.configureKoin
import com.doduohor.infrastructure.database.DatabaseConfig
import com.doduohor.infrastructure.database.DatabaseFactory
import io.ktor.server.netty.EngineMain
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val databaseEnabled = environment.config
        .propertyOrNull("database.enabled")
        ?.getString()
        ?.toBoolean() == true

    val databaseFactory = if (databaseEnabled) DatabaseFactory() else null
    val database = databaseFactory?.connect(DatabaseConfig.from(environment.config))

    if (database != null) {
        transaction(database) {
            exec("SELECT 1")
        }
    }

    databaseFactory?.let { factory ->
        monitor.subscribe(ApplicationStopping) {
            factory.close()
        }
    }

    configureKoin(database)
    configureCors()
    configureSSE()
    configureSerialization()
    configureStatusPages()
    configureLogging()
    configureSecurity()
    configureRouting()
}
