package com.doduohor.repository.mongo

import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.EventHistoryStatus
import com.doduohor.events.EventProcessingPolicy
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.bson.Document
import java.time.Instant

class MongoEventHistoryRepository(
    database: MongoDatabase
) : EventHistoryRepository {
    private val collection = database.getCollection<Document>("event_history")

    override suspend fun createIndexes(){
        collection.createIndex(
            Indexes.ascending("eventId"),
            IndexOptions().unique(true)
        )
    }

    override suspend fun tryStartProcessing(event: EventHistoryDocument): MarkStartProcessingResult {
        try {
            collection.insertOne(
                event.copy(
                    status = EventHistoryStatus.PROCESSING,
                    attempt = 1,
                    processedAt = null,
                    errorMessage = null,
                    processingStartedAt = Instant.now().toString()
                ).toDocument()
            )
            return MarkStartProcessingResult.Started
        } catch (exception: MongoWriteException) {
            if (exception.error.code != DUPLICATE_KEY_ERROR_CODE) {
                throw exception
            }
        }

        val existingEvent = collection.find(eq("eventId", event.eventId)).firstOrNull()
            ?: return MarkStartProcessingResult.Started

        return when (EventHistoryStatus.valueOf(existingEvent.getString("status"))) {
            EventHistoryStatus.PROCESSING -> tryRestartStaleProcessing(existingEvent, event.eventId)
            EventHistoryStatus.PROCESSED -> MarkStartProcessingResult.AlreadyProcessed
            EventHistoryStatus.FAILED -> tryRestartFailed(existingEvent, event.eventId)
        }
    }

    private suspend fun tryRestartStaleProcessing(
        existingEvent: Document,
        eventId: String
    ): MarkStartProcessingResult {
        val currentAttempt = existingEvent.getInteger("attempt")
            ?: return MarkStartProcessingResult.AttemptsExceeded
        val startedAt = existingEvent.getString("processingStartedAt")
            ?: return MarkStartProcessingResult.AlreadyProcessing
        val staleBefore = Instant.now().minusSeconds(EventProcessingPolicy.PROCESSING_TIMEOUT_SECONDS)

        if (!Instant.parse(startedAt).isBefore(staleBefore)) {
            return MarkStartProcessingResult.AlreadyProcessing
        }
        if (currentAttempt >= EventProcessingPolicy.MAX_ATTEMPTS) {
            return MarkStartProcessingResult.AttemptsExceeded
        }

        val updateResult = collection.updateOne(
            Filters.and(
                eq("eventId", eventId),
                eq("status", EventHistoryStatus.PROCESSING.name),
                eq("attempt", currentAttempt),
                eq("processingStartedAt", startedAt)
            ),
            combine(
                set("attempt", currentAttempt + 1),
                set("processingStartedAt", Instant.now().toString()),
                set("processedAt", null),
                set("errorMessage", null)
            )
        )

        if (updateResult.matchedCount == 1L) {
            return MarkStartProcessingResult.Started
        }

        return when (currentStatus(eventId)) {
            EventHistoryStatus.PROCESSING -> MarkStartProcessingResult.AlreadyProcessing
            EventHistoryStatus.PROCESSED -> MarkStartProcessingResult.AlreadyProcessed
            EventHistoryStatus.FAILED -> MarkStartProcessingResult.AttemptsExceeded
            null -> MarkStartProcessingResult.Started
        }
    }

    private suspend fun tryRestartFailed(
        existingEvent: Document,
        eventId: String
    ): MarkStartProcessingResult {
        val currentAttempt = existingEvent.getInteger("attempt")
            ?: return MarkStartProcessingResult.AttemptsExceeded

        if (currentAttempt >= EventProcessingPolicy.MAX_ATTEMPTS) {
            return MarkStartProcessingResult.AttemptsExceeded
        }

        val updateResult = collection.updateOne(
            Filters.and(
                eq("eventId", eventId),
                eq("status", EventHistoryStatus.FAILED.name),
                eq("attempt", currentAttempt)
            ),
            combine(
                set("status", EventHistoryStatus.PROCESSING.name),
                set("attempt", currentAttempt + 1),
                set("receivedAt", Instant.now().toString()),
                set("processingStartedAt", Instant.now().toString()),
                set("processedAt", null),
                set("errorMessage", null)
            )
        )

        if (updateResult.matchedCount == 1L) {
            return MarkStartProcessingResult.Started
        }

        return when (currentStatus(eventId)) {
            EventHistoryStatus.PROCESSING -> MarkStartProcessingResult.AlreadyProcessing
            EventHistoryStatus.PROCESSED -> MarkStartProcessingResult.AlreadyProcessed
            EventHistoryStatus.FAILED -> MarkStartProcessingResult.AttemptsExceeded
            null -> MarkStartProcessingResult.Started
        }
    }

    override suspend fun markProcessed(eventId: String): MarkProcessedResult {
        val updateResult = collection.updateOne(
            Filters.and(
                eq("eventId", eventId),
                eq("status", EventHistoryStatus.PROCESSING.name)
            ),
            combine(
                set("status", EventHistoryStatus.PROCESSED.name),
                set("processedAt", Instant.now().toString()),
                set("errorMessage", null)
            )
        )

        if (updateResult.matchedCount == 1L) {
            return MarkProcessedResult.Updated
        }

        return when (currentStatus(eventId)) {
            null -> MarkProcessedResult.NotFound
            else -> MarkProcessedResult.NotProcessing
        }
    }

    override suspend fun markFailed(eventId: String, errorMessage: String): MarkFailedResult {
        val updateResult = collection.updateOne(
            Filters.and(
                eq("eventId", eventId),
                eq("status", EventHistoryStatus.PROCESSING.name)
            ),
            combine(
                set("status", EventHistoryStatus.FAILED.name),
                set("processedAt", Instant.now().toString()),
                set("errorMessage", errorMessage)
            )
        )

        if (updateResult.matchedCount == 1L) {
            return MarkFailedResult.Updated
        }

        return when (currentStatus(eventId)) {
            null -> MarkFailedResult.NotFound
            else -> MarkFailedResult.NotProcessing
        }
    }

    override suspend fun save(event: EventHistoryDocument): EventHistoryDocument {
        collection.insertOne(event.toDocument())
        return event
    }

    override suspend fun findById(eventId: String): EventHistoryDocument? {
        return collection
            .find(eq("eventId", eventId))
            .toList()
            .firstOrNull()
            ?.toEventHistoryDocument()
    }

    override suspend fun findByType(type: String): List<EventHistoryDocument> {
        return collection
            .find(eq("eventType", type))
            .toList()
            .map { it.toEventHistoryDocument() }
    }

    private fun EventHistoryDocument.toDocument(): Document =
        Document()
            .append("eventId", eventId)
            .append("eventType", eventType)
            .append("eventCreatedAt", eventCreatedAt)
            .append("receivedAt", receivedAt)
            .append("processedAt", processedAt)
            .append("data", Document.parse(data.toString()))
            .append("status", status.name)
            .append("errorMessage", errorMessage)
            .append("attempt", attempt)
            .append("processingStartedAt", processingStartedAt)

    private fun Document.toEventHistoryDocument(): EventHistoryDocument {
        val dataDocument = get("data", Document::class.java)

        return EventHistoryDocument(
            eventId = getString("eventId"),
            eventType = getString("eventType"),
            eventCreatedAt = getString("eventCreatedAt"),
            receivedAt = getString("receivedAt"),
            data = Json.parseToJsonElement(dataDocument.toJson()).jsonObject,
            status = EventHistoryStatus.valueOf(getString("status")),
            processedAt = getString("processedAt"),
            errorMessage = getString("errorMessage"),
            attempt = getInteger("attempt"),
            processingStartedAt = getString("processingStartedAt")
        )
    }

    private suspend fun currentStatus(eventId: String): EventHistoryStatus? =
        collection.find(eq("eventId", eventId))
            .firstOrNull()
            ?.getString("status")
            ?.let(EventHistoryStatus::valueOf)

    private companion object {
        const val DUPLICATE_KEY_ERROR_CODE = 11000
    }
}
