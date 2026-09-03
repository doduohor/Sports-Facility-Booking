package com.doduohor

import com.doduohor.infrastructure.database.postgres.DatabaseConfig
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseConfigTest {

    @Test
    fun `fromEnv reports blank required variable`() {
        assertFailsWith<IllegalStateException> {
            DatabaseConfig.fromEnv { name -> if (name == "DATABASE_PASSWORD") " " else "value" }
        }
    }

    @Test
    fun `reads database config from application config`() {
        val config = MapApplicationConfig(
            "database.url" to "jdbc:postgresql://postgres:5432/sports_facility_booking",
            "database.user" to "sports",
            "database.password" to "sports",
        )

        val databaseConfig = DatabaseConfig.from(config)

        assertEquals("jdbc:postgresql://postgres:5432/sports_facility_booking", databaseConfig.url)
        assertEquals("sports", databaseConfig.user)
        assertEquals("sports", databaseConfig.password)
    }
}
