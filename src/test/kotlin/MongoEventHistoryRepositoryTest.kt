package com.doduohor

import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.EventHistoryStatus
import com.doduohor.events.EventProcessingPolicy
import com.doduohor.repository.mongo.MarkFailedResult
import com.doduohor.repository.mongo.MarkProcessedResult
import com.doduohor.repository.mongo.MarkStartProcessingResult
import com.doduohor.infrastructure.database.mongo.MongoConfig
import com.doduohor.infrastructure.database.mongo.MongoFactory
import com.doduohor.infrastructure.time.FixedClock
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant

@Testcontainers
class MongoEventHistoryRepositoryTest {
    private val fixedInstant = Instant.parse("2026-08-20T12:00:00Z")
    private val fixedClock = FixedClock(fixedInstant)

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
        get() = MongoEventHistoryRepository(connection.database, fixedClock)

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
        assertEquals(MarkStartProcessingResult.Started, started)
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.PROCESSING, savedEvent.status)
        assertEquals(1, savedEvent.attempt)
        assertNull(savedEvent.processedAt)
        assertEquals(fixedInstant.toString(), savedEvent.processingStartedAt)
        assertNull(savedEvent.errorMessage)
    }

    @Test
    fun `tryStartProcessing returns already processing without replacing duplicate event document`() = runBlocking {
        repository.createIndexes()
        val event = event(
            id = 1,
            type = "MEASUREMENT_CREATED",
            status = EventHistoryStatus.PROCESSING
        )

        val firstStart = repository.tryStartProcessing(event)
        val storedAfterFirstStart = repository.findById("event-1")
        val secondStart = repository.tryStartProcessing(event)

        assertEquals(MarkStartProcessingResult.Started, firstStart)
        assertEquals(MarkStartProcessingResult.AlreadyProcessing, secondStart)
        assertEquals(storedAfterFirstStart, repository.findById("event-1"))
        assertEquals(1, repository.findByType("MEASUREMENT_CREATED").size)
    }

    @Test
    fun `tryStartProcessing retries failed event and increments attempt`() = runBlocking {
        repository.createIndexes()
        val event = event(
            id = 1,
            type = "INCIDENT_CREATED",
            status = EventHistoryStatus.PROCESSING
        )

        assertEquals(MarkStartProcessingResult.Started, repository.tryStartProcessing(event))
        assertEquals(MarkFailedResult.Updated, repository.markFailed("event-1", "telegram error"))

        val result = repository.tryStartProcessing(event)
        val savedEvent = repository.findById("event-1")

        assertEquals(MarkStartProcessingResult.Started, result)
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.PROCESSING, savedEvent.status)
        assertEquals(2, savedEvent.attempt)
        assertNull(savedEvent.errorMessage)
    }

    @Test
    fun `tryStartProcessing does not retry failed event after attempt limit`() = runBlocking {
        repository.createIndexes()
        repository.save(
            event(
                id = 1,
                type = "INCIDENT_CREATED",
                status = EventHistoryStatus.FAILED,
                attempt = EventProcessingPolicy.MAX_ATTEMPTS
            )
        )

        val result = repository.tryStartProcessing(event(id = 1, type = "INCIDENT_CREATED"))

        assertEquals(MarkStartProcessingResult.AttemptsExceeded, result)
        assertEquals(
            event(id = 1, type = "INCIDENT_CREATED", status = EventHistoryStatus.FAILED, attempt = EventProcessingPolicy.MAX_ATTEMPTS),
            repository.findById("event-1")
        )
    }

    @Test
    fun `tryStartProcessing does not restart fresh processing event`() = runBlocking {
        repository.createIndexes()
        repository.save(
            event(
                id = 1,
                type = "INCIDENT_CREATED",
                status = EventHistoryStatus.PROCESSING,
                processingStartedAt = fixedInstant.toString()
            )
        )

        val result = repository.tryStartProcessing(event(id = 1, type = "INCIDENT_CREATED"))

        assertEquals(MarkStartProcessingResult.AlreadyProcessing, result)
        assertEquals(
            event(
                id = 1,
                type = "INCIDENT_CREATED",
                status = EventHistoryStatus.PROCESSING,
                processingStartedAt = fixedInstant.toString()
            ),
            repository.findById("event-1")
        )
    }

    @Test
    fun `tryStartProcessing restarts stale processing event and increments attempt`() = runBlocking {
        repository.createIndexes()
        repository.save(
            event(
                id = 1,
                type = "INCIDENT_CREATED",
                status = EventHistoryStatus.PROCESSING,
                processingStartedAt = fixedInstant
                    .minusSeconds(EventProcessingPolicy.PROCESSING_TIMEOUT_SECONDS + 1)
                    .toString()
            )
        )

        val result = repository.tryStartProcessing(event(id = 1, type = "INCIDENT_CREATED"))
        val savedEvent = repository.findById("event-1")

        assertEquals(MarkStartProcessingResult.Started, result)
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.PROCESSING, savedEvent.status)
        assertEquals(2, savedEvent.attempt)
        assertEquals(fixedInstant.toString(), savedEvent.processingStartedAt)
        Unit
    }

    @Test
    fun `markProcessed sets processed status and processedAt`() = runBlocking {
        repository.createIndexes()
        repository.tryStartProcessing(
            event(id = 1, type = "MEASUREMENT_CREATED", status = EventHistoryStatus.PROCESSING)
        )

        val result = repository.markProcessed("event-1")

        val savedEvent = repository.findById("event-1")
        assertEquals(MarkProcessedResult.Updated, result)
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.PROCESSED, savedEvent.status)
        assertEquals(fixedInstant.toString(), savedEvent.processedAt)
        assertNull(savedEvent.errorMessage)
    }

    @Test
    fun `markProcessed does not change processed event`() = runBlocking {
        repository.createIndexes()
        val processed = event(id = 1, type = "MEASUREMENT_CREATED")
        repository.save(processed)

        val result = repository.markProcessed("event-1")

        val savedEvent = repository.findById("event-1")
        assertEquals(MarkProcessedResult.NotProcessing, result)
        assertEquals(processed, savedEvent)
    }

    @Test
    fun `markProcessed returns not found for unknown event`() = runBlocking {
        repository.createIndexes()

        val result = repository.markProcessed("missing-event")

        assertEquals(MarkProcessedResult.NotFound, result)
    }

    @Test
    fun `markFailed sets failed status processedAt and error message`() = runBlocking {
        repository.createIndexes()
        repository.tryStartProcessing(
            event(id = 1, type = "INCIDENT_CREATED", status = EventHistoryStatus.PROCESSING)
        )

        val result = repository.markFailed("event-1", "telegram error")

        val savedEvent = repository.findById("event-1")
        assertEquals(MarkFailedResult.Updated, result)
        assertNotNull(savedEvent)
        assertEquals(EventHistoryStatus.FAILED, savedEvent.status)
        assertEquals(fixedInstant.toString(), savedEvent.processedAt)
        assertEquals("telegram error", savedEvent.errorMessage)
    }

    @Test
    fun `markFailed does not change processed event`() = runBlocking {
        repository.createIndexes()
        val processed = event(id = 1, type = "INCIDENT_CREATED")
        repository.save(processed)

        val result = repository.markFailed("event-1", "telegram error")

        val savedEvent = repository.findById("event-1")
        assertEquals(MarkFailedResult.NotProcessing, result)
        assertEquals(processed, savedEvent)
    }

    @Test
    fun `markProcessed and markFailed do not change failed event`() = runBlocking {
        repository.createIndexes()
        val failed = event(
            id = 1,
            type = "INCIDENT_CREATED",
            status = EventHistoryStatus.FAILED,
            processingStartedAt = fixedInstant.minusSeconds(60).toString()
        )
        repository.save(failed)

        assertEquals(MarkProcessedResult.NotProcessing, repository.markProcessed("event-1"))
        assertEquals(MarkFailedResult.NotProcessing, repository.markFailed("event-1", "late error"))

        assertEquals(failed, repository.findById("event-1"))
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
        status: EventHistoryStatus = EventHistoryStatus.PROCESSED,
        attempt: Int = 1,
        processingStartedAt: String? = null
    ): EventHistoryDocument =
        EventHistoryDocument(
            eventId = "event-$id",
            eventType = type,
            eventCreatedAt = "2026-08-16T10:00:00Z",
            receivedAt = "2026-08-16T10:00:01Z",
            processedAt = null,
            processingStartedAt = processingStartedAt,
            data = buildJsonObject {
                put("id", id)
                put("value", 24.5)
            },
            status = status,
            errorMessage = null,
            attempt = attempt
        )
}
