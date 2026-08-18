package com.doduohor.infrastructure.database.mongo

import io.ktor.server.config.ApplicationConfig

data class MongoConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val database: String
){
    companion object{
        fun from(config: ApplicationConfig): MongoConfig =
            MongoConfig(
                host = config.property("mongo.host").getString(),
                port = config.property("mongo.port").getString().toInt(),
                username = config.property("mongo.username").getString(),
                password = config.property("mongo.password").getString(),
                database = config.property("mongo.database").getString()
            )
        fun fromEnv(): MongoConfig =
            MongoConfig(
                host = System.getenv("MONGO_HOST").toString(),
                port = System.getenv("MONGO_PORT").toInt(),
                username = System.getenv("MONGO_USERNAME").toString(),
                password = System.getenv("MONGO_PASSWORD").toString(),
                database = System.getenv("MONGO_DATABASE").toString()
            )
    }
}
