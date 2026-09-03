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
    check(databaseEnabled) {
        "PostgreSQL is mandatory: set database.enabled=true"
    }

    val databaseFactory = DatabaseFactory()
    val database = databaseFactory.connect(DatabaseConfig.from(environment.config))
    val mongoConnection = MongoFactory.connect(MongoConfig.from(environment.config))

    transaction(database) {
        exec("SELECT 1")
    }

    monitor.subscribe(ApplicationStopping) {
        databaseFactory.close()
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
