package com.doduohor

import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import kotlin.test.assertEquals

@Testcontainers
class MongoFactoryTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:8.0")
            .withEnv("MONGO_INITDB_ROOT_USERNAME", "mongo_admin")
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", "mongo_password")

        private lateinit var connection: com.doduohor.infrastructure.database.mongo.MongoConnection

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            connection = MongoFactory.connect(
                MongoConfig(
                    host = mongo.host,
                    port = mongo.firstMappedPort,
                    username = "mongo_admin",
                    password = "mongo_password",
                    database = "sports_facility_booking"
                )
            )
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            connection.client.close()
        }
    }

    @Test
    fun `connects to mongo and selects configured database`() {
        assertEquals("sports_facility_booking", connection.database.name)
    }
}
