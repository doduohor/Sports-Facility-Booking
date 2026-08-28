package com.doduohor.infrastructure.database.postgres

import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String
) {
    companion object {
        fun from(config: ApplicationConfig): DatabaseConfig =
            DatabaseConfig(
                url = config.property("database.url").getString(),
                user = config.property("database.user").getString(),
                password = config.property("database.password").getString()
            )

        fun fromEnv(): DatabaseConfig =
            DatabaseConfig(
                url = System.getenv("DATABASE_URL"),
                user = System.getenv("DATABASE_USER"),
                password = System.getenv("DATABASE_PASSWORD")
            )
    }
}
