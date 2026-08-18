package com.doduohor

import io.ktor.server.application.*
import com.doduohor.di.configureKoin
import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoFactory
import com.doduohor.infrastructure.database.postgres.DatabaseConfig
import com.doduohor.infrastructure.database.postgres.DatabaseFactory
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
    val mongoConnection = MongoFactory.connect(MongoConfig.from(environment.config))

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

    monitor.subscribe(ApplicationStopping) {
        mongoConnection.client.close()
    }

    configureKoin(database, null, mongoConnection)
    configureCors()
    configureSSE()
    configureSerialization()
    configureStatusPages()
    configureLogging()
    configureSecurity()
    configureRouting()
}
