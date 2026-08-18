package com.doduohor

import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.EventHistoryStatus
import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoFactory
import com.doduohor.repository.mongo.MongoEventHistoryRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bson.Document
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class MongoEventHistoryRepositoryTest {

    companion object {
        @Container
        @JvmStatic
        val mongo = MongoDBContainer("mongo:8.0")
            .withEnv("MONGO_INITDB_ROOT_USERNAME", "mongo_admin")
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", "mongo_password")

        private lateinit var connection: com.doduohor.infrastructure.database.mongo.MongoConnection

        @JvmStatic
        @BeforeAll
        fun initializeConnection() {
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
        fun closeConnection() {
            connection.client.close()
        }
    }

    private val repository: MongoEventHistoryRepository
        get() = MongoEventHistoryRepository(connection.database)

    @BeforeEach
    fun clearCollection() = runBlocking {
        connection.database
            .getCollection<Document>("event_history")
            .drop()
    }

    @Test
    fun `save and findById return stored event`() = runBlocking {
        val event = event(id = 1, type = "MEASUREMENT_CREATED")

        repository.save(event)

        assertEquals(event, repository.findById("event-1"))
    }

    @Test
    fun `tryStartProcessing saves new event as processing`() = runBlocking {
        repository.createIndexes()
        val event = event(
            id = 1,
            type = "MEASUREMENT_CREATED",
            status = EventHistoryStatus.PROCESSING
        )

        val started = repository.tryStartProcessing(event)

        val savedEvent = repository.findById("event-1")
        assertTrue(started)
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.PROCESSING, savedEvent.status)
        assertEquals(1, savedEvent.attempt)
        assertNull(savedEvent.processedAt)
        assertNull(savedEvent.errorMessage)
    }

    @Test
    fun `tryStartProcessing returns false when event id already exists`() = runBlocking {
        repository.createIndexes()
        val event = event(
            id = 1,
            type = "MEASUREMENT_CREATED",
            status = EventHistoryStatus.PROCESSING
        )

        val firstStart = repository.tryStartProcessing(event)
        val secondStart = repository.tryStartProcessing(event)

        assertTrue(firstStart)
        assertFalse(secondStart)
        assertEquals(1, repository.findByType("MEASUREMENT_CREATED").size)
    }

    @Test
    fun `markProcessed sets processed status and processedAt`() = runBlocking {
        repository.createIndexes()
        repository.tryStartProcessing(
            event(id = 1, type = "MEASUREMENT_CREATED", status = EventHistoryStatus.PROCESSING)
        )

        repository.markProcessed("event-1")

        val savedEvent = repository.findById("event-1")
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.PROCESSED, savedEvent.status)
        assertNotNull(savedEvent.processedAt)
        assertNull(savedEvent.errorMessage)
    }

    @Test
    fun `markFailed sets failed status processedAt and error message`() = runBlocking {
        repository.createIndexes()
        repository.tryStartProcessing(
            event(id = 1, type = "INCIDENT_CREATED", status = EventHistoryStatus.PROCESSING)
        )

        repository.markFailed("event-1", "telegram error")

        val savedEvent = repository.findById("event-1")
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.FAILED, savedEvent.status)
        assertNotNull(savedEvent.processedAt)
        assertEquals("telegram error", savedEvent.errorMessage)
    }

    @Test
    fun `findByType returns matching events`() = runBlocking {
        repository.save(event(id = 1, type = "MEASUREMENT_CREATED"))
        repository.save(event(id = 2, type = "INCIDENT_CREATED"))
        repository.save(event(id = 3, type = "MEASUREMENT_CREATED"))

        val events = repository.findByType("MEASUREMENT_CREATED")

        assertEquals(listOf("event-1", "event-3"), events.map { it.eventId })
    }

    private fun event(
        id: Long,
        type: String,
        status: EventHistoryStatus = EventHistoryStatus.PROCESSED
    ): EventHistoryDocument =
        EventHistoryDocument(
            eventId = "event-$id",
            eventType = type,
            eventCreatedAt = "2026-08-16T10:00:00Z",
            receivedAt = "2026-08-16T10:00:01Z",
            processedAt = null,
            data = buildJsonObject {
                put("id", id)
                put("value", 24.5)
            },
            status = status,
            errorMessage = null,
            attempt = 1
        )
}
