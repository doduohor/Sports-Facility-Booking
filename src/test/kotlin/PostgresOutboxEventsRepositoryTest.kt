package com.doduohor

import com.doduohor.events.ExpandAttemptResult
import com.doduohor.events.EventProcessingPolicy
import com.doduohor.events.IntegrationEventType
import com.doduohor.events.MakeAsPublishedResult
import com.doduohor.events.OutboxEventStatus
import com.doduohor.events.OutboxEvents
import com.doduohor.events.SaveErrorResult
import com.doduohor.infrastructure.database.postgres.OutboxEventsTable
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.events.StartPublishingResult
import com.doduohor.repository.postgres.PostgresOutboxEventsRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@Testcontainers
class PostgresOutboxEventsRepositoryTest {
    private val fixedInstant = Instant.parse("2026-08-20T12:00:00Z")
    private val fixedClock = FixedClock(fixedInstant)

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        private lateinit var dataSource: HikariDataSource
        private lateinit var database: Database

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = postgres.driverClassName
            }
            dataSource = HikariDataSource(hikariConfig)
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            database = Database.connect(dataSource)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            dataSource.close()
        }
    }

    private lateinit var repository: PostgresOutboxEventsRepository

    @BeforeEach
    fun clearTable() {
        transaction(database) {
            OutboxEventsTable.deleteAll()
        }
        repository = PostgresOutboxEventsRepository(database, fixedClock)
    }

    @Test
    fun `save and find returns outbox event`() {
        val event = event(status = OutboxEventStatus.NEW)

        assertEquals(com.doduohor.events.SaveEventResult.Success, repository.saveEvent(event))

        val saved = repository.findUnprocessedEvents()
        assertEquals(listOf(event), saved)
    }

    @Test
    fun `expand attempt increments processing event`() {
        val event = event(status = OutboxEventStatus.PROCESSING, attempt = 1)
        repository.saveEvent(event)

        assertEquals(ExpandAttemptResult.Success, repository.expandAttempt(event.eventId))
        val savedAttempt = transaction(database) {
            OutboxEventsTable.selectAll().single()[OutboxEventsTable.attempt]
        }
        assertEquals(2, savedAttempt)
    }

    @Test
    fun `try start publishing claims new event and increments attempt`() {
        val event = event(status = OutboxEventStatus.NEW, attempt = 0)
        repository.saveEvent(event)

        assertEquals(StartPublishingResult.Started, repository.tryStartPublishing(event.eventId))

        val saved = transaction(database) {
            OutboxEventsTable.selectAll().single()
        }
        assertEquals(OutboxEventStatus.PROCESSING.name, saved[OutboxEventsTable.status])
        assertEquals(1, saved[OutboxEventsTable.attempt])
    }

    @Test
    fun `try start publishing does not claim same event twice`() {
        val event = event(status = OutboxEventStatus.NEW)
        repository.saveEvent(event)

        assertEquals(StartPublishingResult.Started, repository.tryStartPublishing(event.eventId))
        assertEquals(StartPublishingResult.AlreadyProcessing, repository.tryStartPublishing(event.eventId))
    }

    @Test
    fun `try start publishing returns not found for unknown event`() {
        assertEquals(StartPublishingResult.NotFound, repository.tryStartPublishing(Uuid.random()))
    }

    @Test
    fun `failed event is returned for retry`() {
        val event = event(
            status = OutboxEventStatus.FAILED,
            attempt = 1
        )
        repository.saveEvent(event)

        val saved = repository.findUnprocessedEvents().single()
        assertEquals(event.eventId, saved.eventId)
        assertEquals(event.status, saved.status)
        assertEquals(event.attempt, saved.attempt)
    }

    @Test
    fun `try start publishing retries failed event`() {
        val event = event(
            status = OutboxEventStatus.FAILED,
            attempt = 1
        )
        repository.saveEvent(event)

        assertEquals(StartPublishingResult.Started, repository.tryStartPublishing(event.eventId))

        val saved = transaction(database) {
            OutboxEventsTable.selectAll().single()
        }
        assertEquals(OutboxEventStatus.PROCESSING.name, saved[OutboxEventsTable.status])
        assertEquals(2, saved[OutboxEventsTable.attempt])
        assertEquals(null, saved[OutboxEventsTable.errorMessage])
    }

    @Test
    fun `failed event with max attempts is not returned or retried`() {
        val event = event(
            status = OutboxEventStatus.FAILED,
            attempt = EventProcessingPolicy.MAX_ATTEMPTS
        )
        repository.saveEvent(event)

        assertTrue(repository.findUnprocessedEvents().isEmpty())
        assertEquals(StartPublishingResult.AttemptsExceeded, repository.tryStartPublishing(event.eventId))
    }

    @Test
    fun `expand attempt does not change new event`() {
        val event = event(status = OutboxEventStatus.NEW, attempt = 0)
        repository.saveEvent(event)

        assertEquals(ExpandAttemptResult.NotProcessing, repository.expandAttempt(event.eventId))
        assertTrue(repository.findUnprocessedEvents().single().attempt == 0)
    }

    @Test
    fun `save error marks processing event as failed`() {
        val event = event(status = OutboxEventStatus.PROCESSING)
        repository.saveEvent(event)

        assertEquals(SaveErrorResult.Success, repository.saveError(event.eventId, "Rabbit unavailable"))
        val saved = transaction(database) {
            OutboxEventsTable.selectAll().single()
        }
        assertEquals(OutboxEventStatus.FAILED.name, saved[OutboxEventsTable.status])
        assertEquals("Rabbit unavailable", saved[OutboxEventsTable.errorMessage])
        val failedEvent = repository.findUnprocessedEvents().single()
        assertEquals(event.eventId, failedEvent.eventId)
        assertEquals(OutboxEventStatus.FAILED, failedEvent.status)
        assertEquals("Rabbit unavailable", failedEvent.errorMessage)
    }

    @Test
    fun `save error does not change published event`() {
        val event = event(status = OutboxEventStatus.PUBLISHED)
        repository.saveEvent(event)

        assertEquals(SaveErrorResult.NotProcessing, repository.saveError(event.eventId, "late error"))
    }

    @Test
    fun `make as published rejects new event`() {
        val event = event(status = OutboxEventStatus.NEW)
        repository.saveEvent(event)

        assertEquals(MakeAsPublishedResult.NotProcessing, repository.makeAsPublished(event.eventId))
    }

    @Test
    fun `make as published sets publishedAt from injected clock`() {
        val event = event(status = OutboxEventStatus.PROCESSING)
        repository.saveEvent(event)

        assertEquals(MakeAsPublishedResult.Success, repository.makeAsPublished(event.eventId))

        val publishedAt = transaction(database) {
            OutboxEventsTable.selectAll().single()[OutboxEventsTable.publishedAt]
        }
        assertEquals(OffsetDateTime.ofInstant(fixedInstant, ZoneOffset.UTC), publishedAt)
    }

    private fun event(
        status: OutboxEventStatus,
        attempt: Int = 0
    ): OutboxEvents {
        val now = OffsetDateTime.of(2026, 8, 19, 12, 0, 0, 0, ZoneOffset.UTC)
        return OutboxEvents(
            id = 1,
            eventId = Uuid.random(),
            eventType = IntegrationEventType.MEASUREMENT_CREATED,
            payload = buildJsonObject { put("value", 24.5) },
            status = status,
            createdAt = now,
            publishedAt = null,
            attempt = attempt,
            errorMessage = null
        )
    }
}
