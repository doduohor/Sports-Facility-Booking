package com.doduohor.infrastructure.database.postgres

import io.ktor.server.config.ApplicationConfig

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String
) {
    companion object {
        fun fromEnv(): DatabaseConfig =
            DatabaseConfig(
                url = requireNotNull(System.getenv("DATABASE_URL")),
                user = requireNotNull(System.getenv("DATABASE_USER")),
                password = requireNotNull(System.getenv("DATABASE_PASSWORD"))
            )

        fun from(config: ApplicationConfig): DatabaseConfig =
            DatabaseConfig(
                url = config.property("database.url").getString(),
                user = config.property("database.user").getString(),
                password = config.property("database.password").getString()
            )
    }
}
