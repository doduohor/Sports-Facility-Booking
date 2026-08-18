package com.doduohor

import com.doduohor.infrastructure.database.mongo.MongoConfig
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class MongoConfigTest {

    @Test
    fun `reads mongo config from application config`() {
        val config = MapApplicationConfig(
            "mongo.host" to "mongo",
            "mongo.port" to "27017",
            "mongo.username" to "mongo_admin",
            "mongo.password" to "mongo_password",
            "mongo.database" to "sports_facility_booking"
        )

        val mongoConfig = MongoConfig.from(config)

        assertEquals("mongo", mongoConfig.host)
        assertEquals(27017, mongoConfig.port)
        assertEquals("mongo_admin", mongoConfig.username)
        assertEquals("mongo_password", mongoConfig.password)
        assertEquals("sports_facility_booking", mongoConfig.database)
    }
}
