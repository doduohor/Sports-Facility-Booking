package com.doduohor.repository.postgres

import com.doduohor.domain.shared.Clock
import com.doduohor.events.ExpandAttemptResult
import com.doduohor.events.EventProcessingPolicy
import com.doduohor.events.IntegrationEventType
import com.doduohor.events.MakeAsPublishedResult
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.OutboxEventStatus
import com.doduohor.events.OutboxEvents
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.events.SaveErrorResult
import com.doduohor.events.SaveEventResult
import com.doduohor.events.StartPublishingResult
import com.doduohor.infrastructure.database.postgres.OutboxEventsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.sql.SQLException
import kotlin.uuid.Uuid

class PostgresOutboxEventsRepository(
    private val database: Database,
    private val clock: Clock
): OutboxEventsRepository {
    override fun saveEvent(event: NewOutboxEvents): SaveEventResult = try {
        transaction(database) {
            OutboxEventsTable.insert {
                it[OutboxEventsTable.eventId] = event.eventId
                it[OutboxEventsTable.eventType] = event.eventType.name
                it[OutboxEventsTable.payload] = event.payload
                it[OutboxEventsTable.status] = event.status.name
                it[OutboxEventsTable.createdAt] = event.createdAt
                it[OutboxEventsTable.publishedAt] = event.publishedAt
                it[OutboxEventsTable.attempt] = event.attempt
                it[errorMessage] = event.errorMessage
            }
            SaveEventResult.Success
        }
    } catch (exception: ExposedSQLException) {
        if ((exception.cause as? SQLException)?.sqlState == UNIQUE_VIOLATION_SQL_STATE) {
            SaveEventResult.Error
        } else {
            throw exception
        }
    }

    fun saveEvent(event: OutboxEvents): SaveEventResult = saveEvent(
        NewOutboxEvents(
            eventId = event.eventId,
            eventType = event.eventType,
            payload = event.payload,
            status = event.status,
            createdAt = event.createdAt,
            publishedAt = event.publishedAt,
            attempt = event.attempt,
            errorMessage = event.errorMessage
        )
    )

    override fun findUnprocessedEvents(): List<OutboxEvents> = transaction(database) {
        OutboxEventsTable.selectAll()
            .where {
                (OutboxEventsTable.status eq OutboxEventStatus.NEW.name) or
                    ((OutboxEventsTable.status eq OutboxEventStatus.FAILED.name) and
                        (OutboxEventsTable.attempt less EventProcessingPolicy.MAX_ATTEMPTS))
            }
            .orderBy(OutboxEventsTable.id)
            .map{it -> toOutboxEvents(it)}
    }

    override fun tryStartPublishing(eventId: Uuid): StartPublishingResult = transaction(database) {
        val eventData = OutboxEventsTable.selectAll()
            .where { OutboxEventsTable.eventId eq eventId }
            .firstOrNull()
            ?: return@transaction StartPublishingResult.NotFound

        val status = eventData[OutboxEventsTable.status]
        val attempt = eventData[OutboxEventsTable.attempt]

        when (status) {
            OutboxEventStatus.NEW.name,
            OutboxEventStatus.FAILED.name -> {
                if (attempt >= EventProcessingPolicy.MAX_ATTEMPTS) {
                    return@transaction StartPublishingResult.AttemptsExceeded
                }

                val updatedRows = OutboxEventsTable.update(
                    {
                        (OutboxEventsTable.eventId eq eventId) and
                            (OutboxEventsTable.status eq status) and
                            (OutboxEventsTable.attempt less EventProcessingPolicy.MAX_ATTEMPTS)
                    }
                ) {
                    it[OutboxEventsTable.status] = OutboxEventStatus.PROCESSING.name
                    it[OutboxEventsTable.attempt] = OutboxEventsTable.attempt.plus(1)
                    it[OutboxEventsTable.errorMessage] = null
                    it[OutboxEventsTable.publishedAt] = null
                }

                if (updatedRows == 0) StartPublishingResult.AlreadyProcessing
                else StartPublishingResult.Started
            }
            OutboxEventStatus.PROCESSING.name -> StartPublishingResult.AlreadyProcessing
            OutboxEventStatus.PUBLISHED.name -> StartPublishingResult.AlreadyPublished
            else -> StartPublishingResult.NotNew
        }
    }

    override fun makeAsPublished(eventId: Uuid): MakeAsPublishedResult = transaction(database) {
        val eventData = OutboxEventsTable.selectAll()
            .where { OutboxEventsTable.eventId eq eventId }
            .firstOrNull() ?: return@transaction MakeAsPublishedResult.NotFound
        when(OutboxEventStatus.valueOf(eventData[OutboxEventsTable.status])){
            OutboxEventStatus.NEW -> MakeAsPublishedResult.NotProcessing
            OutboxEventStatus.PROCESSING -> {
                val updatedRows = OutboxEventsTable.update(
                    {
                        (OutboxEventsTable.eventId eq eventId) and
                            (OutboxEventsTable.status eq OutboxEventStatus.PROCESSING.name)
                    }
                ){
                    it[OutboxEventsTable.status] = OutboxEventStatus.PUBLISHED.name
                    it[OutboxEventsTable.publishedAt] = OffsetDateTime.ofInstant(clock.now(), ZoneOffset.UTC)
                    it[OutboxEventsTable.errorMessage] = null
                }
                if(updatedRows == 0)
                    MakeAsPublishedResult.NotProcessing
                else
                    MakeAsPublishedResult.Success
            }
            OutboxEventStatus.PUBLISHED -> MakeAsPublishedResult.ActualPublished
            OutboxEventStatus.FAILED -> MakeAsPublishedResult.NotProcessing
        }
    }

    override fun expandAttempt(eventId: Uuid): ExpandAttemptResult {
        return transaction(database) {
            val eventData = OutboxEventsTable.selectAll()
                .where { OutboxEventsTable.eventId eq eventId }
                .firstOrNull()
                ?: return@transaction ExpandAttemptResult.NotFound

            if (eventData[OutboxEventsTable.status] != OutboxEventStatus.PROCESSING.name) {
                return@transaction ExpandAttemptResult.NotProcessing
            }

            val updatedRows = OutboxEventsTable.update(
                {
                    (OutboxEventsTable.eventId eq eventId) and
                        (OutboxEventsTable.status eq OutboxEventStatus.PROCESSING.name)
                }
            ) {
                it[OutboxEventsTable.attempt] = OutboxEventsTable.attempt.plus(1)
            }

            if (updatedRows == 0) ExpandAttemptResult.NotProcessing
            else ExpandAttemptResult.Success
        }
    }

    override fun saveError(eventId: Uuid, error: String): SaveErrorResult {
        return transaction(database) {
            val eventData = OutboxEventsTable.selectAll()
                .where { OutboxEventsTable.eventId eq eventId }
                .firstOrNull()
                ?: return@transaction SaveErrorResult.NotFound

            if (eventData[OutboxEventsTable.status] != OutboxEventStatus.PROCESSING.name) {
                return@transaction SaveErrorResult.NotProcessing
            }

            val updatedRows = OutboxEventsTable.update(
                {
                    (OutboxEventsTable.eventId eq eventId) and
                        (OutboxEventsTable.status eq OutboxEventStatus.PROCESSING.name)
                }
            ) {
                it[OutboxEventsTable.status] = OutboxEventStatus.FAILED.name
                it[OutboxEventsTable.errorMessage] = error
                it[OutboxEventsTable.publishedAt] = null
            }

            if (updatedRows == 0) SaveErrorResult.NotProcessing
            else SaveErrorResult.Success
        }
    }

    private fun toOutboxEvents(row: ResultRow): OutboxEvents =
        OutboxEvents(
            id = row[OutboxEventsTable.id],
            eventId = row[OutboxEventsTable.eventId],
            eventType = IntegrationEventType.valueOf(row[OutboxEventsTable.eventType]),
            payload = row[OutboxEventsTable.payload],
            status = OutboxEventStatus.valueOf(row[OutboxEventsTable.status]),
            createdAt = row[OutboxEventsTable.createdAt],
            publishedAt = row[OutboxEventsTable.publishedAt],
            attempt = row[OutboxEventsTable.attempt],
            errorMessage = row[OutboxEventsTable.errorMessage]
        )

    private companion object {
        const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }

}
