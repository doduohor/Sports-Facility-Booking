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

        fun fromEnv(): DatabaseConfig = fromEnv(System::getenv)

        internal fun fromEnv(readEnv: (String) -> String?): DatabaseConfig =
            DatabaseConfig(
                url = requiredEnv("DATABASE_URL", readEnv),
                user = requiredEnv("DATABASE_USER", readEnv),
                password = requiredEnv("DATABASE_PASSWORD", readEnv)
            )

        private fun requiredEnv(name: String, readEnv: (String) -> String?): String =
            readEnv(name)?.takeIf { it.isNotBlank() }
                ?: error("Required environment variable is missing or blank: $name")
    }
}
